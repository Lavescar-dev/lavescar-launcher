package app.lavescar.launcher

internal data class HomeSeedCandidate(
    val packageName: String,
    val className: String,
    val label: String,
) {
    val componentKey: String = "$packageName/$className"
}

internal data class HomeSeedPlan(
    val dockItems: List<HomeSeedCandidate>,
    val pageItems: List<HomeSeedCandidate>,
)

internal object HomeSeedPlanner {
    private const val DEFAULT_DOCK_SIZE = 4
    private const val DEFAULT_PAGE_SIZE = 16

    fun shouldSeed(
        hasCompletedHomeSeed: Boolean,
        hasWorkspaceItems: Boolean,
        availableAppCount: Int,
    ): Boolean {
        return !hasCompletedHomeSeed && !hasWorkspaceItems && availableAppCount > 0
    }

    fun createPlan(
        apps: List<HomeSeedCandidate>,
        ownPackageName: String,
        preferredDockComponentKeys: List<String>,
        dockSize: Int = DEFAULT_DOCK_SIZE,
        pageSize: Int = DEFAULT_PAGE_SIZE,
    ): HomeSeedPlan {
        val filtered = apps
            .filterNot { it.packageName == ownPackageName }
            .distinctBy(HomeSeedCandidate::componentKey)
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })

        val dockItems = buildList {
            preferredDockComponentKeys.forEach { preferredKey ->
                val match = filtered.firstOrNull { it.componentKey == preferredKey }
                if (match != null && match !in this) {
                    add(match)
                }
            }
            filtered.forEach { candidate ->
                if (size >= dockSize) {
                    return@forEach
                }
                if (candidate !in this) {
                    add(candidate)
                }
            }
        }.take(dockSize)

        val pageItems = filtered
            .filterNot { it in dockItems }
            .take(pageSize)

        return HomeSeedPlan(
            dockItems = dockItems,
            pageItems = pageItems,
        )
    }
}
