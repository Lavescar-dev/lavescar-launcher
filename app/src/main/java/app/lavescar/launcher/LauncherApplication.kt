package app.lavescar.launcher

import android.app.Application

class LauncherApplication : Application() {
    val appGraph: LauncherGraph by lazy { LauncherGraph(this) }
}

