package app.lavescar.launcher.system

import android.content.Intent

interface HomeRoleController {
    fun isDefaultLauncher(): Boolean

    fun createRequestIntentOrNull(): Intent?

    fun createFallbackSettingsIntent(): Intent
}

