package app.lavescar.launcher.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.launcherPrefs by preferencesDataStore(name = "launcher_prefs")

data class LauncherPreferences(
    val workspaceColumns: Int = 4,
    val workspaceRows: Int = 5,
    val hasCompletedHomeSeed: Boolean = false,
    val sampleWallpaperApplied: Boolean = false,
    val minimalModeEnabled: Boolean = true,
    val minimalModeMigrated: Boolean = false,
)

interface LauncherPreferencesRepository {
    val preferences: Flow<LauncherPreferences>

    suspend fun setGrid(columns: Int, rows: Int)

    suspend fun setHomeSeedCompleted(completed: Boolean)

    suspend fun setSampleWallpaperApplied(applied: Boolean)

    suspend fun setMinimalModeEnabled(enabled: Boolean)

    suspend fun setMinimalModeMigrated(migrated: Boolean)
}

class DataStoreLauncherPreferencesRepository(
    private val context: Context,
) : LauncherPreferencesRepository {

    override val preferences: Flow<LauncherPreferences> =
        context.launcherPrefs.data.map { prefs ->
            LauncherPreferences(
                workspaceColumns = prefs[KEY_COLUMNS] ?: 4,
                workspaceRows = prefs[KEY_ROWS] ?: 5,
                hasCompletedHomeSeed = prefs[KEY_HOME_SEED_COMPLETED] ?: false,
                sampleWallpaperApplied = prefs[KEY_SAMPLE_WALLPAPER_APPLIED] ?: false,
                minimalModeEnabled = prefs[KEY_MINIMAL_MODE_ENABLED] ?: true,
                minimalModeMigrated = prefs[KEY_MINIMAL_MODE_MIGRATED] ?: false,
            )
        }

    override suspend fun setGrid(columns: Int, rows: Int) {
        context.launcherPrefs.edit { prefs ->
            prefs[KEY_COLUMNS] = columns
            prefs[KEY_ROWS] = rows
        }
    }

    override suspend fun setHomeSeedCompleted(completed: Boolean) {
        context.launcherPrefs.edit { prefs ->
            prefs[KEY_HOME_SEED_COMPLETED] = completed
        }
    }

    override suspend fun setSampleWallpaperApplied(applied: Boolean) {
        context.launcherPrefs.edit { prefs ->
            prefs[KEY_SAMPLE_WALLPAPER_APPLIED] = applied
        }
    }

    override suspend fun setMinimalModeEnabled(enabled: Boolean) {
        context.launcherPrefs.edit { prefs ->
            prefs[KEY_MINIMAL_MODE_ENABLED] = enabled
        }
    }

    override suspend fun setMinimalModeMigrated(migrated: Boolean) {
        context.launcherPrefs.edit { prefs ->
            prefs[KEY_MINIMAL_MODE_MIGRATED] = migrated
        }
    }

    private companion object {
        val KEY_COLUMNS = intPreferencesKey("workspace_columns")
        val KEY_ROWS = intPreferencesKey("workspace_rows")
        val KEY_HOME_SEED_COMPLETED = booleanPreferencesKey("home_seed_completed")
        val KEY_SAMPLE_WALLPAPER_APPLIED = booleanPreferencesKey("sample_wallpaper_applied")
        val KEY_MINIMAL_MODE_ENABLED = booleanPreferencesKey("minimal_mode_enabled")
        val KEY_MINIMAL_MODE_MIGRATED = booleanPreferencesKey("minimal_mode_migrated")
    }
}
