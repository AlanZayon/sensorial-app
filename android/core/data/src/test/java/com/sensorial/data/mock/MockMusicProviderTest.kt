package com.sensorial.data.mock

import com.sensorial.domain.recommendation.RecommendationContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MockMusicProviderTest {

    @Test
    fun `catalog has a diverse seed pool`() = runTest {
        val provider = MockMusicProvider()
        val candidates = provider.getCandidates(
            RecommendationContext(null, emptyList(), emptySet()),
        )
        assertTrue(candidates.size >= 20)
        val genres = candidates.flatMap { it.genres.map { g -> g.value } }.toSet()
        assertTrue(genres.size >= 6)
    }

    @Test
    fun `search filters by title`() = runTest {
        val provider = MockMusicProvider()
        val results = provider.searchTracks("Neon")
        assertEquals(1, results.size)
        assertEquals("Neon Pulse", results.first().title)
    }
}
