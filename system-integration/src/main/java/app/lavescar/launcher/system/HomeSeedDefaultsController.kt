package app.lavescar.launcher.system

import android.content.ComponentName

interface HomeSeedDefaultsController {
    fun preferredDockComponents(): List<ComponentName>
}
