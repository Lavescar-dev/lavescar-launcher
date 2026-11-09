package app.lavescar.launcher.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View

data class HostedWidgetPlacement(
    val appWidgetId: Int,
    val provider: ComponentName,
    val label: String,
    val spanX: Int,
    val spanY: Int,
)

interface WidgetHostController {
    fun startListening()

    fun stopListening()

    fun allocateAppWidgetId(): Int

    fun deleteAppWidgetId(appWidgetId: Int)

    fun createPickWidgetIntent(appWidgetId: Int): Intent

    fun createBindWidgetIntent(appWidgetId: Int, provider: ComponentName): Intent

    fun bindWidgetIfAllowed(appWidgetId: Int, provider: ComponentName): Boolean

    fun createConfigurationIntentOrNull(appWidgetId: Int): Intent?

    fun extractSelectedProvider(data: Intent?): ComponentName?

    fun isBound(appWidgetId: Int): Boolean

    fun buildPlacement(appWidgetId: Int): HostedWidgetPlacement?

    fun createHostView(context: Context, appWidgetId: Int): View?
}
