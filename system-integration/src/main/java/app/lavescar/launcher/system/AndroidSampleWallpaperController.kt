package app.lavescar.launcher.system

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.util.Log

class AndroidSampleWallpaperController(
    context: Context,
) : SampleWallpaperController {

    private val appContext = context.applicationContext
    private val wallpaperManager = WallpaperManager.getInstance(appContext)

    override fun applySampleWallpaper(): Boolean {
        return runCatching {
            val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.BLACK)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                wallpaperManager.setBitmap(
                    bitmap,
                    null,
                    true,
                    WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK,
                )
            } else {
                wallpaperManager.setBitmap(bitmap)
            }
            Log.i(TAG, "Applied pure black system and lock wallpaper")
            true
        }.getOrElse { throwable ->
            Log.e(TAG, "Failed to apply pure black wallpaper", throwable)
            false
        }
    }

    private companion object {
        const val TAG = "LavescarWallpaper"
    }
}
