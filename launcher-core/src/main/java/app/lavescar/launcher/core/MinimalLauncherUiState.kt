package app.lavescar.launcher.core

import android.content.ComponentName

enum class MinimalLauncherSurface {
    HOME,
    APP_LIBRARY,
}

data class MinimalLauncherRowUiState(
    val stableId: String,
    val componentName: ComponentName,
    val title: String,
    val isPinned: Boolean,
)

data class MinimalLauncherActionUiState(
    val id: String,
    val title: String,
    val destructive: Boolean = false,
)

data class MinimalLauncherActionSheetUiState(
    val title: String,
    val subtitle: String? = null,
    val actions: List<MinimalLauncherActionUiState> = emptyList(),
)

data class MinimalLauncherUiState(
    val surface: MinimalLauncherSurface = MinimalLauncherSurface.HOME,
    val homeQuery: String = "",
    val libraryQuery: String = "",
    val homeRows: List<MinimalLauncherRowUiState> = emptyList(),
    val libraryRows: List<MinimalLauncherRowUiState> = emptyList(),
    val homeEmptyMessage: String? = null,
    val libraryEmptyMessage: String? = null,
    val showDefaultLauncherAction: Boolean = false,
    val actionSheet: MinimalLauncherActionSheetUiState? = null,
)

interface MinimalLauncherRootCallbacks {
    fun onHomeQueryChanged(query: String)

    fun onLibraryQueryChanged(query: String)

    fun onOpenAppLibrary()

    fun onCloseAppLibrary()

    fun onLaunchApp(componentName: ComponentName)

    fun onRequestAppActions(componentName: ComponentName)

    fun onDismissActionSheet()

    fun onSelectActionSheetAction(actionId: String)

    fun onRequestDefaultLauncher()
}
