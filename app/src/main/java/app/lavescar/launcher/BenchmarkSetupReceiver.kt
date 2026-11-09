package app.lavescar.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import app.lavescar.launcher.system.LauncherAppInfo
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class BenchmarkSetupReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIXTURE) {
            return
        }
        val graph = (context.applicationContext as LauncherApplication).appGraph
        val fixture = intent.getStringExtra(EXTRA_FIXTURE).orEmpty()
        runBlocking {
            graph.appCatalogRepository.refresh()
            graph.preferencesRepository.setHomeSeedCompleted(true)
            graph.preferencesRepository.setMinimalModeEnabled(true)
            graph.preferencesRepository.setMinimalModeMigrated(true)
            when (fixture) {
                FIXTURE_EMPTY -> graph.pinnedAppsRepository.clear()
                FIXTURE_POPULATED -> seedPinnedFixture(graph, context.packageName)
            }
        }
    }

    private suspend fun seedPinnedFixture(
        graph: LauncherGraph,
        ownPackageName: String,
    ) {
        graph.pinnedAppsRepository.clear()
        val apps = waitForApps(graph)
        val seeded = MinimalPinnedSeedPlanner.createSeed(
            apps = apps.map { it.toSeedCandidate() },
            workspaceComponentKeys = emptyList(),
            preferredDefaultKeys = graph.homeSeedDefaultsController.preferredDockComponents()
                .map { it.flattenToShortString() },
            ownPackageName = ownPackageName,
        )
        graph.pinnedAppsRepository.replace(seeded)
    }

    private suspend fun waitForApps(graph: LauncherGraph): List<LauncherAppInfo> {
        graph.appCatalogRepository.refresh()
        repeat(10) {
            val current = graph.appCatalogRepository.apps.value
            if (current.isNotEmpty()) {
                return current
            }
            SystemClock.sleep(150)
        }
        return graph.appCatalogRepository.apps
            .filter { it.isNotEmpty() }
            .first()
    }

    private fun LauncherAppInfo.toSeedCandidate(): MinimalPinnedSeedCandidate {
        return MinimalPinnedSeedCandidate(
            componentKey = componentKey,
            packageName = componentName.packageName,
            label = label,
        )
    }

    companion object {
        const val ACTION_FIXTURE = "app.lavescar.launcher.action.BENCHMARK_FIXTURE"
        const val EXTRA_FIXTURE = "fixture"
        const val FIXTURE_EMPTY = "empty"
        const val FIXTURE_POPULATED = "populated"
    }
}
