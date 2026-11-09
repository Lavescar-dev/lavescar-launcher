package app.lavescar.launcher.system

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

class AndroidHomeRoleController(
    private val context: Context,
) : HomeRoleController {

    override fun isDefaultLauncher(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager?.isRoleAvailable(RoleManager.ROLE_HOME) == true) {
                return roleManager.isRoleHeld(RoleManager.ROLE_HOME)
            }
        }

        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(homeIntent, 0)
        return resolved?.activityInfo?.packageName == context.packageName
    }

    override fun createRequestIntentOrNull(): Intent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager?.isRoleAvailable(RoleManager.ROLE_HOME) == true) {
                return roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
            }
        }
        return null
    }

    override fun createFallbackSettingsIntent(): Intent {
        return Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

