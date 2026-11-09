package app.lavescar.launcher

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MinimalPinnedSeedPlannerTest {

    @Test
    fun createSeed_prioritizesFirefoxAndWorkspaceApps() {
        val seeded = MinimalPinnedSeedPlanner.createSeed(
            apps = listOf(
                app("pkg.phone/MainActivity", "pkg.phone", "Phone"),
                app("pkg.whatsapp/MainActivity", "com.whatsapp", "WhatsApp"),
                app("pkg.camera/MainActivity", "pkg.camera", "Camera"),
                app("org.mozilla.firefox/App", "org.mozilla.firefox", "Firefox"),
            ),
            workspaceComponentKeys = listOf("pkg.whatsapp/MainActivity", "pkg.phone/MainActivity"),
            preferredDefaultKeys = listOf("pkg.camera/MainActivity"),
            ownPackageName = "app.lavescar.launcher",
        )

        assertThat(seeded).containsExactly(
            "org.mozilla.firefox/App",
            "pkg.whatsapp/MainActivity",
            "pkg.phone/MainActivity",
            "pkg.camera/MainActivity",
        ).inOrder()
    }

    @Test
    fun createSeed_fallsBackAlphabeticallyWithoutFirefox() {
        val seeded = MinimalPinnedSeedPlanner.createSeed(
            apps = listOf(
                app("pkg.beta/MainActivity", "pkg.beta", "Beta"),
                app("pkg.alpha/MainActivity", "pkg.alpha", "Alpha"),
                app("pkg.gamma/MainActivity", "pkg.gamma", "Gamma"),
            ),
            workspaceComponentKeys = emptyList(),
            preferredDefaultKeys = emptyList(),
            ownPackageName = "app.lavescar.launcher",
        )

        assertThat(seeded).containsExactly(
            "pkg.alpha/MainActivity",
            "pkg.beta/MainActivity",
            "pkg.gamma/MainActivity",
        ).inOrder()
    }

    private fun app(
        componentKey: String,
        packageName: String,
        label: String,
    ): MinimalPinnedSeedCandidate {
        return MinimalPinnedSeedCandidate(
            componentKey = componentKey,
            packageName = packageName,
            label = label,
        )
    }
}
