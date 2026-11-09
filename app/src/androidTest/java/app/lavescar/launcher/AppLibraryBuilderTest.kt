package app.lavescar.launcher

import android.content.ComponentName
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.lavescar.launcher.core.AppLibraryOverviewBlockUiState
import app.lavescar.launcher.core.FolderChildItem
import app.lavescar.launcher.data.WorkspaceItemModel
import app.lavescar.launcher.data.WorkspaceModel
import app.lavescar.launcher.data.WorkspacePageModel
import app.lavescar.launcher.system.LauncherAppCategory
import app.lavescar.launcher.system.LauncherAppInfo
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLibraryBuilderTest {

    @Test
    fun buildSuggestedApps_prioritizesDockThenHomeWithoutDuplicates() {
        val alpha = app("pkg.alpha", "AlphaActivity", "Alpha", LauncherAppCategory.PRODUCTIVITY)
        val beta = app("pkg.beta", "BetaActivity", "Beta", LauncherAppCategory.SOCIAL)
        val gamma = app("pkg.gamma", "GammaActivity", "Gamma", LauncherAppCategory.UTILITIES)
        val delta = app("pkg.delta", "DeltaActivity", "Delta", LauncherAppCategory.PHOTOS_VIDEO)
        val epsilon = app("pkg.epsilon", "EpsilonActivity", "Epsilon", LauncherAppCategory.OTHER)

        val suggested = AppLibraryBuilder.buildSuggestedApps(
            apps = listOf(alpha, beta, gamma, delta, epsilon),
            workspace = WorkspaceModel(
                pages = listOf(
                    WorkspacePageModel(
                        page = 0,
                        items = listOf(
                            shortcut("page-gamma", gamma),
                            shortcut("page-beta", beta),
                        ),
                    ),
                ),
                dockItems = listOf(
                    shortcut("dock-beta", beta),
                    shortcut("dock-alpha", alpha),
                ),
            ),
        )

        assertThat(suggested.map { it.label }).containsExactly("Beta", "Alpha", "Gamma", "Delta").inOrder()
    }

    @Test
    fun buildOverviewBlocks_includesSpecialCollectionsAndCategoryCards() {
        val blocks = AppLibraryBuilder.buildOverviewBlocks(
            apps = listOf(
                app("pkg.mail", "MailActivity", "Mail", LauncherAppCategory.PRODUCTIVITY, installedAtMillis = 10),
                app("pkg.docs", "DocsActivity", "Docs", LauncherAppCategory.PRODUCTIVITY, installedAtMillis = 20),
                app("pkg.tasks", "TasksActivity", "Tasks", LauncherAppCategory.PRODUCTIVITY, installedAtMillis = 30),
                app("pkg.notes", "NotesActivity", "Notes", LauncherAppCategory.PRODUCTIVITY, installedAtMillis = 40),
                app("pkg.sheets", "SheetsActivity", "Sheets", LauncherAppCategory.PRODUCTIVITY, installedAtMillis = 50),
                app("pkg.chat", "ChatActivity", "Chat", LauncherAppCategory.SOCIAL, installedAtMillis = 60),
            ),
            workspace = WorkspaceModel(
                pages = emptyList(),
                dockItems = listOf(
                    shortcut("dock-mail", app("pkg.mail", "MailActivity", "Mail", LauncherAppCategory.PRODUCTIVITY)),
                ),
            ),
            suggestedTitle = "Suggested",
            recentlyAddedTitle = "Recently Added",
        )

        assertThat(blocks.take(2).map { it.title }).containsExactly("Suggested", "Recently Added").inOrder()
        val productivity = blocks.filterIsInstance<AppLibraryOverviewBlockUiState.Category>().first()
        assertThat(productivity.title).isEqualTo("Productivity")
        assertThat(productivity.previewApps.map { it.title }).containsExactly("Docs", "Mail", "Notes", "Sheets").inOrder()
    }

    @Test
    fun buildRecentlyAddedApps_excludesSuggestedDuplicates() {
        val alpha = app("pkg.alpha", "AlphaActivity", "Alpha", LauncherAppCategory.PRODUCTIVITY, installedAtMillis = 10)
        val beta = app("pkg.beta", "BetaActivity", "Beta", LauncherAppCategory.SOCIAL, installedAtMillis = 20)
        val gamma = app("pkg.gamma", "GammaActivity", "Gamma", LauncherAppCategory.UTILITIES, installedAtMillis = 30)

        val recent = AppLibraryBuilder.buildRecentlyAddedApps(
            apps = listOf(alpha, beta, gamma),
            excludedComponentKeys = setOf(beta.componentKey),
        )

        assertThat(recent.map { it.label }).containsExactly("Gamma", "Alpha").inOrder()
    }

    @Test
    fun paginateFolderItems_splitsIntoNineItemPages() {
        val pages = AppLibraryBuilder.paginateFolderItems(
            (1..10).map { index ->
                FolderChildItem(
                    id = "item-$index",
                    componentName = ComponentName("pkg.$index", "pkg.$index.MainActivity"),
                    title = "App $index",
                    icon = ColorDrawable(Color.TRANSPARENT),
                )
            },
        )

        assertThat(pages).hasSize(2)
        assertThat(pages[0].items).hasSize(9)
        assertThat(pages[1].items.single().title).isEqualTo("App 10")
    }

    @Test
    fun buildSearchRows_groupsMatchesUnderLetterHeaders() {
        val rows = AppLibraryBuilder.buildSearchRows(
            apps = listOf(
                app("pkg.akbank", "AkbankActivity", "Akbank", LauncherAppCategory.PRODUCTIVITY),
                app("pkg.amazon", "AmazonActivity", "Amazon", LauncherAppCategory.PRODUCTIVITY),
                app("pkg.chrome", "ChromeActivity", "Chrome", LauncherAppCategory.UTILITIES),
            ),
            query = "a",
        )

        assertThat(rows.map { it.stableId }).containsExactly(
            "header_A",
            "pkg.akbank/AkbankActivity",
            "pkg.amazon/AmazonActivity",
        ).inOrder()
    }

    @Test
    fun buildSearchRows_prefersPrefixMatchesOverContains() {
        val rows = AppLibraryBuilder.buildSearchRows(
            apps = listOf(
                app("pkg.calendar", "CalendarActivity", "Calendar", LauncherAppCategory.PRODUCTIVITY),
                app("pkg.localcast", "LocalcastActivity", "Localcast", LauncherAppCategory.ENTERTAINMENT),
                app("pkg.miscalc", "MiscalcActivity", "My Calc", LauncherAppCategory.UTILITIES),
            ),
            query = "cal",
        )

        assertThat(rows.filterIsInstance<app.lavescar.launcher.core.AppLibrarySearchRowUiState.App>().map { it.item.title })
            .containsExactly("Calendar", "Localcast", "My Calc")
            .inOrder()
    }

    private fun shortcut(id: String, app: LauncherAppInfo): WorkspaceItemModel.AppShortcut {
        return WorkspaceItemModel.AppShortcut(
            id = id,
            title = app.label,
            position = 0,
            packageName = app.componentName.packageName,
            className = app.componentName.className,
        )
    }

    private fun app(
        packageName: String,
        className: String,
        label: String,
        category: LauncherAppCategory,
        installedAtMillis: Long = 0L,
    ): LauncherAppInfo {
        return LauncherAppInfo(
            componentName = ComponentName(packageName, className),
            label = label,
            user = Process.myUserHandle(),
            icon = ColorDrawable(Color.TRANSPARENT),
            category = category,
            searchKey = label.lowercase(),
            installedAtMillis = installedAtMillis,
        )
    }
}
