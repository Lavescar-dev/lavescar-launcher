package app.lavescar.launcher

import app.lavescar.launcher.core.AppLibraryOverviewBlockUiState
import app.lavescar.launcher.core.AppLibrarySearchRowUiState
import app.lavescar.launcher.core.DrawerAppItem
import app.lavescar.launcher.core.FolderChildItem
import app.lavescar.launcher.core.FolderPageUiState
import app.lavescar.launcher.data.WorkspaceItemModel
import app.lavescar.launcher.data.WorkspaceModel
import app.lavescar.launcher.system.LauncherAppCategory
import app.lavescar.launcher.system.LauncherAppInfo

internal object AppLibraryBuilder {
    private const val SUGGESTED_COUNT = 4
    private const val RECENTLY_ADDED_COUNT = 4
    private const val CATEGORY_PREVIEW_COUNT = 4
    private const val FOLDER_PAGE_SIZE = 9

    fun buildSuggestedApps(
        apps: List<LauncherAppInfo>,
        workspace: WorkspaceModel,
    ): List<LauncherAppInfo> {
        val appMap = apps.associateBy { it.componentKey }
        val result = linkedSetOf<LauncherAppInfo>()

        workspace.dockItems
            .filterIsInstance<WorkspaceItemModel.AppShortcut>()
            .forEach { shortcut ->
                appMap[shortcut.componentKey()]?.let(result::add)
            }

        workspace.pages
            .flatMap { it.items }
            .filterIsInstance<WorkspaceItemModel.AppShortcut>()
            .forEach { shortcut ->
                if (result.size >= SUGGESTED_COUNT) {
                    return@forEach
                }
                appMap[shortcut.componentKey()]?.let(result::add)
            }

        apps.forEach { app ->
            if (result.size >= SUGGESTED_COUNT) {
                return@forEach
            }
            result += app
        }

        return result.take(SUGGESTED_COUNT)
    }

    fun buildRecentlyAddedApps(
        apps: List<LauncherAppInfo>,
        excludedComponentKeys: Set<String>,
    ): List<LauncherAppInfo> {
        return apps
            .asSequence()
            .filterNot { excludedComponentKeys.contains(it.componentKey) }
            .sortedWith(
                compareByDescending<LauncherAppInfo> { it.installedAtMillis }
                    .thenBy { it.label.lowercase() },
            )
            .take(RECENTLY_ADDED_COUNT)
            .toList()
    }

    fun buildOverviewBlocks(
        apps: List<LauncherAppInfo>,
        workspace: WorkspaceModel,
        suggestedTitle: String,
        recentlyAddedTitle: String,
    ): List<AppLibraryOverviewBlockUiState> {
        val suggestedApps = buildSuggestedApps(apps, workspace)
        val recentlyAdded = buildRecentlyAddedApps(
            apps = apps,
            excludedComponentKeys = suggestedApps.mapTo(linkedSetOf(), LauncherAppInfo::componentKey),
        )
        val categoryBlocks = apps
            .groupBy { it.category }
            .toList()
            .sortedWith(
                compareBy<Pair<LauncherAppCategory, List<LauncherAppInfo>>> { categoryPriority(it.first) }
                    .thenByDescending { it.second.size },
            )
            .mapNotNull { (category, groupedApps) ->
                val previewApps = groupedApps
                    .sortedBy { it.label.lowercase() }
                    .take(CATEGORY_PREVIEW_COUNT)
                    .map { it.toDrawerItem() }
                if (previewApps.isEmpty()) {
                    return@mapNotNull null
                }
                AppLibraryOverviewBlockUiState.Category(
                    id = category.name,
                    title = category.title,
                    previewApps = previewApps,
                )
            }
        return buildList {
            add(
                AppLibraryOverviewBlockUiState.Suggested(
                    title = suggestedTitle,
                    apps = suggestedApps.map { it.toDrawerItem() },
                ),
            )
            if (recentlyAdded.isNotEmpty()) {
                add(
                    AppLibraryOverviewBlockUiState.RecentlyAdded(
                        title = recentlyAddedTitle,
                        apps = recentlyAdded.map { it.toDrawerItem() },
                    ),
                )
            }
            addAll(categoryBlocks)
        }
    }

    fun buildSearchRows(
        apps: List<LauncherAppInfo>,
        query: String,
    ): List<AppLibrarySearchRowUiState> {
        val normalizedQuery = query.trim().lowercase()
        val filtered = apps
            .mapNotNull { app ->
                searchRank(app, normalizedQuery)?.let { rank -> app to rank }
            }
            .sortedWith(
                compareBy<Pair<LauncherAppInfo, Int>> { it.second }
                    .thenBy { it.first.label.lowercase() }
                    .thenBy { it.first.componentKey },
            )
            .map { it.first }
        return filtered
            .groupBy { indexLetterFor(it.label) }
            .toSortedMap()
            .flatMap { (letter, groupedApps) ->
                buildList {
                    add(
                        AppLibrarySearchRowUiState.Header(
                            stableId = "header_$letter",
                            title = letter,
                        ),
                    )
                    groupedApps.forEach { app ->
                        add(
                            AppLibrarySearchRowUiState.App(
                                stableId = app.componentKey,
                                item = app.toDrawerItem(),
                            ),
                        )
                    }
                }
            }
    }

    fun paginateFolderItems(items: List<FolderChildItem>): List<FolderPageUiState> {
        if (items.isEmpty()) {
            return listOf(FolderPageUiState(index = 0, items = emptyList()))
        }
        return items.chunked(FOLDER_PAGE_SIZE).mapIndexed { index, chunk ->
            FolderPageUiState(
                index = index,
                items = chunk,
            )
        }
    }

    private fun categoryPriority(category: LauncherAppCategory): Int {
        return when (category) {
            LauncherAppCategory.PRODUCTIVITY -> 0
            LauncherAppCategory.SOCIAL -> 1
            LauncherAppCategory.UTILITIES -> 2
            LauncherAppCategory.PHOTOS_VIDEO -> 3
            LauncherAppCategory.ENTERTAINMENT -> 4
            LauncherAppCategory.GAMES -> 5
            LauncherAppCategory.TRAVEL -> 6
            LauncherAppCategory.HEALTH -> 7
            LauncherAppCategory.OTHER -> 8
        }
    }

    private fun WorkspaceItemModel.AppShortcut.componentKey(): String {
        return "$packageName/$className"
    }

    private fun indexLetterFor(label: String): String {
        val first = label.trim().firstOrNull() ?: return "#"
        return if (first.isLetterOrDigit()) {
            first.uppercase()
        } else {
            "#"
        }
    }

    private fun searchRank(
        app: LauncherAppInfo,
        normalizedQuery: String,
    ): Int? {
        if (normalizedQuery.isEmpty()) {
            return 3
        }
        val label = app.label.lowercase()
        val words = label.split(' ', '-', '_')
        return when {
            label.startsWith(normalizedQuery) -> 0
            words.any { it.startsWith(normalizedQuery) } -> 1
            app.searchKey.contains(normalizedQuery) -> 2
            else -> null
        }
    }

    private fun LauncherAppInfo.toDrawerItem(): DrawerAppItem {
        return DrawerAppItem(
            componentName = componentName,
            title = label,
            icon = icon,
        )
    }
}
