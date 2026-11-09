package app.lavescar.launcher

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MinimalSearchEngineTest {

    @Test
    fun search_prefersPrefixThenContains() {
        val results = MinimalSearchEngine.search(
            candidates = listOf(
                candidate("pkg.calendar/Main", "Calendar", "calendar pkg.calendar"),
                candidate("pkg.localcast/Main", "Localcast", "localcast pkg.localcast"),
                candidate("pkg.mycalc/Main", "My Calc", "my calc pkg.mycalc"),
            ),
            query = "cal",
        )

        assertThat(results.map { it.label }).containsExactly("Calendar", "My Calc", "Localcast").inOrder()
    }

    @Test
    fun search_returnsAlphabeticalResultsWhenQueryBlank() {
        val results = MinimalSearchEngine.search(
            candidates = listOf(
                candidate("pkg.zeta/Main", "Zeta", "zeta"),
                candidate("pkg.alpha/Main", "Alpha", "alpha"),
            ),
            query = "  ",
        )

        assertThat(results.map { it.label }).containsExactly("Alpha", "Zeta").inOrder()
    }

    private fun candidate(
        componentKey: String,
        label: String,
        searchKey: String,
    ): MinimalSearchCandidate {
        return MinimalSearchCandidate(
            componentKey = componentKey,
            label = label,
            searchKey = searchKey,
        )
    }
}
