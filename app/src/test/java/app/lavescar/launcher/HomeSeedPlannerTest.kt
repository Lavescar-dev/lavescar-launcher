package app.lavescar.launcher

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HomeSeedPlannerTest {

    @Test
    fun createPlan_prefersSuggestedDockAppsWithoutDuplicates() {
        val plan = HomeSeedPlanner.createPlan(
            apps = listOf(
                app("pkg.phone", "Dialer", "Phone"),
                app("pkg.browser", "Browser", "Browser"),
                app("pkg.sms", "Messages", "Messages"),
                app("pkg.camera", "Camera", "Camera"),
                app("pkg.mail", "Mail", "Mail"),
            ),
            ownPackageName = "app.lavescar.launcher.debug",
            preferredDockComponentKeys = listOf(
                "pkg.phone/Dialer",
                "pkg.browser/Browser",
                "pkg.sms/Messages",
                "pkg.camera/Camera",
            ),
        )

        assertThat(plan.dockItems.map { it.componentKey }).containsExactly(
            "pkg.phone/Dialer",
            "pkg.browser/Browser",
            "pkg.sms/Messages",
            "pkg.camera/Camera",
        ).inOrder()
        assertThat(plan.pageItems.map { it.componentKey }).containsExactly("pkg.mail/Mail")
    }

    @Test
    fun createPlan_excludesLauncherPackageAndFallsBackAlphabetically() {
        val plan = HomeSeedPlanner.createPlan(
            apps = listOf(
                app("app.lavescar.launcher.debug", "Launcher", "LavescarOS"),
                app("pkg.zeta", "Zeta", "Zeta"),
                app("pkg.alpha", "Alpha", "Alpha"),
                app("pkg.beta", "Beta", "Beta"),
                app("pkg.gamma", "Gamma", "Gamma"),
                app("pkg.delta", "Delta", "Delta"),
            ),
            ownPackageName = "app.lavescar.launcher.debug",
            preferredDockComponentKeys = emptyList(),
        )

        assertThat(plan.dockItems.map { it.label }).containsExactly("Alpha", "Beta", "Delta", "Gamma").inOrder()
        assertThat(plan.pageItems.map { it.label }).containsExactly("Zeta")
    }

    @Test
    fun shouldSeed_onlyWhenWorkspaceIsEmptyAndNotCompleted() {
        assertThat(HomeSeedPlanner.shouldSeed(false, false, 5)).isTrue()
        assertThat(HomeSeedPlanner.shouldSeed(true, false, 5)).isFalse()
        assertThat(HomeSeedPlanner.shouldSeed(false, true, 5)).isFalse()
        assertThat(HomeSeedPlanner.shouldSeed(false, false, 0)).isFalse()
    }

    private fun app(packageName: String, className: String, label: String): HomeSeedCandidate {
        return HomeSeedCandidate(
            packageName = packageName,
            className = className,
            label = label,
        )
    }
}
