package com.sonara.player.data

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LibraryGroupsTest {
    private fun song(title: String, artist: String, album: String) = Track(
        id = "local:$title",
        title = title,
        artist = artist,
        album = album,
        durationMs = 200_000,
        uri = "content://x/$title",
        artworkUri = null,
        source = TrackSource.LOCAL,
    )

    private val songs = listOf(
        song("One", "Alan Walker", "Different World"),
        song("Two", "alan walker", "Different World"),
        song("Three", "Arthur Miguel", "Unknown album"),
        song("Four", "Unknown artist", "Unknown album"),
        song("Five", "Zara", "Solo"),
    )

    @Test
    fun artistsAreMergedIgnoringCaseAndSortedByName() {
        val artists = LibraryGroups.artists(songs)
        assertEquals(listOf("Alan Walker", "Arthur Miguel", "Zara"), artists.map { it.name })
        assertEquals(2, artists.first().count)
    }

    @Test
    fun unknownNamesAreLeftOut() {
        assertTrue(LibraryGroups.artists(songs).none { it.name.equals("Unknown artist", ignoreCase = true) })
        assertEquals(listOf("Different World", "Solo"), LibraryGroups.albums(songs).map { it.name })
    }

    @Test
    fun everyGroupHasASongToTakeTheCoverFrom() {
        for (group in LibraryGroups.albums(songs) + LibraryGroups.artists(songs)) {
            assertTrue(group.count >= 1)
            assertTrue(songs.any { it.id == group.sample.id })
        }
    }
}
