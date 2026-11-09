package app.lavescar.launcher

import android.app.Application
import android.content.ComponentName
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.lavescar.launcher.core.MinimalLauncherActionSheetUiState
import app.lavescar.launcher.core.MinimalLauncherActionUiState
import app.lavescar.launcher.core.MinimalLauncherRowUiState
import app.lavescar.launcher.core.MinimalLauncherSurface
import app.lavescar.launcher.core.MinimalLauncherUiState
import app.lavescar.launcher.data.LauncherPreferences
import app.lavescar.launcher.data.LauncherPreferencesRepository
import app.lavescar.launcher.data.PinnedAppsRepository
import app.lavescar.launcher.system.AppActionController
import app.lavescar.launcher.system.AppCatalogRepository
import app.lavescar.launcher.system.HomeRoleController
import app.lavescar.launcher.system.LauncherAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MinimalLauncherViewModel(
    application: Application,
    private val pinnedAppsRepository: PinnedAppsRepository,
    private val preferencesRepository: LauncherPreferencesRepository,
    private val appCatalogRepository: AppCatalogRepository,
    private val homeRoleController: HomeRoleController,
    private val appActionController: AppActionController,
) : AndroidViewModel(application) {

    private val ownPackageName = application.packageName
    private val searchEmptyString = application.getString(R.string.minimal_search_empty)
    private val libraryEmptyString = application.getString(R.string.minimal_library_empty)

    private val surface = MutableStateFlow(MinimalLauncherSurface.HOME)
    private val homeQuery = MutableStateFlow("")
    private val libraryQuery = MutableStateFlow("")
    private val actionSheet = MutableStateFlow<MinimalLauncherActionSheetUiState?>(null)
    private val isDefaultLauncher = MutableStateFlow(true)
    private val commands = MutableSharedFlow<LauncherCommand>(extraBufferCapacity = 1)

    private var seedInFlight = false
    private var actionBindings: Map<String, PendingAction> = emptyMap()
    private var nextActionId = 0

    private val preferencesState: StateFlow<LauncherPreferences> = preferencesRepository.preferences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = LauncherPreferences(),
    )

    private val pinnedKeysState = pinnedAppsRepository.pinnedComponentKeys.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList(),
    )

    private val contentState = combine(
        appCatalogRepository.apps,
        pinnedKeysState,
        preferencesState,
        isDefaultLauncher,
    ) { apps, pinnedKeys, preferences, defaultLauncher ->
        val filteredApps = apps.filterNot { it.componentName.packageName == ownPackageName }
        val appMap = filteredApps.associateBy { it.componentKey }
        val pinnedSet = pinnedKeys.toSet()
        val pinnedRows = pinnedKeys.mapNotNull(appMap::get).map { it.toRow(isPinned = true) }
        val allLibraryRows = filteredApps.map { it.toRow(isPinned = it.componentKey in pinnedSet) }
        val searchCandidates = filteredApps.map {
            MinimalSearchCandidate(
                componentKey = it.componentKey,
                label = it.label,
                searchKey = it.searchKey,
            )
        }
        ContentState(
            hasAnyApps = apps.isNotEmpty(),
            appMap = appMap,
            pinnedSet = pinnedSet,
            pinnedRows = pinnedRows,
            allLibraryRows = allLibraryRows,
            searchCandidates = searchCandidates,
            preferences = preferences,
            isDefaultLauncher = defaultLauncher,
        )
    }.flowOn(Dispatchers.Default)

    private val surfaceState = combine(
        surface,
        homeQuery,
        libraryQuery,
        actionSheet,
    ) { currentSurface, currentHomeQuery, currentLibraryQuery, currentActionSheet ->
        SurfaceState(
            surface = currentSurface,
            homeQuery = currentHomeQuery,
            libraryQuery = currentLibraryQuery,
            actionSheet = currentActionSheet,
        )
    }

    val uiState: StateFlow<MinimalLauncherUiState> = combine(
        contentState,
        surfaceState,
    ) { content, surfaceState ->
        val preferences = content.preferences
        val currentSurface = surfaceState.surface
        val currentHomeQuery = surfaceState.homeQuery
        val currentLibraryQuery = surfaceState.libraryQuery
        val currentActionSheet = surfaceState.actionSheet
        val defaultLauncher = content.isDefaultLauncher

        val homeRows = if (currentHomeQuery.isBlank()) {
            content.pinnedRows
        } else {
            rankApps(content.searchCandidates, content.appMap, currentHomeQuery)
                .map { it.toRow(isPinned = it.componentKey in content.pinnedSet) }
        }
        val libraryRows = if (currentLibraryQuery.isBlank()) {
            content.allLibraryRows
        } else {
            rankApps(content.searchCandidates, content.appMap, currentLibraryQuery)
                .map { it.toRow(isPinned = it.componentKey in content.pinnedSet) }
        }

        MinimalLauncherUiState(
            surface = if (preferences.minimalModeEnabled) currentSurface else MinimalLauncherSurface.HOME,
            homeQuery = currentHomeQuery,
            libraryQuery = currentLibraryQuery,
            homeRows = homeRows,
            libraryRows = libraryRows,
            homeEmptyMessage = when {
                currentHomeQuery.isNotBlank() && homeRows.isEmpty() && content.hasAnyApps ->
                    searchEmptyString
                else -> null
            },
            libraryEmptyMessage = if (libraryRows.isEmpty() && content.hasAnyApps) {
                libraryEmptyString
            } else {
                null
            },
            showDefaultLauncherAction = !defaultLauncher,
            actionSheet = currentActionSheet,
        )
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = MinimalLauncherUiState(),
    )

    val launcherCommands = commands.asSharedFlow()

    init {
        refresh()
        observeMinimalSeed()
    }

    fun refresh() {
        appCatalogRepository.refresh()
        viewModelScope.launch(Dispatchers.IO) {
            isDefaultLauncher.value = homeRoleController.isDefaultLauncher()
        }
    }

    fun updateHomeQuery(query: String) {
        homeQuery.value = query
    }

    fun updateLibraryQuery(query: String) {
        libraryQuery.value = query
    }

    fun openAppLibrary() {
        dismissActionSheet()
        libraryQuery.value = ""
        surface.value = MinimalLauncherSurface.APP_LIBRARY
    }

    fun closeAppLibrary() {
        dismissActionSheet()
        libraryQuery.value = ""
        surface.value = MinimalLauncherSurface.HOME
    }

    fun requestDefaultLauncher() {
        commands.tryEmit(LauncherCommand.RequestDefaultLauncher)
    }

    fun requestAppActions(componentName: ComponentName) {
        val app = appCatalogRepository.apps.value.firstOrNull { it.componentName == componentName } ?: return
        val isPinned = pinnedKeysState.value.contains(app.componentKey)
        val actionItems = buildList {
            add(
                action(
                    title = if (isPinned) {
                        getApplication<Application>().getString(R.string.action_remove_from_home)
                    } else {
                        getApplication<Application>().getString(R.string.action_add_to_home)
                    },
                    pendingAction = if (isPinned) {
                        PendingAction.Unpin(app.componentKey)
                    } else {
                        PendingAction.Pin(app.componentKey)
                    },
                ),
            )
            add(
                action(
                    title = getApplication<Application>().getString(R.string.action_app_info),
                    pendingAction = PendingAction.AppInfo(app.componentName.packageName),
                ),
            )
            if (appActionController.canUninstall(app.componentName.packageName)) {
                add(
                    action(
                        title = getApplication<Application>().getString(R.string.action_uninstall),
                        pendingAction = PendingAction.Uninstall(app.componentName.packageName),
                        destructive = true,
                    ),
                )
            }
        }

        actionSheet.value = MinimalLauncherActionSheetUiState(
            title = app.label,
            subtitle = if (isPinned) {
                getApplication<Application>().getString(R.string.minimal_action_sheet_pinned)
            } else {
                getApplication<Application>().getString(R.string.minimal_action_sheet_library)
            },
            actions = actionItems,
        )
    }

    fun dismissActionSheet() {
        actionBindings = emptyMap()
        actionSheet.value = null
    }

    fun onActionSheetActionSelected(actionId: String) {
        val pendingAction = actionBindings[actionId] ?: return
        dismissActionSheet()
        when (pendingAction) {
            is PendingAction.Pin -> viewModelScope.launch {
                pinnedAppsRepository.pin(pendingAction.componentKey)
            }

            is PendingAction.Unpin -> viewModelScope.launch {
                pinnedAppsRepository.unpin(pendingAction.componentKey)
            }

            is PendingAction.AppInfo -> appActionController.openAppInfo(pendingAction.packageName)
            is PendingAction.Uninstall -> appActionController.requestUninstall(pendingAction.packageName)
        }
    }

    fun handleAppLaunched() {
        homeQuery.value = ""
        libraryQuery.value = ""
        actionSheet.value = null
        actionBindings = emptyMap()
        surface.value = MinimalLauncherSurface.HOME
    }

    fun onHomeIntent() {
        homeQuery.value = ""
        libraryQuery.value = ""
        actionSheet.value = null
        actionBindings = emptyMap()
        surface.value = MinimalLauncherSurface.HOME
    }

    fun handleBackPressed(): Boolean {
        if (actionSheet.value != null) {
            dismissActionSheet()
            return true
        }
        return when (surface.value) {
            MinimalLauncherSurface.APP_LIBRARY -> {
                if (libraryQuery.value.isNotBlank()) {
                    libraryQuery.value = ""
                } else {
                    surface.value = MinimalLauncherSurface.HOME
                }
                true
            }

            MinimalLauncherSurface.HOME -> {
                if (homeQuery.value.isNotBlank()) {
                    homeQuery.value = ""
                    true
                } else {
                    false
                }
            }
        }
    }

    private fun observeMinimalSeed() {
        viewModelScope.launch(Dispatchers.IO) {
            combine(
                appCatalogRepository.apps,
                pinnedKeysState,
                preferencesState,
            ) { apps, pinnedKeys, preferences ->
                Triple(apps, pinnedKeys, preferences)
            }.collect { (apps, pinnedKeys, preferences) ->
                if (seedInFlight || apps.isEmpty()) {
                    return@collect
                }
                seedInFlight = true
                try {
                    if (!preferences.minimalModeEnabled) {
                        preferencesRepository.setMinimalModeEnabled(true)
                    }
                    val filteredApps = apps.filterNot { it.componentName.packageName == ownPackageName }
                    pruneMissingPinnedApps(filteredApps, pinnedKeys)
                    if (!preferences.minimalModeMigrated) {
                        preferencesRepository.setMinimalModeMigrated(true)
                    }
                } finally {
                    seedInFlight = false
                }
            }
        }
    }

    private suspend fun pruneMissingPinnedApps(
        apps: List<LauncherAppInfo>,
        pinnedKeys: List<String>,
    ) {
        val availableKeys = apps.mapTo(linkedSetOf()) { it.componentKey }
        val validPinned = pinnedKeys.filter { it in availableKeys }
        if (validPinned != pinnedKeys) {
            pinnedAppsRepository.replace(validPinned)
        }
    }

    private fun rankApps(
        candidates: List<MinimalSearchCandidate>,
        appMap: Map<String, LauncherAppInfo>,
        query: String,
    ): List<LauncherAppInfo> {
        val rankedKeys = MinimalSearchEngine.search(candidates, query).map { it.componentKey }
        return rankedKeys.mapNotNull(appMap::get)
    }

    private fun LauncherAppInfo.toRow(isPinned: Boolean): MinimalLauncherRowUiState {
        return MinimalLauncherRowUiState(
            stableId = componentKey,
            componentName = componentName,
            title = label,
            isPinned = isPinned,
        )
    }

    private fun action(
        title: String,
        pendingAction: PendingAction,
        destructive: Boolean = false,
    ): MinimalLauncherActionUiState {
        val actionId = "minimal_action_${nextActionId++}"
        actionBindings = actionBindings + (actionId to pendingAction)
        return MinimalLauncherActionUiState(
            id = actionId,
            title = title,
            destructive = destructive,
        )
    }

    sealed interface LauncherCommand {
        data object RequestDefaultLauncher : LauncherCommand
    }

    private sealed interface PendingAction {
        data class Pin(val componentKey: String) : PendingAction
        data class Unpin(val componentKey: String) : PendingAction
        data class AppInfo(val packageName: String) : PendingAction
        data class Uninstall(val packageName: String) : PendingAction
    }

    private data class ContentState(
        val hasAnyApps: Boolean,
        val appMap: Map<String, LauncherAppInfo>,
        val pinnedSet: Set<String>,
        val pinnedRows: List<MinimalLauncherRowUiState>,
        val allLibraryRows: List<MinimalLauncherRowUiState>,
        val searchCandidates: List<MinimalSearchCandidate>,
        val preferences: LauncherPreferences,
        val isDefaultLauncher: Boolean,
    )

    private data class SurfaceState(
        val surface: MinimalLauncherSurface,
        val homeQuery: String,
        val libraryQuery: String,
        val actionSheet: MinimalLauncherActionSheetUiState?,
    )

    class Factory(
        private val application: Application,
        private val pinnedAppsRepository: PinnedAppsRepository,
        private val preferencesRepository: LauncherPreferencesRepository,
        private val appCatalogRepository: AppCatalogRepository,
        private val homeRoleController: HomeRoleController,
        private val appActionController: AppActionController,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MinimalLauncherViewModel::class.java)) {
                return MinimalLauncherViewModel(
                    application = application,
                    pinnedAppsRepository = pinnedAppsRepository,
                    preferencesRepository = preferencesRepository,
                    appCatalogRepository = appCatalogRepository,
                    homeRoleController = homeRoleController,
                    appActionController = appActionController,
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
