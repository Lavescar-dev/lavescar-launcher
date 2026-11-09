package app.lavescar.launcher.data

import androidx.room.withTransaction
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomWorkspaceRepository(
    private val database: LauncherDatabase,
) : WorkspaceRepository {

    private val dao = database.workspaceDao()

    override val workspace: Flow<WorkspaceModel> = dao.observeAll().map(::toWorkspaceModel)

    override suspend fun addShortcut(
        app: AppShortcutReference,
        target: WorkspaceDropTarget,
        targetItemId: String?,
    ): Boolean = database.withTransaction {
        val items = dao.getAll().toMutableList()
        if (!validateCapacity(items, target, targetItemId)) {
            return@withTransaction false
        }

        val created = WorkspaceItemEntity(
            id = UUID.randomUUID().toString(),
            itemType = TYPE_APP,
            container = target.container.name,
            containerId = target.containerId,
            page = target.page,
            position = target.position.coerceAtLeast(0),
            title = app.title,
            packageName = app.packageName,
            className = app.className,
            appWidgetId = null,
            spanX = 1,
            spanY = 1,
        )

        val applied = applyIncomingDrop(
            items = items,
            incoming = created,
            target = target,
            targetItemId = targetItemId,
        )
        if (!applied) {
            return@withTransaction false
        }

        persistSnapshot(items)
        true
    }

    override suspend fun addWidget(
        widget: WidgetReference,
        target: WorkspaceDropTarget,
    ): Boolean = database.withTransaction {
        if (target.container != WorkspaceContainerType.PAGE) {
            return@withTransaction false
        }

        val items = dao.getAll().toMutableList()
        val created = WorkspaceItemEntity(
            id = UUID.randomUUID().toString(),
            itemType = TYPE_WIDGET,
            container = target.container.name,
            containerId = null,
            page = target.page,
            position = target.position.coerceAtLeast(0),
            title = widget.title,
            packageName = widget.providerPackageName,
            className = widget.providerClassName,
            appWidgetId = widget.appWidgetId,
            spanX = widget.spanX.coerceIn(1, 4),
            spanY = widget.spanY.coerceIn(1, 4),
        )

        insertIntoContainer(items, created, target)
        persistSnapshot(items)
        true
    }

    override suspend fun moveItem(
        itemId: String,
        target: WorkspaceDropTarget,
        targetItemId: String?,
    ): Boolean = database.withTransaction {
        val items = dao.getAll().toMutableList()
        val existingIndex = items.indexOfFirst { it.id == itemId }
        if (existingIndex == -1) {
            return@withTransaction false
        }

        val moving = items[existingIndex]
        if (targetItemId == itemId) {
            return@withTransaction true
        }

        if (moving.itemType == TYPE_FOLDER && targetItemId != null) {
            return@withTransaction false
        }

        if (moving.itemType == TYPE_WIDGET && (target.container != WorkspaceContainerType.PAGE || targetItemId != null)) {
            return@withTransaction false
        }

        items.removeAt(existingIndex)
        normalizeContainer(items, moving.container, moving.containerId, moving.page)

        if (!validateCapacity(items, target, targetItemId)) {
            items.add(existingIndex, moving)
            normalizeContainer(items, moving.container, moving.containerId, moving.page)
            persistSnapshot(items)
            return@withTransaction false
        }

        val applied = applyIncomingDrop(
            items = items,
            incoming = moving,
            target = target,
            targetItemId = targetItemId,
        )
        if (!applied) {
            items.add(existingIndex, moving)
            normalizeContainer(items, moving.container, moving.containerId, moving.page)
            persistSnapshot(items)
            return@withTransaction false
        }

        cleanupFolders(items)
        persistSnapshot(items)
        true
    }

    override suspend fun removeItem(itemId: String) {
        database.withTransaction {
            val items = dao.getAll().toMutableList()
            val target = items.firstOrNull { it.id == itemId } ?: return@withTransaction
            items.removeAll { it.id == itemId || it.containerId == itemId }
            normalizeContainer(items, target.container, target.containerId, target.page)
            cleanupFolders(items)
            persistSnapshot(items)
        }
    }

    override suspend fun renameFolder(folderId: String, title: String) {
        database.withTransaction {
            val items = dao.getAll().toMutableList()
            val folderIndex = items.indexOfFirst { it.id == folderId && it.itemType == TYPE_FOLDER }
            if (folderIndex == -1) {
                return@withTransaction
            }
            items[folderIndex] = items[folderIndex].copy(title = title.ifBlank { DEFAULT_FOLDER_TITLE })
            persistSnapshot(items)
        }
    }

    override suspend fun clearWorkspace() {
        database.withTransaction {
            dao.clearAll()
        }
    }

    private fun applyIncomingDrop(
        items: MutableList<WorkspaceItemEntity>,
        incoming: WorkspaceItemEntity,
        target: WorkspaceDropTarget,
        targetItemId: String?,
    ): Boolean {
        val targetItem = targetItemId?.let { id -> items.firstOrNull { it.id == id } }

        if (targetItem != null) {
            return when (targetItem.itemType) {
                TYPE_FOLDER -> {
                    if (incoming.itemType == TYPE_APP) {
                        addToFolder(items, targetItem.id, incoming)
                        true
                    } else {
                        false
                    }
                }

                TYPE_APP -> {
                    if (incoming.itemType != TYPE_APP) {
                        false
                    } else {
                        createFolder(items, incoming, targetItem)
                        true
                    }
                }

                else -> false
            }
        }

        val normalizedIncoming = incoming.copy(
            container = target.container.name,
            containerId = target.containerId,
            page = target.page,
        )
        insertIntoContainer(items, normalizedIncoming, target)
        return true
    }

    private fun insertIntoContainer(
        items: MutableList<WorkspaceItemEntity>,
        incoming: WorkspaceItemEntity,
        target: WorkspaceDropTarget,
    ) {
        val containerItems = items
            .filter {
                it.container == target.container.name &&
                    it.containerId == target.containerId &&
                    it.page == target.page
            }
            .sortedBy { it.position }

        val insertPosition = target.position.coerceIn(0, containerItems.size)
        containerItems.forEachIndexed { index, entity ->
            val newPosition = if (index >= insertPosition) index + 1 else index
            replace(items, entity.copy(position = newPosition))
        }
        items.add(incoming.copy(position = insertPosition))
        normalizeContainer(items, target.container.name, target.containerId, target.page)
    }

    private fun addToFolder(
        items: MutableList<WorkspaceItemEntity>,
        folderId: String,
        incoming: WorkspaceItemEntity,
    ) {
        val children = items
            .filter { it.container == WorkspaceContainerType.FOLDER.name && it.containerId == folderId }
            .sortedBy { it.position }
        items.add(
            incoming.copy(
                container = WorkspaceContainerType.FOLDER.name,
                containerId = folderId,
                page = 0,
                position = children.size,
            ),
        )
        normalizeContainer(items, WorkspaceContainerType.FOLDER.name, folderId, 0)
    }

    private fun createFolder(
        items: MutableList<WorkspaceItemEntity>,
        incoming: WorkspaceItemEntity,
        targetItem: WorkspaceItemEntity,
    ) {
        val folderId = UUID.randomUUID().toString()
        val folderTitle = DEFAULT_FOLDER_TITLE
        val targetContainer = targetItem.container
        val targetContainerId = targetItem.containerId
        val targetPage = targetItem.page
        val targetPosition = targetItem.position

        items.removeAll { it.id == targetItem.id }
        normalizeContainer(items, targetContainer, targetContainerId, targetPage)
        items.add(
            WorkspaceItemEntity(
                id = folderId,
                itemType = TYPE_FOLDER,
                container = targetContainer,
                containerId = targetContainerId,
                page = targetPage,
                position = targetPosition,
                title = folderTitle,
                packageName = null,
                className = null,
                appWidgetId = null,
                spanX = 1,
                spanY = 1,
            ),
        )
        items.add(
            targetItem.copy(
                container = WorkspaceContainerType.FOLDER.name,
                containerId = folderId,
                page = 0,
                position = 0,
            ),
        )
        items.add(
            incoming.copy(
                container = WorkspaceContainerType.FOLDER.name,
                containerId = folderId,
                page = 0,
                position = 1,
            ),
        )
        normalizeContainer(items, targetContainer, targetContainerId, targetPage)
        normalizeContainer(items, WorkspaceContainerType.FOLDER.name, folderId, 0)
    }

    private fun cleanupFolders(items: MutableList<WorkspaceItemEntity>) {
        val folders = items.filter { it.itemType == TYPE_FOLDER }
        folders.forEach { folder ->
            val children = items
                .filter { it.container == WorkspaceContainerType.FOLDER.name && it.containerId == folder.id }
                .sortedBy { it.position }

            when (children.size) {
                0 -> {
                    items.removeAll { it.id == folder.id }
                    normalizeContainer(items, folder.container, folder.containerId, folder.page)
                }

                1 -> {
                    val onlyChild = children.single()
                    items.removeAll { it.id == folder.id || it.id == onlyChild.id }
                    items.add(
                        onlyChild.copy(
                            container = folder.container,
                            containerId = folder.containerId,
                            page = folder.page,
                            position = folder.position,
                        ),
                    )
                    normalizeContainer(items, folder.container, folder.containerId, folder.page)
                }

                else -> normalizeContainer(items, WorkspaceContainerType.FOLDER.name, folder.id, 0)
            }
        }
    }

    private fun validateCapacity(
        items: List<WorkspaceItemEntity>,
        target: WorkspaceDropTarget,
        targetItemId: String?,
    ): Boolean {
        if (targetItemId != null) {
            return true
        }
        val current = items.count {
            it.container == target.container.name &&
                it.containerId == target.containerId &&
                it.page == target.page
        }
        val limit = when (target.container) {
            WorkspaceContainerType.PAGE -> PAGE_CAPACITY
            WorkspaceContainerType.DOCK -> DOCK_CAPACITY
            WorkspaceContainerType.FOLDER -> FOLDER_CAPACITY
        }
        return current < limit
    }

    private fun replace(items: MutableList<WorkspaceItemEntity>, entity: WorkspaceItemEntity) {
        val index = items.indexOfFirst { it.id == entity.id }
        if (index >= 0) {
            items[index] = entity
        }
    }

    private fun normalizeContainer(
        items: MutableList<WorkspaceItemEntity>,
        container: String,
        containerId: String?,
        page: Int,
    ) {
        items
            .filter { it.container == container && it.containerId == containerId && it.page == page }
            .sortedBy { it.position }
            .forEachIndexed { index, entity ->
                replace(items, entity.copy(position = index))
            }
    }

    private fun toWorkspaceModel(items: List<WorkspaceItemEntity>): WorkspaceModel {
        val pages = items
            .filter { it.container == WorkspaceContainerType.PAGE.name }
            .groupBy { it.page }
            .toSortedMap()
            .map { (page, entities) ->
                WorkspacePageModel(
                    page = page,
                    items = entities
                        .sortedBy { it.position }
                        .mapNotNull { entity -> toWorkspaceItem(entity, allItems = items) },
                )
            }
        val dockItems = items
            .filter { it.container == WorkspaceContainerType.DOCK.name }
            .sortedBy { it.position }
            .mapNotNull { entity -> toWorkspaceItem(entity, allItems = items) }
        return WorkspaceModel(pages = pages, dockItems = dockItems)
    }

    private fun toWorkspaceItem(
        entity: WorkspaceItemEntity,
        allItems: List<WorkspaceItemEntity>,
    ): WorkspaceItemModel? {
        return when (entity.itemType) {
            TYPE_APP -> WorkspaceItemModel.AppShortcut(
                id = entity.id,
                title = entity.title,
                position = entity.position,
                packageName = entity.packageName.orEmpty(),
                className = entity.className.orEmpty(),
            )

            TYPE_FOLDER -> WorkspaceItemModel.Folder(
                id = entity.id,
                title = entity.title,
                position = entity.position,
                items = allItems
                    .filter {
                        it.container == WorkspaceContainerType.FOLDER.name &&
                            it.containerId == entity.id &&
                            it.itemType == TYPE_APP
                    }
                    .sortedBy { it.position }
                    .map { child ->
                        WorkspaceItemModel.AppShortcut(
                            id = child.id,
                            title = child.title,
                            position = child.position,
                            packageName = child.packageName.orEmpty(),
                            className = child.className.orEmpty(),
                        )
                    },
            )

            TYPE_WIDGET -> WorkspaceItemModel.Widget(
                id = entity.id,
                title = entity.title,
                position = entity.position,
                appWidgetId = entity.appWidgetId ?: INVALID_APP_WIDGET_ID,
                providerPackageName = entity.packageName.orEmpty(),
                providerClassName = entity.className.orEmpty(),
                spanX = entity.spanX.coerceAtLeast(1),
                spanY = entity.spanY.coerceAtLeast(1),
            )

            else -> null
        }
    }

    private suspend fun persistSnapshot(items: List<WorkspaceItemEntity>) {
        dao.clearAll()
        if (items.isNotEmpty()) {
            dao.upsertAll(items)
        }
    }

    private companion object {
        const val TYPE_APP = "APP"
        const val TYPE_FOLDER = "FOLDER"
        const val TYPE_WIDGET = "WIDGET"
        const val PAGE_CAPACITY = 20
        const val DOCK_CAPACITY = 4
        const val FOLDER_CAPACITY = 16
        const val DEFAULT_FOLDER_TITLE = "Folder"
        const val INVALID_APP_WIDGET_ID = -1
    }
}
