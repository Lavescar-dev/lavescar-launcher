package app.lavescar.launcher

import java.util.Locale

data class MinimalSearchCandidate(
    val componentKey: String,
    val label: String,
    val searchKey: String,
)

object MinimalSearchEngine {

    fun search(
        candidates: List<MinimalSearchCandidate>,
        query: String,
    ): List<MinimalSearchCandidate> {
        val normalizedQuery = query.trim().lowercase(Locale.getDefault())
        if (normalizedQuery.isBlank()) {
            return candidates.sortedWith(compareBy<MinimalSearchCandidate> { it.label.lowercase(Locale.getDefault()) }.thenBy { it.componentKey })
        }

        return candidates
            .mapNotNull { candidate ->
                matchRank(candidate, normalizedQuery)?.let { rank -> candidate to rank }
            }
            .sortedWith(
                compareBy<Pair<MinimalSearchCandidate, Int>> { it.second }
                    .thenBy { it.first.label.lowercase(Locale.getDefault()) }
                    .thenBy { it.first.componentKey },
            )
            .map { it.first }
    }

    private fun matchRank(
        candidate: MinimalSearchCandidate,
        normalizedQuery: String,
    ): Int? {
        val normalizedLabel = candidate.label.lowercase(Locale.getDefault())
        if (normalizedLabel.startsWith(normalizedQuery)) {
            return 0
        }
        if (tokenize(normalizedLabel).any { it.startsWith(normalizedQuery) }) {
            return 1
        }
        if (candidate.searchKey.contains(normalizedQuery)) {
            return 2
        }
        return null
    }

    private fun tokenize(value: String): List<String> {
        return value
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter(String::isNotBlank)
    }
}
