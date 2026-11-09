package app.lavescar.launcher.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore

class AndroidHomeSeedDefaultsController(
    context: Context,
) : HomeSeedDefaultsController {

    private val packageManager = context.applicationContext.packageManager

    override fun preferredDockComponents(): List<ComponentName> {
        return listOfNotNull(
            resolve(Intent(Intent.ACTION_DIAL)),
            resolve(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.android.com"))),
            resolve(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING)),
            resolve(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)),
        ).distinct()
    }

    private fun resolve(intent: Intent): ComponentName? {
        val resolved = packageManager.resolveActivity(intent, 0)?.activityInfo ?: return null
        return ComponentName(resolved.packageName, resolved.name)
    }
}
