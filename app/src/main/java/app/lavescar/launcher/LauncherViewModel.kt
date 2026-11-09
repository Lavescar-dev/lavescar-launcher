package app.lavescar.launcher

import android.app.Application
import android.content.ComponentName
import android.graphics.drawable.Drawable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.lavescar.launcher.core.AppLibraryEmptyStateUiState
import app.lavescar.launcher.core.AppLibraryMode
import app.lavescar.launcher.core.AppLibraryUiState
import app.lavescar.launcher.core.DrawerAppItem
import app.lavescar.launcher.core.EditSheetActionUiState
import app.lavescar.launcher.core.EditSheetUiState
import app.lavescar.launcher.core.FolderChildItem
import app.lavescar.launcher.core.FolderSheetUiState
import app.lavescar.launcher.core.HomePageUiState
import app.lavescar.launcher.core.LauncherActionSheetAction
import app.lavescar.launcher.core.LauncherActionSheetPresentation
import app.lavescar.launcher.core.LauncherActionSheetSection
import app.lavescar.launcher.core.LauncherActionSheetState
import app.lavescar.launcher.core.LauncherContainerKind
import app.lavescar.launcher.core.LauncherDropTarget
import app.lavescar.launcher.core.LauncherSurfaceMode
import app.lavescar.launcher.core.LauncherSurfaceAnchor
import app.lavescar.launcher.core.LauncherUiState
import app.lavescar.launcher.core.SpotlightUiState
import app.lavescar.launcher.core.WorkspaceSurfaceItem
import app.lavescar.launcher.data.AppShortcutReference
import app.lavescar.launcher.data.LauncherPreferences
import app.lavescar.launcher.data.LauncherPreferencesRepository
import app.lavescar.launcher.data.WidgetReference
import app.lavescar.launcher.data.WorkspaceContainerType
import app.lavescar.launcher.data.WorkspaceDropTarget
import app.lavescar.launcher.data.WorkspaceItemModel
import app.lavescar.launcher.data.WorkspaceModel
import app.lavescar.launcher.data.WorkspaceRepository
import app.lavescar.launcher.system.AppActionController
import app.lavescar.launcher.system.AppCatalogRepository
import app.lavescar.launcher.system.HomeRoleController
import app.lavescar.launcher.system.HomeSeedDefaultsController
import app.lavescar.launcher.system.HostedWidgetPlacement
import app.lavescar.launcher.system.LauncherAppInfo
import app.lavescar.launcher.system.WidgetHostController
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LauncherViewModel(
    application: Application,
    private val workspaceRepository: WorkspaceRepository,
    private val preferencesRepository: LauncherPreferencesRepository,
    private val appCatalogRepository: AppCatalogRepository,
    private val homeRoleController: HomeRoleController,
    private val homeSeedDefaultsController: HomeSeedDefaultsController,
    private val appActionController: AppActionController,
    private val widgetHostController: WidgetHostController,
) : AndroidViewModel(application) {

    private val placeholderIcon: Drawable = application.packageManager.defaultActivityIcon
    private val appLibraryQuery = MutableStateFlow("")
    private val spotlightQuery = MutableStateFlow("")
    private val surfaceMode = MutableStateFlow(LauncherSurfaceMode.HOME)
    private val currentHomePageIndex = MutableStateFlow(0)
    private val openFolderId = MutableStateFlow<String?>(null)
    private val openFolderAnchor = MutableStateFlow<LauncherSurfaceAnchor?>(null)
    private val actionSheet = MutableStateFlow<LauncherActionSheetState?>(null)
    private val editSheet = MutableStateFlow<EditSheetUiState?>(null)
    private val showDefaultPrompt = MutableStateFlow(false)
    private val commands = MutableSharedFlow<LauncherCommand>(extraBufferCapacity = 1)

    private var actionBindings: Map<String, PendingAction> = emptyMap()
    private var editBindings: Map<String, PendingEditAction> = emptyMap()
    private var nextActionId = 0
    private var homeSeedInFlight = false

    private val workspaceState: StateFlow<WorkspaceModel> = workspaceRepository.workspace.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = WorkspaceModel(pages = emptyList(), dockItems = emptyList()),
    )

    private val preferencesState: StateFlow<LauncherPreferences> = preferencesRepository.preferences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = LauncherPreferences(),
    )

    private val contentState = combine(
        appCatalogRepository.apps,
        workspaceState,
        preferencesState,
        appLibraryQuery,
        spotlightQuery,
    ) { apps, workspace, preferences, libraryQuery, spotlightQuery ->
        ContentState(
            apps = apps,
            workspace = workspace,
            preferences = preferences,
            appLibraryQuery = libraryQuery,
            spotlightQuery = spotlightQuery,
        )
    }

    private val surfaceState = combine(
        currentHomePageIndex,
        surfaceMode,
        openFolderId,
        openFolderAnchor,
        actionSheet,
        editSheet,
    ) { values ->
        SurfaceState(
            currentHomePageIndex = values[0] as Int,
            surfaceMode = values[1] as LauncherSurfaceMode,
            folderId = values[2] as String?,
            folderAnchor = values[3] as LauncherSurfaceAnchor?,
            actionSheetState = values[4] as LauncherActionSheetState?,
            editSheetState = values[5] as EditSheetUiState?,
        )
    }

    val uiState: StateFlow<LauncherUiState> = combine(
        contentState,
        surfaceState,
    ) { content, surface ->
        val appMap = content.apps.associateBy { it.componentKey }
        val appLibraryMode = surface.surfaceMode.toAppLibraryMode()
        val appLibrarySearchRows = if (appLibraryMode == AppLibraryMode.SEARCH) {
            AppLibraryBuilder.buildSearchRows(content.apps, content.appLibraryQuery)
        } else {
            emptyList()
        }
        val appLibrary = AppLibraryUiState(
            mode = appLibraryMode,
            query = content.appLibraryQuery,
            overviewBlocks = AppLibraryBuilder.buildOverviewBlocks(
                apps = content.apps,
                workspace = content.workspace,
                suggestedTitle = getApplication<Application>().getString(R.string.launcher_suggested_title),
                recentlyAddedTitle = getApplication<Application>().getString(R.string.launcher_recently_added_title),
            ),
            searchRows = appLibrarySearchRows,
            emptyState = if (appLibraryMode == AppLibraryMode.SEARCH && appLibrarySearchRows.isEmpty()) {
                AppLibraryEmptyStateUiState(
                    title = getApplication<Application>().getString(R.string.launcher_search_empty_title),
                    subtitle = getApplication<Application>().getString(R.string.launcher_search_empty_subtitle),
                )
            } else {
                null
            },
        )
        val spotlightSearchRows = if (content.spotlightQuery.isBlank()) {
            emptyList()
        } else {
            AppLibraryBuilder.buildSearchRows(content.apps, content.spotlightQuery)
        }
        val spotlight = SpotlightUiState(
            query = content.spotlightQuery,
            suggestedApps = AppLibraryBuilder.buildSuggestedApps(content.apps, content.workspace)
                .map { it.toDrawerItem() },
            searchRows = spotlightSearchRows,
            emptyState = if (content.spotlightQuery.isNotBlank() && spotlightSearchRows.isEmpty()) {
                AppLibraryEmptyStateUiState(
                    title = getApplication<Application>().getString(R.string.launcher_search_empty_title),
                    subtitle = getApplication<Application>().getString(R.string.launcher_search_empty_subtitle),
                )
            } else {
                null
            },
        )

        val homePages = content.workspace.pages.map { page ->
            HomePageUiState(
                page = page.page,
                items = page.items.map { it.toSurfaceItem(appMap, placeholderIcon) },
            )
        }.ifEmpty {
            listOf(HomePageUiState(page = 0, items = emptyList()))
        }
        val clampedHomePageIndex = surface.currentHomePageIndex.coerceIn(0, homePages.lastIndex)
        val dockItems = content.workspace.dockItems.map { it.toSurfaceItem(appMap, placeholderIcon) }
        val folder = content.workspace.findFolder(surface.folderId)?.toFolderSheetUiState(
            appMap = appMap,
            fallbackIcon = placeholderIcon,
            anchor = surface.folderAnchor,
        )

        LauncherUiState(
            homePages = homePages,
            currentHomePageIndex = clampedHomePageIndex,
            surfaceMode = surface.surfaceMode,
            dockItems = dockItems,
            appLibrary = appLibrary,
            spotlight = spotlight,
            drawerOpen = surface.surfaceMode.isAppLibraryVisible(),
            openFolder = folder,
            actionSheet = surface.actionSheetState,
            editSheet = surface.editSheetState,
            emptyWorkspaceHintVisible = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = LauncherUiState(),
    )

    val launcherCommands = commands.asSharedFlow()

    init {
        refresh()
        observeInitialHomeSeed()
    }

    fun refresh() {
        appCatalogRepository.refresh()
        showDefaultPrompt.value = !homeRoleController.isDefaultLauncher()
    }

    fun openSpotlight() {
        surfaceMode.value = LauncherSurfaceMode.SPOTLIGHT
        appLibraryQuery.value = ""
        openFolderId.value = null
        openFolderAnchor.value = null
        dismissEditSheet()
        dismissActionSheet()
    }

    fun closeSpotlight() {
        spotlightQuery.value = ""
        surfaceMode.value = LauncherSurfaceMode.HOME
    }

    fun openAppLibrary() {
        currentHomePageIndex.value = workspaceState.value.pages.lastIndex.coerceAtLeast(0)
        surfaceMode.value = LauncherSurfaceMode.APP_LIBRARY_OVERVIEW
        appLibraryQuery.value = ""
        openFolderId.value = null
        openFolderAnchor.value = null
        dismissEditSheet()
        dismissActionSheet()
    }

    fun enterAppLibrarySearch() {
        surfaceMode.value = LauncherSurfaceMode.APP_LIBRARY_SEARCH
    }

    fun closeAppLibrary() {
        appLibraryQuery.value = ""
        surfaceMode.value = LauncherSurfaceMode.HOME
    }

    fun updateAppLibraryQuery(query: String) {
        appLibraryQuery.value = query
        if (surfaceMode.value.isAppLibraryVisible()) {
            surfaceMode.value = LauncherSurfaceMode.APP_LIBRARY_SEARCH
        }
    }

    fun updateSpotlightQuery(query: String) {
        spotlightQuery.value = query
    }

    fun navigateHome(pageIndex: Int) {
        currentHomePageIndex.value = pageIndex.coerceIn(0, workspaceState.value.pages.lastIndex.coerceAtLeast(0))
        surfaceMode.value = LauncherSurfaceMode.HOME
    }

    fun openFolder(folderId: String, anchor: LauncherSurfaceAnchor) {
        surfaceMode.value = LauncherSurfaceMode.HOME
        dismissEditSheet()
        dismissActionSheet()
        openFolderId.value = folderId
        openFolderAnchor.value = anchor
    }

    fun closeFolder() {
        openFolderId.value = null
        openFolderAnchor.value = null
    }

    fun requestWorkspaceMenu() {
        dismissTransientSurfaces()
        val actions = buildList {
            add(
                buildEditAction(
                    title = getApplication<Application>().getString(R.string.action_add_widget),
                    subtitle = getApplication<Application>().getString(R.string.action_add_widget_subtitle),
                    action = PendingEditAction.AddWidget,
                ),
            )
            if (showDefaultPrompt.value) {
                add(
                    buildEditAction(
                        title = getApplication<Application>().getString(R.string.action_set_default_launcher),
                        subtitle = getApplication<Application>().getString(R.string.action_set_default_launcher_subtitle),
                        action = PendingEditAction.RequestDefaultLauncher,
                    ),
                )
            }
            add(
                buildEditAction(
                    title = getApplication<Application>().getString(R.string.action_apply_sample_wallpaper),
                    subtitle = getApplication<Application>().getString(R.string.action_apply_sample_wallpaper_subtitle),
                    action = PendingEditAction.ApplySampleWallpaper,
                ),
            )
        }
        showEditSheet(
            title = getApplication<Application>().getString(R.string.action_sheet_workspace_title),
            subtitle = getApplication<Application>().getString(R.string.action_sheet_workspace_subtitle),
            actions = actions,
        )
    }

    fun requestDrawerItemActions(app: DrawerAppItem, anchor: LauncherSurfaceAnchor) {
        viewModelScope.launch {
            val shortcuts = appActionController.loadShortcutActions(app.componentName)
            val primaryActions = buildList {
                add(
                    buildAction(
                        title = getApplication<Application>().getString(R.string.action_add_to_home),
                        subtitle = getApplication<Application>().getString(R.string.action_add_to_home_subtitle),
                        action = PendingAction.AddDrawerShortcut(app),
                    ),
                )
                add(
                    buildAction(
                        title = getApplication<Application>().getString(R.string.action_app_info),
                        action = PendingAction.OpenAppInfo(app.componentName.packageName),
                    ),
                )
            }
            val shortcutActions = shortcuts.map { shortcut ->
                buildAction(
                    title = shortcut.title,
                    subtitle = shortcut.subtitle,
                    action = PendingAction.LaunchShortcut(app.componentName, shortcut.id),
                )
            }
            val destructiveActions = buildList {
                if (appActionController.canUninstall(app.componentName.packageName)) {
                    add(
                        buildAction(
                            title = getApplication<Application>().getString(R.string.action_uninstall),
                            destructive = true,
                            action = PendingAction.Uninstall(app.componentName.packageName),
                        ),
                    )
                }
            }
            showActionSheet(
                title = app.title,
                icon = app.icon,
                sections = buildList {
                    add(BoundActionSection(actions = primaryActions))
                    if (shortcutActions.isNotEmpty()) {
                        add(
                            BoundActionSection(
                                title = getApplication<Application>().getString(R.string.action_section_shortcuts),
                                actions = shortcutActions,
                            ),
                        )
                    }
                    if (destructiveActions.isNotEmpty()) {
                        add(BoundActionSection(actions = destructiveActions))
                    }
                },
                presentation = LauncherActionSheetPresentation.Anchored(anchor),
            )
        }
    }

    fun requestWorkspaceItemActions(itemId: String, anchor: LauncherSurfaceAnchor) {
        viewModelScope.launch {
            val item = workspaceState.value.findTopLevelItem(itemId) ?: return@launch
            val appMap = appCatalogRepository.apps.value.associateBy { it.componentKey }
            when (item) {
                is WorkspaceItemModel.AppShortcut -> {
                    val componentName = ComponentName(item.packageName, item.className)
                    val shortcuts = appActionController.loadShortcutActions(componentName)
                    val primaryActions = buildList {
                        add(
                            buildAction(
                                title = getApplication<Application>().getString(R.string.action_move_item),
                                subtitle = getApplication<Application>().getString(R.string.action_move_item_subtitle),
                                action = PendingAction.StartWorkspaceMove(itemId),
                            ),
                        )
                        add(
                            buildAction(
                                title = getApplication<Application>().getString(R.string.action_app_info),
                                action = PendingAction.OpenAppInfo(item.packageName),
                            ),
                        )
                    }
                    val shortcutActions = shortcuts.map { shortcut ->
                        buildAction(
                            title = shortcut.title,
                            subtitle = shortcut.subtitle,
                            action = PendingAction.LaunchShortcut(componentName, shortcut.id),
                        )
                    }
                    val destructiveActions = buildList {
                        add(
                            buildAction(
                                title = getApplication<Application>().getString(R.string.action_remove_from_home),
                                destructive = true,
                                action = PendingAction.RemoveWorkspaceItem(itemId),
                            ),
                        )
                        if (appActionController.canUninstall(item.packageName)) {
                            add(
                                buildAction(
                                    title = getApplication<Application>().getString(R.string.action_uninstall),
                                    destructive = true,
                                    action = PendingAction.Uninstall(item.packageName),
                                ),
                            )
                        }
                    }
                    showActionSheet(
                        title = item.title,
                        icon = appMap[componentKey(item.packageName, item.className)]?.icon ?: placeholderIcon,
                        sections = buildList {
                            add(BoundActionSection(actions = primaryActions))
                            if (shortcutActions.isNotEmpty()) {
                                add(
                                    BoundActionSection(
                                        title = getApplication<Application>().getString(R.string.action_section_shortcuts),
                                        actions = shortcutActions,
                                    ),
                                )
                            }
                            add(BoundActionSection(actions = destructiveActions))
                        },
                        presentation = LauncherActionSheetPresentation.Anchored(anchor),
                    )
                }

                is WorkspaceItemModel.Widget -> {
                    showActionSheet(
                        title = item.title,
                        subtitle = getApplication<Application>().getString(R.string.action_widget_sheet_subtitle),
                        sections = listOf(
                            BoundActionSection(
                                actions = listOf(
                                    buildAction(
                                        title = getApplication<Application>().getString(R.string.action_remove_widget),
                                        destructive = true,
                                        action = PendingAction.RemoveWorkspaceItem(itemId),
                                    ),
                                ),
                            ),
                        ),
                        presentation = LauncherActionSheetPresentation.Anchored(anchor),
                    )
                }

                is WorkspaceItemModel.Folder -> Unit
            }
        }
    }

    fun confirmRenameFolder(folderId: String, title: String) {
        viewModelScope.launch {
            workspaceRepository.renameFolder(folderId, title)
            openFolderId.value = folderId
        }
    }

    fun dismissActionSheet() {
        actionBindings = emptyMap()
        actionSheet.value = null
    }

    fun dismissEditSheet() {
        editBindings = emptyMap()
        editSheet.value = null
    }

    fun onSampleWallpaperApplyResult(success: Boolean) {
        if (!success) {
            return
        }
        viewModelScope.launch {
            preferencesRepository.setSampleWallpaperApplied(true)
        }
    }

    fun onActionSheetActionSelected(actionId: String) {
        val action = actionBindings[actionId] ?: return
        dismissActionSheet()
        when (action) {
            PendingAction.AddWidget -> commands.tryEmit(LauncherCommand.StartAddWidgetFlow)

            is PendingAction.AddDrawerShortcut -> addDrawerAppToHome(action.app)

            is PendingAction.StartWorkspaceMove -> commands.tryEmit(LauncherCommand.StartWorkspaceItemMove(action.itemId))

            is PendingAction.RemoveWorkspaceItem -> removeWorkspaceItem(action.itemId)

            is PendingAction.OpenAppInfo -> appActionController.openAppInfo(action.packageName)

            is PendingAction.Uninstall -> appActionController.requestUninstall(action.packageName)

            is PendingAction.LaunchShortcut -> {
                if (appActionController.launchShortcut(action.componentName, action.shortcutId)) {
                    if (surfaceMode.value.isAppLibraryVisible()) {
                        closeAppLibrary()
                    }
                    if (surfaceMode.value == LauncherSurfaceMode.SPOTLIGHT) {
                        closeSpotlight()
                    }
                    openFolderId.value = null
                    openFolderAnchor.value = null
                }
            }
        }
    }

    fun onEditSheetActionSelected(actionId: String) {
        val action = editBindings[actionId] ?: return
        dismissEditSheet()
        when (action) {
            PendingEditAction.AddWidget -> commands.tryEmit(LauncherCommand.StartAddWidgetFlow)
            PendingEditAction.RequestDefaultLauncher -> commands.tryEmit(LauncherCommand.RequestDefaultLauncher)
            PendingEditAction.ApplySampleWallpaper -> commands.tryEmit(LauncherCommand.ApplySampleWallpaper)
        }
    }

    fun addDrawerAppToWorkspace(
        app: DrawerAppItem,
        target: LauncherDropTarget,
        targetItemId: String?,
    ) {
        viewModelScope.launch {
            workspaceRepository.addShortcut(
                app = AppShortcutReference(
                    packageName = app.componentName.packageName,
                    className = app.componentName.className,
                    title = app.title,
                ),
                target = target.toDataTarget(),
                targetItemId = targetItemId,
            )
            when {
                surfaceMode.value.isAppLibraryVisible() -> closeAppLibrary()
                surfaceMode.value == LauncherSurfaceMode.SPOTLIGHT -> closeSpotlight()
            }
        }
    }

    fun addWidgetPlacement(placement: HostedWidgetPlacement) {
        viewModelScope.launch {
            val added = workspaceRepository.addWidget(
                widget = WidgetReference(
                    appWidgetId = placement.appWidgetId,
                    providerPackageName = placement.provider.packageName,
                    providerClassName = placement.provider.className,
                    title = placement.label,
                    spanX = placement.spanX,
                    spanY = placement.spanY,
                ),
                target = WorkspaceDropTarget(
                    container = WorkspaceContainerType.PAGE,
                    page = currentWorkspacePageNumber(),
                    position = currentWorkspacePageItems().size,
                ),
            )
            if (!added) {
                widgetHostController.deleteAppWidgetId(placement.appWidgetId)
            }
        }
    }

    fun moveWorkspaceItem(itemId: String, target: LauncherDropTarget, targetItemId: String?) {
        viewModelScope.launch {
            workspaceRepository.moveItem(itemId, target.toDataTarget(), targetItemId)
        }
    }

    fun removeWorkspaceItem(itemId: String) {
        viewModelScope.launch {
            val widgetId = (workspaceState.value.findTopLevelItem(itemId) as? WorkspaceItemModel.Widget)?.appWidgetId
            workspaceRepository.removeItem(itemId)
            widgetId?.let(widgetHostController::deleteAppWidgetId)
            if (openFolderId.value == itemId) {
                openFolderId.value = null
                openFolderAnchor.value = null
            }
        }
    }

    fun handleAppLaunched(componentName: ComponentName) {
        if (surfaceMode.value.isAppLibraryVisible()) {
            closeAppLibrary()
        }
        if (surfaceMode.value == LauncherSurfaceMode.SPOTLIGHT) {
            closeSpotlight()
        }
        openFolderId.value = null
        openFolderAnchor.value = null
        dismissActionSheet()
    }

    fun closeTopSurface(): Boolean {
        return when {
            actionSheet.value != null -> {
                dismissActionSheet()
                true
            }

            editSheet.value != null -> {
                dismissEditSheet()
                true
            }

            openFolderId.value != null -> {
                openFolderId.value = null
                openFolderAnchor.value = null
                true
            }

            surfaceMode.value == LauncherSurfaceMode.SPOTLIGHT -> {
                closeSpotlight()
                true
            }

            surfaceMode.value == LauncherSurfaceMode.APP_LIBRARY_SEARCH -> {
                appLibraryQuery.value = ""
                surfaceMode.value = LauncherSurfaceMode.APP_LIBRARY_OVERVIEW
                true
            }

            surfaceMode.value == LauncherSurfaceMode.APP_LIBRARY_OVERVIEW -> {
                closeAppLibrary()
                true
            }

            else -> false
        }
    }

    private fun addDrawerAppToHome(app: DrawerAppItem) {
        viewModelScope.launch {
            workspaceRepository.addShortcut(
                app = AppShortcutReference(
                    packageName = app.componentName.packageName,
                    className = app.componentName.className,
                    title = app.title,
                ),
                target = WorkspaceDropTarget(
                    container = WorkspaceContainerType.PAGE,
                    page = currentWorkspacePageNumber(),
                    position = currentWorkspacePageItems().size,
                ),
            )
            when {
                surfaceMode.value.isAppLibraryVisible() -> closeAppLibrary()
                surfaceMode.value == LauncherSurfaceMode.SPOTLIGHT -> closeSpotlight()
            }
        }
    }

    private fun dismissTransientSurfaces() {
        if (surfaceMode.value.isAppLibraryVisible()) {
            closeAppLibrary()
        }
        if (surfaceMode.value == LauncherSurfaceMode.SPOTLIGHT) {
            closeSpotlight()
        }
        openFolderId.value = null
        openFolderAnchor.value = null
        dismissActionSheet()
        dismissEditSheet()
    }

    private fun observeInitialHomeSeed() {
        viewModelScope.launch {
            combine(
                appCatalogRepository.apps,
                workspaceState,
                preferencesState,
            ) { apps, workspace, preferences ->
                Triple(apps, workspace, preferences)
            }.collect { (apps, workspace, preferences) ->
                if (homeSeedInFlight) {
                    return@collect
                }
                if (!HomeSeedPlanner.shouldSeed(
                        hasCompletedHomeSeed = preferences.hasCompletedHomeSeed,
                        hasWorkspaceItems = workspace.pages.any { it.items.isNotEmpty() } || workspace.dockItems.isNotEmpty(),
                        availableAppCount = apps.size,
                    )
                ) {
                    return@collect
                }

                homeSeedInFlight = true
                try {
                    seedInitialWorkspace(apps)
                    preferencesRepository.setHomeSeedCompleted(true)
                } finally {
                    homeSeedInFlight = false
                }
            }
        }
    }

    private suspend fun seedInitialWorkspace(apps: List<LauncherAppInfo>) {
        val seedPlan = HomeSeedPlanner.createPlan(
            apps = apps.map {
                HomeSeedCandidate(
                    packageName = it.componentName.packageName,
                    className = it.componentName.className,
                    label = it.label,
                )
            },
            ownPackageName = getApplication<Application>().packageName,
            preferredDockComponentKeys = homeSeedDefaultsController.preferredDockComponents()
                .map(ComponentName::flattenToShortString),
        )

        if (seedPlan.dockItems.isEmpty() && seedPlan.pageItems.isEmpty()) {
            return
        }

        seedPlan.dockItems.forEachIndexed { index, app ->
            workspaceRepository.addShortcut(
                app = AppShortcutReference(
                    packageName = app.packageName,
                    className = app.className,
                    title = app.label,
                ),
                target = WorkspaceDropTarget(
                    container = WorkspaceContainerType.DOCK,
                    page = 0,
                    position = index,
                ),
            )
        }

        seedPlan.pageItems.forEachIndexed { index, app ->
            workspaceRepository.addShortcut(
                app = AppShortcutReference(
                    packageName = app.packageName,
                    className = app.className,
                    title = app.label,
                ),
                target = WorkspaceDropTarget(
                    container = WorkspaceContainerType.PAGE,
                    page = 0,
                    position = index,
                ),
            )
        }
    }

    private fun buildAction(
        title: String,
        subtitle: String? = null,
        destructive: Boolean = false,
        action: PendingAction,
    ): BoundAction {
        val id = "action_${nextActionId++}"
        return BoundAction(
            ui = LauncherActionSheetAction(
                id = id,
                title = title,
                subtitle = subtitle,
                destructive = destructive,
            ),
            action = action,
        )
    }

    private fun buildEditAction(
        title: String,
        subtitle: String? = null,
        action: PendingEditAction,
    ): BoundEditAction {
        val id = "edit_action_${nextActionId++}"
        return BoundEditAction(
            ui = EditSheetActionUiState(
                id = id,
                title = title,
                subtitle = subtitle,
            ),
            action = action,
        )
    }

    private fun showActionSheet(
        title: String,
        subtitle: String? = null,
        icon: Drawable? = null,
        sections: List<BoundActionSection>,
        presentation: LauncherActionSheetPresentation,
    ) {
        dismissEditSheet()
        actionBindings = sections
            .flatMap { section -> section.actions }
            .associate { it.ui.id to it.action }
        actionSheet.value = LauncherActionSheetState(
            title = title,
            subtitle = subtitle,
            icon = icon,
            sections = sections.map { section ->
                LauncherActionSheetSection(
                    title = section.title,
                    actions = section.actions.map(BoundAction::ui),
                )
            },
            presentation = presentation,
        )
    }

    private fun showEditSheet(
        title: String,
        subtitle: String? = null,
        actions: List<BoundEditAction>,
    ) {
        dismissActionSheet()
        editBindings = actions.associate { it.ui.id to it.action }
        editSheet.value = EditSheetUiState(
            title = title,
            subtitle = subtitle,
            actions = actions.map(BoundEditAction::ui),
        )
    }

    private fun LauncherDropTarget.toDataTarget(): WorkspaceDropTarget {
        val targetPage = when (container) {
            LauncherContainerKind.PAGE -> currentWorkspacePageNumber()
            else -> page
        }
        return WorkspaceDropTarget(
            container = when (container) {
                LauncherContainerKind.PAGE -> WorkspaceContainerType.PAGE
                LauncherContainerKind.DOCK -> WorkspaceContainerType.DOCK
                LauncherContainerKind.FOLDER -> WorkspaceContainerType.FOLDER
            },
            page = targetPage,
            containerId = containerId,
            position = position,
        )
    }

    private fun WorkspaceModel.findFolder(folderId: String?): WorkspaceItemModel.Folder? {
        if (folderId == null) {
            return null
        }
        return allTopLevelItems()
            .filterIsInstance<WorkspaceItemModel.Folder>()
            .firstOrNull { it.id == folderId }
    }

    private fun WorkspaceModel.findTopLevelItem(itemId: String): WorkspaceItemModel? {
        return allTopLevelItems().firstOrNull { it.id == itemId }
    }

    private fun WorkspaceModel.allTopLevelItems(): List<WorkspaceItemModel> {
        return pages.flatMap { it.items } + dockItems
    }

    private fun currentWorkspacePageItems(): List<WorkspaceItemModel> {
        val workspace = workspaceState.value
        return workspace.pages
            .getOrNull(currentHomePageIndex.value)
            ?.items
            ?: workspace.pageItems
    }

    private fun currentWorkspacePageNumber(): Int {
        val workspace = workspaceState.value
        return workspace.pages
            .getOrNull(currentHomePageIndex.value)
            ?.page
            ?: 0
    }

    private fun WorkspaceItemModel.toSurfaceItem(
        appMap: Map<String, LauncherAppInfo>,
        fallbackIcon: Drawable,
    ): WorkspaceSurfaceItem {
        return when (this) {
            is WorkspaceItemModel.AppShortcut -> WorkspaceSurfaceItem.AppShortcut(
                id = id,
                title = title,
                componentName = ComponentName(packageName, className),
                icon = appMap[componentKey(packageName, className)]?.icon ?: fallbackIcon,
            )

            is WorkspaceItemModel.Folder -> WorkspaceSurfaceItem.Folder(
                id = id,
                title = title,
                previewIcons = items.take(4).map { child ->
                    appMap[componentKey(child.packageName, child.className)]?.icon ?: fallbackIcon
                },
                items = items.map { child ->
                    FolderChildItem(
                        id = child.id,
                        componentName = ComponentName(child.packageName, child.className),
                        title = child.title,
                        icon = appMap[componentKey(child.packageName, child.className)]?.icon ?: fallbackIcon,
                    )
                },
            )

            is WorkspaceItemModel.Widget -> WorkspaceSurfaceItem.Widget(
                id = id,
                title = title,
                appWidgetId = appWidgetId,
                spanX = spanX,
                spanY = spanY,
                providerPackageName = providerPackageName,
                providerClassName = providerClassName,
            )
        }
    }

    private fun WorkspaceItemModel.Folder.toFolderSheetUiState(
        appMap: Map<String, LauncherAppInfo>,
        fallbackIcon: Drawable,
        anchor: LauncherSurfaceAnchor?,
    ): FolderSheetUiState {
        return FolderSheetUiState(
            folderId = id,
            title = title,
            anchor = anchor,
            pages = AppLibraryBuilder.paginateFolderItems(
                items.map { item ->
                    FolderChildItem(
                        id = item.id,
                        componentName = ComponentName(item.packageName, item.className),
                        title = item.title,
                        icon = appMap[componentKey(item.packageName, item.className)]?.icon ?: fallbackIcon,
                    )
                },
            ),
        )
    }

    private fun LauncherAppInfo.toDrawerItem(): DrawerAppItem {
        return DrawerAppItem(
            componentName = componentName,
            title = label,
            icon = icon,
        )
    }

    private fun componentKey(packageName: String, className: String): String {
        return ComponentName(packageName, className).flattenToShortString()
    }

    private data class ContentState(
        val apps: List<LauncherAppInfo>,
        val workspace: WorkspaceModel,
        val preferences: LauncherPreferences,
        val appLibraryQuery: String,
        val spotlightQuery: String,
    )

    private data class SurfaceState(
        val currentHomePageIndex: Int,
        val surfaceMode: LauncherSurfaceMode,
        val folderId: String?,
        val folderAnchor: LauncherSurfaceAnchor?,
        val actionSheetState: LauncherActionSheetState?,
        val editSheetState: EditSheetUiState?,
    )

    private data class BoundAction(
        val ui: LauncherActionSheetAction,
        val action: PendingAction,
    )

    private data class BoundActionSection(
        val title: String? = null,
        val actions: List<BoundAction>,
    )

    private data class BoundEditAction(
        val ui: EditSheetActionUiState,
        val action: PendingEditAction,
    )

    private sealed interface PendingAction {
        data object AddWidget : PendingAction
        data class AddDrawerShortcut(val app: DrawerAppItem) : PendingAction
        data class StartWorkspaceMove(val itemId: String) : PendingAction
        data class RemoveWorkspaceItem(val itemId: String) : PendingAction
        data class OpenAppInfo(val packageName: String) : PendingAction
        data class Uninstall(val packageName: String) : PendingAction
        data class LaunchShortcut(
            val componentName: ComponentName,
            val shortcutId: String,
        ) : PendingAction
    }

    private sealed interface PendingEditAction {
        data object AddWidget : PendingEditAction
        data object RequestDefaultLauncher : PendingEditAction
        data object ApplySampleWallpaper : PendingEditAction
    }

    sealed interface LauncherCommand {
        data object StartAddWidgetFlow : LauncherCommand
        data object RequestDefaultLauncher : LauncherCommand
        data object ApplySampleWallpaper : LauncherCommand
        data class StartWorkspaceItemMove(val itemId: String) : LauncherCommand
    }

    private fun LauncherSurfaceMode.toAppLibraryMode(): AppLibraryMode {
        return if (this == LauncherSurfaceMode.APP_LIBRARY_SEARCH) {
            AppLibraryMode.SEARCH
        } else {
            AppLibraryMode.OVERVIEW
        }
    }

    private fun LauncherSurfaceMode.isAppLibraryVisible(): Boolean {
        return this == LauncherSurfaceMode.APP_LIBRARY_OVERVIEW || this == LauncherSurfaceMode.APP_LIBRARY_SEARCH
    }

    class Factory(
        private val application: Application,
        private val workspaceRepository: WorkspaceRepository,
        private val preferencesRepository: LauncherPreferencesRepository,
        private val appCatalogRepository: AppCatalogRepository,
        private val homeRoleController: HomeRoleController,
        private val homeSeedDefaultsController: HomeSeedDefaultsController,
        private val appActionController: AppActionController,
        private val widgetHostController: WidgetHostController,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LauncherViewModel(
                application = application,
                workspaceRepository = workspaceRepository,
                preferencesRepository = preferencesRepository,
                appCatalogRepository = appCatalogRepository,
                homeRoleController = homeRoleController,
                homeSeedDefaultsController = homeSeedDefaultsController,
                appActionController = appActionController,
                widgetHostController = widgetHostController,
            ) as T
        }
    }
}
