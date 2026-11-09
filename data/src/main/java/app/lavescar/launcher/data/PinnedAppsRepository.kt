package app.lavescar.launcher.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.pinnedAppsPrefs by preferencesDataStore(name = "pinned_apps")

interface PinnedAppsRepository {
    val pinnedComponentKeys: Flow<List<String>>

    suspend fun replace(componentKeys: List<String>)

    suspend fun pin(componentKey: String)

    suspend fun unpin(componentKey: String)

    suspend fun clear()
}

class DataStorePinnedAppsRepository(
    private val context: Context,
) : PinnedAppsRepository {

    override val pinnedComponentKeys: Flow<List<String>> =
        context.pinnedAppsPrefs.data.map { prefs ->
            prefs[KEY_PINNED_COMPONENTS]
                .orEmpty()
                .split(SEPARATOR)
                .map(String::trim)
                .filter(String::isNotEmpty)
        }

    override suspend fun replace(componentKeys: List<String>) {
        val sanitized = sanitize(componentKeys)
        context.pinnedAppsPrefs.edit { prefs ->
            prefs[KEY_PINNED_COMPONENTS] = sanitized.joinToString(SEPARATOR)
        }
    }

    override suspend fun pin(componentKey: String) {
        context.pinnedAppsPrefs.edit { prefs ->
            val updated = sanitize(
                prefs[KEY_PINNED_COMPONENTS]
                    .orEmpty()
                    .split(SEPARATOR) + componentKey,
            )
            prefs[KEY_PINNED_COMPONENTS] = updated.joinToString(SEPARATOR)
        }
    }

    override suspend fun unpin(componentKey: String) {
        context.pinnedAppsPrefs.edit { prefs ->
            val updated = sanitize(
                prefs[KEY_PINNED_COMPONENTS]
                    .orEmpty()
                    .split(SEPARATOR)
                    .filterNot { it == componentKey },
            )
            prefs[KEY_PINNED_COMPONENTS] = updated.joinToString(SEPARATOR)
        }
    }

    override suspend fun clear() {
        context.pinnedAppsPrefs.edit { prefs ->
            prefs.remove(KEY_PINNED_COMPONENTS)
        }
    }

    private fun sanitize(componentKeys: List<String>): List<String> {
        return componentKeys
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
    }

    private companion object {
        const val SEPARATOR = "\u001F"
        val KEY_PINNED_COMPONENTS = stringPreferencesKey("pinned_components")
    }
}
