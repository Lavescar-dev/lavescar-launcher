package app.lavescar.launcher.core

import android.content.ComponentName
import android.graphics.drawable.Drawable

data class DrawerAppItem(
    val componentName: ComponentName,
    val title: String,
    val icon: Drawable?,
) {
    val componentKey: String = componentName.flattenToShortString()
}

data class FolderChildItem(
    val id: String,
    val componentName: ComponentName,
    val title: String,
    val icon: Drawable?,
)

data class FolderPageUiState(
    val index: Int,
    val items: List<FolderChildItem>,
)

sealed interface WorkspaceSurfaceItem {
    val id: String
    val title: String

    data class AppShortcut(
        override val id: String,
        override val title: String,
        val componentName: ComponentName,
        val icon: Drawable?,
    ) : WorkspaceSurfaceItem

    data class Folder(
        override val id: String,
        override val title: String,
        val previewIcons: List<Drawable?>,
        val items: List<FolderChildItem>,
    ) : WorkspaceSurfaceItem

    data class Widget(
        override val id: String,
        override val title: String,
        val appWidgetId: Int,
        val spanX: Int,
        val spanY: Int,
        val providerPackageName: String,
        val providerClassName: String,
    ) : WorkspaceSurfaceItem
}

data class FolderSheetUiState(
    val folderId: String,
    val title: String,
    val anchor: LauncherSurfaceAnchor?,
    val pages: List<FolderPageUiState>,
)

data class LauncherSurfaceAnchor(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

sealed interface LauncherActionSheetPresentation {
    data object BottomSheet : LauncherActionSheetPresentation

    data class Anchored(
        val anchor: LauncherSurfaceAnchor,
    ) : LauncherActionSheetPresentation
}

data class LauncherActionSheetAction(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val destructive: Boolean = false,
)

data class LauncherActionSheetSection(
    val title: String? = null,
    val actions: List<LauncherActionSheetAction>,
)

data class LauncherActionSheetState(
    val title: String,
    val subtitle: String? = null,
    val icon: Drawable? = null,
    val sections: List<LauncherActionSheetSection>,
    val presentation: LauncherActionSheetPresentation = LauncherActionSheetPresentation.BottomSheet,
)

data class EditSheetActionUiState(
    val id: String,
    val title: String,
    val subtitle: String? = null,
)

data class EditSheetUiState(
    val title: String,
    val subtitle: String? = null,
    val actions: List<EditSheetActionUiState>,
)

enum class AppLibraryMode {
    OVERVIEW,
    SEARCH,
}

sealed interface AppLibraryOverviewBlockUiState {
    val id: String
    val title: String

    data class Suggested(
        override val id: String = "suggested",
        override val title: String,
        val apps: List<DrawerAppItem>,
    ) : AppLibraryOverviewBlockUiState

    data class RecentlyAdded(
        override val id: String = "recently_added",
        override val title: String,
        val apps: List<DrawerAppItem>,
    ) : AppLibraryOverviewBlockUiState

    data class Category(
        override val id: String,
        override val title: String,
        val previewApps: List<DrawerAppItem>,
    ) : AppLibraryOverviewBlockUiState
}

sealed interface AppLibrarySearchRowUiState {
    val stableId: String

    data class Header(
        override val stableId: String,
        val title: String,
    ) : AppLibrarySearchRowUiState

    data class App(
        override val stableId: String,
        val item: DrawerAppItem,
    ) : AppLibrarySearchRowUiState
}

data class AppLibraryEmptyStateUiState(
    val title: String,
    val subtitle: String,
)

data class AppLibraryUiState(
    val mode: AppLibraryMode = AppLibraryMode.OVERVIEW,
    val query: String = "",
    val overviewBlocks: List<AppLibraryOverviewBlockUiState> = emptyList(),
    val searchRows: List<AppLibrarySearchRowUiState> = emptyList(),
    val emptyState: AppLibraryEmptyStateUiState? = null,
)

data class SpotlightUiState(
    val query: String = "",
    val suggestedApps: List<DrawerAppItem> = emptyList(),
    val searchRows: List<AppLibrarySearchRowUiState> = emptyList(),
    val emptyState: AppLibraryEmptyStateUiState? = null,
)

data class HomePageUiState(
    val page: Int,
    val items: List<WorkspaceSurfaceItem> = emptyList(),
)

enum class LauncherSurfaceMode {
    HOME,
    APP_LIBRARY_OVERVIEW,
    APP_LIBRARY_SEARCH,
    SPOTLIGHT,
}

data class LauncherUiState(
    val homePages: List<HomePageUiState> = emptyList(),
    val currentHomePageIndex: Int = 0,
    val surfaceMode: LauncherSurfaceMode = LauncherSurfaceMode.HOME,
    val dockItems: List<WorkspaceSurfaceItem> = emptyList(),
    val appLibrary: AppLibraryUiState = AppLibraryUiState(),
    val spotlight: SpotlightUiState = SpotlightUiState(),
    val drawerOpen: Boolean = false,
    val openFolder: FolderSheetUiState? = null,
    val actionSheet: LauncherActionSheetState? = null,
    val editSheet: EditSheetUiState? = null,
    val emptyWorkspaceHintVisible: Boolean = false,
) {
    val pageItems: List<WorkspaceSurfaceItem>
        get() = homePages.getOrNull(currentHomePageIndex)?.items.orEmpty()
}

enum class LauncherContainerKind {
    PAGE,
    DOCK,
    FOLDER,
}

data class LauncherDropTarget(
    val container: LauncherContainerKind,
    val position: Int,
    val containerId: String? = null,
    val page: Int = 0,
)

interface LauncherRootCallbacks {
    fun onOpenSpotlight()
    fun onOpenAppLibrary()
    fun onEnterAppLibrarySearch()
    fun onCloseAppLibrary()
    fun onCloseSpotlight()
    fun onAppLibraryQueryChanged(query: String)
    fun onSpotlightQueryChanged(query: String)
    fun onNavigateHome(pageIndex: Int)
    fun onLaunchApp(componentName: ComponentName)
    fun onRequestWorkspaceMenu()
    fun onRequestDrawerItemActions(app: DrawerAppItem, anchor: LauncherSurfaceAnchor)
    fun onRequestWorkspaceItemActions(itemId: String, anchor: LauncherSurfaceAnchor)
    fun onOpenFolder(folderId: String, anchor: LauncherSurfaceAnchor)
    fun onCloseFolder()
    fun onConfirmRenameFolder(folderId: String, title: String)
    fun onDismissEditSheet()
    fun onSelectEditSheetAction(actionId: String)
    fun onDismissActionSheet()
    fun onSelectActionSheetAction(actionId: String)
    fun onDropDrawerApp(app: DrawerAppItem, target: LauncherDropTarget, targetItemId: String?)
    fun onDropWorkspaceItem(itemId: String, target: LauncherDropTarget, targetItemId: String?)
    fun onRemoveWorkspaceItem(itemId: String)
}
