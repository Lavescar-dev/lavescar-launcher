package app.lavescar.launcher.data

import androidx.room.migration.Migration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [WorkspaceItemEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class LauncherDatabase : RoomDatabase() {
    abstract fun workspaceDao(): WorkspaceDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE workspace_items ADD COLUMN appWidgetId INTEGER",
                )
                database.execSQL(
                    "ALTER TABLE workspace_items ADD COLUMN spanX INTEGER NOT NULL DEFAULT 1",
                )
                database.execSQL(
                    "ALTER TABLE workspace_items ADD COLUMN spanY INTEGER NOT NULL DEFAULT 1",
                )
            }
        }
    }
}
