package com.sensorial.data.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sensorial.domain.model.Album
import com.sensorial.domain.model.Artist
import com.sensorial.domain.model.ArtistId
import com.sensorial.domain.model.AudioFeatures
import com.sensorial.domain.model.GenreId
import com.sensorial.domain.model.GestureIntensity
import com.sensorial.domain.model.HorizontalIntent
import com.sensorial.domain.model.InteractionId
import com.sensorial.domain.model.InteractionType
import com.sensorial.domain.model.Track
import com.sensorial.domain.model.TrackId
import com.sensorial.domain.model.TrackInteraction
import com.sensorial.domain.model.VerticalIntent
import com.sensorial.domain.preference.UserPreferenceProfile
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RoomRepositoriesTest {

    private lateinit var database: SensorialDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = SensorialDatabase.createInMemory(context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun track(id: String) = Track(
        id = TrackId(id),
        title = "Track $id",
        artists = listOf(Artist(ArtistId("a-$id"), "Artist $id")),
        album = Album("al-$id", "Album"),
        genres = listOf(GenreId("pop")),
        audioFeatures = AudioFeatures(0.5, 0.5, 0.5, 120.0),
    )

    @Test
    fun historySurvivesHydration() = runBlocking {
        val repo = RoomPlaybackHistoryRepository(database)
        repo.push(track("A"))
        repo.push(track("B"))
        repo.previous()

        val reloaded = RoomPlaybackHistoryRepository(database)
        reloaded.hydrate()
        assertEquals("A", reloaded.current()?.id?.value)
        assertEquals(true, reloaded.canGoForward())
    }

    @Test
    fun interactionsAndPreferencesPersist() = runBlocking {
        val interactions = RoomInteractionRepository(database)
        val preferences = RoomUserPreferenceRepository(database)

        interactions.append(
            TrackInteraction(
                id = InteractionId("i1"),
                trackId = TrackId("A"),
                interactionType = InteractionType.Preference,
                verticalIntent = VerticalIntent.Positive,
                horizontalIntent = HorizontalIntent.None,
                gestureIntensity = GestureIntensity.High,
                gestureVelocity = 0.9,
                listeningDurationMs = 12_000,
                timestampEpochMs = 100,
            ),
        )
        preferences.save(
            UserPreferenceProfile(
                genreScores = mapOf(GenreId("pop") to 0.7),
                positiveSignalCount = 1,
            ),
        )

        val interactions2 = RoomInteractionRepository(database)
        interactions2.hydrate()
        val preferences2 = RoomUserPreferenceRepository(database)
        preferences2.hydrate()

        assertEquals(1, interactions2.recent(10).size)
        assertEquals(0.7, preferences2.get().genreScore(GenreId("pop")), 0.0001)
        assertNotNull(preferences2.get().genreScores[GenreId("pop")])
    }
}
