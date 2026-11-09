package app.lavescar.launcher.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workspace_items")
data class WorkspaceItemEntity(
    @PrimaryKey val id: String,
    val itemType: String,
    val container: String,
    val containerId: String?,
    val page: Int,
    val position: Int,
    val title: String,
    val packageName: String?,
    val className: String?,
    val appWidgetId: Int?,
    val spanX: Int,
    val spanY: Int,
)
