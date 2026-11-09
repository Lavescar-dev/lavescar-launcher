package app.lavescar.launcher.system

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LauncherAppsCatalogRepository(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AppCatalogRepository {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val _apps = MutableStateFlow(emptyList<LauncherAppInfo>())
    private val hasLoadedOnce = AtomicBoolean(false)
    private val loadInFlight = AtomicBoolean(false)

    override val apps: StateFlow<List<LauncherAppInfo>> = _apps.asStateFlow()

    private val packageCallback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) = reloadCatalog()
        override fun onPackageAdded(packageName: String?, user: UserHandle?) = reloadCatalog()
        override fun onPackageChanged(packageName: String?, user: UserHandle?) = reloadCatalog()
        override fun onPackagesAvailable(
            packageNames: Array<out String>?,
            user: UserHandle?,
            replacing: Boolean,
        ) = reloadCatalog()

        override fun onPackagesUnavailable(
            packageNames: Array<out String>?,
            user: UserHandle?,
            replacing: Boolean,
        ) = reloadCatalog()
    }

    init {
        runCatching {
            launcherApps?.registerCallback(packageCallback, Handler(Looper.getMainLooper()))
        }
        reloadCatalog()
    }

    override fun refresh() {
        if (!hasLoadedOnce.get() || _apps.value.isEmpty()) {
            reloadCatalog()
        }
    }

    private fun reloadCatalog() {
        if (!loadInFlight.compareAndSet(false, true)) {
            return
        }
        scope.launch {
            try {
                val discovered = launcherApps
                    ?.getActivityList(null, Process.myUserHandle())
                    .orEmpty()
                    .map { info -> info.toAppInfo() }
                    .sortedWith(
                        compareBy<LauncherAppInfo> { it.label.lowercase(Locale.getDefault()) }
                            .thenBy { it.componentKey },
                    )
                _apps.value = discovered
                hasLoadedOnce.set(true)
            } finally {
                loadInFlight.set(false)
            }
        }
    }

    private fun LauncherActivityInfo.toAppInfo(): LauncherAppInfo {
        val resolvedLabel = label?.toString().orEmpty().ifBlank { componentName.shortClassName.removePrefix(".") }
        val normalizedLabel = resolvedLabel.lowercase(Locale.getDefault())
        val normalizedPkg = componentName.packageName.lowercase(Locale.getDefault())
        return LauncherAppInfo(
            componentName = componentName,
            label = resolvedLabel,
            user = user,
            icon = EMPTY_ICON,
            category = LauncherAppCategoryResolver.resolve(
                packageName = componentName.packageName,
                appCategory = null,
            ),
            searchKey = "$normalizedLabel $normalizedPkg",
            installedAtMillis = 0L,
        )
    }

    private companion object {
        val EMPTY_ICON = ColorDrawable(Color.BLACK)
    }
}
