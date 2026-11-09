package app.lavescar.launcher.data

import kotlinx.coroutines.flow.Flow

interface WorkspaceRepository {
    val workspace: Flow<WorkspaceModel>

    suspend fun addShortcut(
        app: AppShortcutReference,
        target: WorkspaceDropTarget,
        targetItemId: String? = null,
    ): Boolean

    suspend fun addWidget(
        widget: WidgetReference,
        target: WorkspaceDropTarget,
    ): Boolean

    suspend fun moveItem(
        itemId: String,
        target: WorkspaceDropTarget,
        targetItemId: String? = null,
    ): Boolean

    suspend fun removeItem(itemId: String)

    suspend fun renameFolder(folderId: String, title: String)

    suspend fun clearWorkspace()
}
