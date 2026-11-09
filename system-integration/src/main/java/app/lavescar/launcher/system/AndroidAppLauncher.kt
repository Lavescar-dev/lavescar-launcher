package app.lavescar.launcher.system

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.os.Process
import android.util.Log
import androidx.annotation.VisibleForTesting

class AndroidAppLauncher(
    private val context: Context,
) : AppLauncher {

    @VisibleForTesting
    internal val launcherApps = context.getSystemService(android.content.pm.LauncherApps::class.java)

    override fun launch(componentName: ComponentName): Boolean {
        return runCatching {
            val noAnimOpts = runCatching {
                ActivityOptions.makeCustomAnimation(context, 0, 0).toBundle()
            }.getOrNull() ?: Bundle.EMPTY
            launcherApps.startMainActivity(
                componentName,
                Process.myUserHandle(),
                Rect(),
                noAnimOpts,
            )
        }.onFailure { throwable ->
            Log.w("AndroidAppLauncher", "Unable to launch $componentName", throwable)
        }.isSuccess
    }
}

