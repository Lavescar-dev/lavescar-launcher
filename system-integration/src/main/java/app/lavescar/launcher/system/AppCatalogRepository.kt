package app.lavescar.launcher.system

import android.content.ComponentName
import android.graphics.drawable.Drawable
import android.os.UserHandle
import kotlinx.coroutines.flow.StateFlow

data class LauncherAppInfo(
    val componentName: ComponentName,
    val label: String,
    val user: UserHandle,
    val icon: Drawable,
    val category: LauncherAppCategory,
    val searchKey: String,
    val installedAtMillis: Long,
) {
    val componentKey: String = componentName.flattenToShortString()
}

interface AppCatalogRepository {
    val apps: StateFlow<List<LauncherAppInfo>>

    fun refresh()
}
