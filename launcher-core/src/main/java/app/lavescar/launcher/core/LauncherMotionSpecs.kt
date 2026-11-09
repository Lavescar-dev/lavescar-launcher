package app.lavescar.launcher.core

import android.animation.TimeInterpolator
import android.view.animation.PathInterpolator

object LauncherMotionSpecs {
    val standardInterpolator: TimeInterpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
    val emphasizedInterpolator: TimeInterpolator = PathInterpolator(0.22f, 0.8f, 0.2f, 1f)
    val spotlightInterpolator: TimeInterpolator = PathInterpolator(0.16f, 1f, 0.3f, 1f)

    const val drawerDurationMillis = 300L
    const val pageTransitionDurationMillis = 340L
    const val spotlightDurationMillis = 260L
    const val folderDurationMillis = 240L
    const val editSheetDurationMillis = 230L
    const val contextMenuDurationMillis = 210L
    const val pressDurationMillis = 110L
    const val pressScale = 0.97f
    const val scrimAlpha = 0.14f
}
