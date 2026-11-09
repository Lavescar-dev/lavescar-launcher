package app.lavescar.launcher.system

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.WindowManager

class AndroidWallpaperWindowController : WallpaperWindowController {
    override fun apply(activity: Activity) {
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        activity.window.decorView.setBackgroundColor(Color.BLACK)
        activity.window.setBackgroundDrawable(ColorDrawable(Color.BLACK))
    }
}
