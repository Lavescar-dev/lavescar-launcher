package app.lavescar.launcher.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherBaselineProfile {

    @get:Rule
    val baselineRule = BaselineProfileRule()

    @Test
    fun generate() {
        baselineRule.collect(
            packageName = TARGET_PACKAGE,
        ) {
            seedMinimalHome()
            launchLauncherAndWait()
            searchHomeForFirstPinnedAppAndWait()
            device.pressBack()
            openAppLibraryAndWait()
            device.pressBack()
        }
    }
}
