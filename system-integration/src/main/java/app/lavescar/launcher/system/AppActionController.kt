package app.lavescar.launcher.system

import android.content.ComponentName

data class LauncherShortcutAction(
    val id: String,
    val title: String,
    val subtitle: String?,
)

interface AppActionController {
    suspend fun loadShortcutActions(componentName: ComponentName): List<LauncherShortcutAction>

    fun launchShortcut(componentName: ComponentName, shortcutId: String): Boolean

    fun openAppInfo(packageName: String): Boolean

    fun requestUninstall(packageName: String): Boolean

    fun canUninstall(packageName: String): Boolean
}
