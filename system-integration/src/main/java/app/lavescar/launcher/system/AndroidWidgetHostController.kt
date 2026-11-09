package app.lavescar.launcher.system

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import kotlin.math.ceil

class AndroidWidgetHostController(
    context: Context,
) : WidgetHostController {

    private val appContext = context.applicationContext
    private val appWidgetManager = AppWidgetManager.getInstance(appContext)
    private val appWidgetHost = AppWidgetHost(appContext, APP_WIDGET_HOST_ID)

    override fun startListening() {
        runCatching { appWidgetHost.startListening() }
    }

    override fun stopListening() {
        runCatching { appWidgetHost.stopListening() }
    }

    override fun allocateAppWidgetId(): Int = appWidgetHost.allocateAppWidgetId()

    override fun deleteAppWidgetId(appWidgetId: Int) {
        appWidgetHost.deleteAppWidgetId(appWidgetId)
    }

    override fun createPickWidgetIntent(appWidgetId: Int): Intent {
        return Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
    }

    override fun createBindWidgetIntent(appWidgetId: Int, provider: ComponentName): Intent {
        return Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, Bundle.EMPTY)
        }
    }

    override fun bindWidgetIfAllowed(appWidgetId: Int, provider: ComponentName): Boolean {
        return runCatching {
            appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, provider)
        }.getOrDefault(false)
    }

    override fun createConfigurationIntentOrNull(appWidgetId: Int): Intent? {
        val info = appWidgetManager.getAppWidgetInfo(appWidgetId) ?: return null
        val configureComponent = info.configure ?: return null
        return Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
            component = configureComponent
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
    }

    override fun extractSelectedProvider(data: Intent?): ComponentName? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            data?.getParcelableExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, ComponentName::class.java)
        } else {
            @Suppress("DEPRECATION")
            data?.getParcelableExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER) as? ComponentName
        }
    }

    override fun isBound(appWidgetId: Int): Boolean {
        return appWidgetManager.getAppWidgetInfo(appWidgetId) != null
    }

    override fun buildPlacement(appWidgetId: Int): HostedWidgetPlacement? {
        val info = appWidgetManager.getAppWidgetInfo(appWidgetId) ?: return null
        return HostedWidgetPlacement(
            appWidgetId = appWidgetId,
            provider = info.provider,
            label = info.loadLabel(appContext.packageManager).toString().ifBlank {
                info.provider.shortClassName.removePrefix(".")
            },
            spanX = DEFAULT_WIDGET_SPAN_X,
            spanY = estimateSpanY(info),
        )
    }

    override fun createHostView(context: Context, appWidgetId: Int): View? {
        val info = appWidgetManager.getAppWidgetInfo(appWidgetId) ?: return null
        return runCatching {
            appWidgetHost.createView(context, appWidgetId, info).apply {
                setAppWidget(appWidgetId, info)
            }
        }.getOrNull()
    }

    private fun estimateSpanY(info: android.appwidget.AppWidgetProviderInfo): Int {
        val density = appContext.resources.displayMetrics.density
        val minHeightDp = info.minHeight / density
        return ceil((minHeightDp / 104f).coerceAtLeast(1f)).toInt().coerceIn(1, 3)
    }

    private companion object {
        const val APP_WIDGET_HOST_ID = 3107
        const val DEFAULT_WIDGET_SPAN_X = 4
    }
}
