package app.lavescar.launcher.system

import android.content.ComponentName

interface AppLauncher {
    fun launch(componentName: ComponentName): Boolean
}

