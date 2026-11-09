package app.lavescar.launcher

import java.util.Locale

data class MinimalPinnedSeedCandidate(
    val componentKey: String,
    val packageName: String,
    val label: String,
)

object MinimalPinnedSeedPlanner {

    fun createSeed(
        apps: List<MinimalPinnedSeedCandidate>,
        workspaceComponentKeys: List<String>,
        preferredDefaultKeys: List<String>,
        ownPackageName: String,
        maxPinned: Int = 6,
    ): List<String> {
        val candidates = apps
            .filterNot { it.packageName == ownPackageName }
            .associateBy { it.componentKey }
        if (candidates.isEmpty()) {
            return emptyList()
        }

        val ordered = linkedSetOf<String>()

        firefoxCandidate(apps)?.let { ordered += it.componentKey }
        workspaceComponentKeys.forEach { key ->
            if (key in candidates) {
                ordered += key
            }
        }
        preferredDefaultKeys.forEach { key ->
            if (key in candidates) {
                ordered += key
            }
        }

        prioritizedKeywords().forEach { keywords ->
            apps.firstOrNull { candidate ->
                candidate.componentKey !in ordered && candidate.matchesAny(keywords)
            }?.let { ordered += it.componentKey }
        }

        apps.sortedBy { it.label.lowercase(Locale.getDefault()) }
            .forEach { candidate ->
                if (candidate.componentKey in candidates) {
                    ordered += candidate.componentKey
                }
            }

        return ordered
            .filter { it in candidates }
            .take(maxPinned)
    }

    private fun firefoxCandidate(apps: List<MinimalPinnedSeedCandidate>): MinimalPinnedSeedCandidate? {
        FIREFOX_PACKAGE_PRIORITY.forEach { packageName ->
            apps.firstOrNull { it.packageName == packageName }?.let { return it }
        }
        return apps.firstOrNull { app ->
            val normalized = app.label.lowercase(Locale.getDefault())
            normalized.contains("firefox") || app.packageName.startsWith("org.mozilla.firefox")
        }
    }

    private fun prioritizedKeywords(): List<List<String>> {
        return listOf(
            listOf("whatsapp", "wa business"),
            listOf("camera", "kamera"),
            listOf("phone", "dialer", "telefon"),
            listOf("messages", "message", "sms", "mesaj"),
            listOf("browser", "chrome", "internet", "opera", "edge"),
        )
    }

    private fun MinimalPinnedSeedCandidate.matchesAny(keywords: List<String>): Boolean {
        val normalizedLabel = label.lowercase(Locale.getDefault())
        val normalizedPackage = packageName.lowercase(Locale.getDefault())
        return keywords.any { keyword ->
            normalizedLabel.contains(keyword) || normalizedPackage.contains(keyword)
        }
    }

    private val FIREFOX_PACKAGE_PRIORITY = listOf(
        "org.mozilla.firefox",
        "org.mozilla.firefox_beta",
        "org.mozilla.fenix",
        "org.mozilla.fennec_fdroid",
    )
}
