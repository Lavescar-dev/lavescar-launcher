package app.lavescar.launcher.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherBenchmarks {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartToWorkspace() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = CompilationMode.Partial(),
            iterations = 5,
            startupMode = StartupMode.COLD,
            setupBlock = {
                seedMinimalHome()
                pressHome()
            },
        ) {
            startActivityAndWait()
            device.waitForIdle()
        }
    }

    @Test
    fun appLibraryOpen() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(),
            iterations = 5,
            startupMode = StartupMode.WARM,
            setupBlock = {
                seedMinimalHome()
                launchLauncherAndWait()
            },
        ) {
            openAppLibraryAndWait()
        }
    }

    @Test
    fun homeSearchResults() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(),
            iterations = 5,
            startupMode = StartupMode.WARM,
            setupBlock = {
                seedMinimalHome()
                launchLauncherAndWait()
            },
        ) {
            searchHomeForFirstPinnedAppAndWait()
        }
    }

    @Test
    fun launchFirstSearchResult() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(),
            iterations = 5,
            startupMode = StartupMode.WARM,
            setupBlock = {
                seedMinimalHome()
                launchLauncherAndWait()
            },
        ) {
            searchHomeForFirstPinnedAppAndWait()
            launchFirstVisibleAppRow()
        }
    }
}
