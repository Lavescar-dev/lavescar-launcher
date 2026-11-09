package app.lavescar.launcher.data

enum class WorkspaceContainerType {
    PAGE,
    DOCK,
    FOLDER,
}

data class AppShortcutReference(
    val packageName: String,
    val className: String,
    val title: String,
)

data class WidgetReference(
    val appWidgetId: Int,
    val providerPackageName: String,
    val providerClassName: String,
    val title: String,
    val spanX: Int,
    val spanY: Int,
)

sealed interface WorkspaceItemModel {
    val id: String
    val title: String
    val position: Int

    data class AppShortcut(
        override val id: String,
        override val title: String,
        override val position: Int,
        val packageName: String,
        val className: String,
    ) : WorkspaceItemModel

    data class Folder(
        override val id: String,
        override val title: String,
        override val position: Int,
        val items: List<AppShortcut>,
    ) : WorkspaceItemModel

    data class Widget(
        override val id: String,
        override val title: String,
        override val position: Int,
        val appWidgetId: Int,
        val providerPackageName: String,
        val providerClassName: String,
        val spanX: Int,
        val spanY: Int,
    ) : WorkspaceItemModel
}

data class WorkspacePageModel(
    val page: Int,
    val items: List<WorkspaceItemModel>,
)

data class WorkspaceModel(
    val pages: List<WorkspacePageModel>,
    val dockItems: List<WorkspaceItemModel>,
) {
    val pageItems: List<WorkspaceItemModel>
        get() = pages.firstOrNull()?.items.orEmpty()
}

data class WorkspaceDropTarget(
    val container: WorkspaceContainerType,
    val page: Int = 0,
    val containerId: String? = null,
    val position: Int,
)
