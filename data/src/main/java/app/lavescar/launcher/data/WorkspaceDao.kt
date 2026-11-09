package app.lavescar.launcher.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkspaceDao {
    @Query("SELECT * FROM workspace_items ORDER BY page ASC, container ASC, position ASC")
    fun observeAll(): Flow<List<WorkspaceItemEntity>>

    @Query("SELECT * FROM workspace_items")
    suspend fun getAll(): List<WorkspaceItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<WorkspaceItemEntity>)

    @Query("DELETE FROM workspace_items WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM workspace_items")
    suspend fun clearAll()
}
