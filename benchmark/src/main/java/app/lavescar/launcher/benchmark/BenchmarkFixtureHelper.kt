package app.lavescar.launcher.benchmark

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal fun MacrobenchmarkScope.seedMinimalHome() {
    sendFixtureBroadcast(FIXTURE_POPULATED)
}

internal fun MacrobenchmarkScope.seedMinimalEmptyHome() {
    sendFixtureBroadcast(FIXTURE_EMPTY)
}

internal fun MacrobenchmarkScope.launchLauncherAndWait() {
    pressHome()
    startActivityAndWait()
    device.wait(Until.hasObject(By.desc(HOME_SEARCH_FIELD_DESCRIPTION)), 2_500)
}

internal fun MacrobenchmarkScope.openAppLibraryAndWait() {
    device.swipe(
        device.displayWidth / 2,
        (device.displayHeight * 0.78f).toInt(),
        device.displayWidth / 2,
        (device.displayHeight * 0.26f).toInt(),
        20,
    )
    device.wait(Until.hasObject(By.desc(LIBRARY_SEARCH_FIELD_DESCRIPTION)), 2_500)
}

internal fun MacrobenchmarkScope.searchHomeForFirstPinnedAppAndWait(): String {
    val firstRow = requireAppRows().firstOrNull()
        ?: error("No app rows were visible on the minimal home screen")
    val label = firstRow.text?.trim().orEmpty().ifBlank {
        firstRow.contentDescription
            ?.removePrefix("Launcher app row ")
            ?.trim()
            .orEmpty()
    }
    require(label.isNotBlank()) { "Could not resolve the first pinned app label" }
    val searchField = requireHomeSearchField()
    searchField.click()
    searchField.text = label
    device.wait(Until.hasObject(By.text(label)), 2_500)
    return label
}

internal fun MacrobenchmarkScope.launchFirstVisibleAppRow() {
    requireAppRows().firstOrNull()?.click()
        ?: error("No app rows were visible to launch")
}

private fun MacrobenchmarkScope.requireHomeSearchField(): UiObject2 {
    device.wait(Until.hasObject(By.desc(HOME_SEARCH_FIELD_DESCRIPTION)), 2_500)
    return device.findObject(By.desc(HOME_SEARCH_FIELD_DESCRIPTION))
        ?: error("Minimal home search field was not found")
}

private fun MacrobenchmarkScope.requireAppRows(): List<UiObject2> {
    device.wait(Until.hasObject(By.descContains(APP_ROW_DESCRIPTION_PREFIX)), 2_500)
    return device.findObjects(By.descContains(APP_ROW_DESCRIPTION_PREFIX))
}

private fun MacrobenchmarkScope.sendFixtureBroadcast(fixture: String) {
    val context = InstrumentationRegistry.getInstrumentation().context
    val latch = CountDownLatch(1)
    val intent = Intent(ACTION_FIXTURE).apply {
        setPackage(TARGET_PACKAGE)
        putExtra(EXTRA_FIXTURE, fixture)
    }
    context.sendOrderedBroadcast(
        intent,
        RECEIVER_PERMISSION,
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                latch.countDown()
            }
        },
        null,
        0,
        null,
        null,
    )
    latch.await(5, TimeUnit.SECONDS)
    SystemClock.sleep(300)
}

internal const val TARGET_PACKAGE = "app.lavescar.launcher"
private const val HOME_SEARCH_FIELD_DESCRIPTION = "Minimal home search field"
private const val LIBRARY_SEARCH_FIELD_DESCRIPTION = "Minimal app library search field"
private const val APP_ROW_DESCRIPTION_PREFIX = "Launcher app row "

private const val ACTION_FIXTURE = "app.lavescar.launcher.action.BENCHMARK_FIXTURE"
private const val EXTRA_FIXTURE = "fixture"
private const val FIXTURE_EMPTY = "empty"
private const val FIXTURE_POPULATED = "populated"
private const val RECEIVER_PERMISSION = "app.lavescar.launcher.permission.BENCHMARK_SETUP"
