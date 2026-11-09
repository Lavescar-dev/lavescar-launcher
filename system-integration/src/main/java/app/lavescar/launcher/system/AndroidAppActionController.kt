package app.lavescar.launcher.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidAppActionController(
    context: Context,
) : AppActionController {

    private val appContext = context.applicationContext
    private val launcherApps = appContext.getSystemService(LauncherApps::class.java)
    private val packageManager = appContext.packageManager

    override suspend fun loadShortcutActions(componentName: ComponentName): List<LauncherShortcutAction> {
        return withContext(Dispatchers.Default) {
            runCatching {
                val query = LauncherApps.ShortcutQuery()
                    .setPackage(componentName.packageName)
                    .setActivity(componentName)
                    .setQueryFlags(
                        LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                            LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                            LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
                    )
                launcherApps.getShortcuts(query, Process.myUserHandle())
                    .orEmpty()
                    .distinctBy { it.id }
                    .sortedBy { it.shortLabel?.toString() ?: it.longLabel?.toString() ?: it.id }
                    .map { shortcut ->
                        LauncherShortcutAction(
                            id = shortcut.id,
                            title = shortcut.shortLabel?.toString() ?: shortcut.longLabel?.toString() ?: shortcut.id,
                            subtitle = shortcut.longLabel?.toString(),
                        )
                    }
            }.getOrDefault(emptyList())
        }
    }

    override fun launchShortcut(componentName: ComponentName, shortcutId: String): Boolean {
        return runCatching {
            launcherApps.startShortcut(
                componentName.packageName,
                shortcutId,
                Rect(),
                Bundle.EMPTY,
                Process.myUserHandle(),
            )
            true
        }.getOrDefault(false)
    }

    override fun openAppInfo(packageName: String): Boolean {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return startActivity(intent)
    }

    override fun requestUninstall(packageName: String): Boolean {
        val intent = Intent(
            Intent.ACTION_DELETE,
            Uri.fromParts("package", packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return startActivity(intent)
    }

    override fun canUninstall(packageName: String): Boolean {
        val applicationInfo = runCatching {
            packageManager.getApplicationInfo(packageName, 0)
        }.getOrNull() ?: return false
        val isSystemApp = applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
        val isUpdatedSystemApp = applicationInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
        return !isSystemApp || isUpdatedSystemApp
    }

    private fun startActivity(intent: Intent): Boolean {
        return runCatching {
            appContext.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
