package app.lavescar.launcher.system

import android.content.pm.ApplicationInfo
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LauncherAppCategoryResolverTest {

    @Test
    fun resolve_usesPlatformCategoryWhenAvailable() {
        assertThat(
            LauncherAppCategoryResolver.resolve(
                packageName = "pkg.mail",
                appCategory = ApplicationInfo.CATEGORY_PRODUCTIVITY,
            ),
        ).isEqualTo(LauncherAppCategory.PRODUCTIVITY)
    }

    @Test
    fun resolve_fallsBackToPackageHeuristics() {
        assertThat(
            LauncherAppCategoryResolver.resolve(
                packageName = "com.example.weather.tools",
                appCategory = ApplicationInfo.CATEGORY_UNDEFINED,
            ),
        ).isEqualTo(LauncherAppCategory.UTILITIES)

        assertThat(
            LauncherAppCategoryResolver.resolve(
                packageName = "com.example.telegram.client",
                appCategory = null,
            ),
        ).isEqualTo(LauncherAppCategory.SOCIAL)
    }
}
