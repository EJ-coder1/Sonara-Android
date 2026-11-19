package com.sonara.player.data

import org.junit.Test
import kotlin.test.assertEquals

class SongSortTest {
    private fun song(title: String, artist: String = "A", duration: Long = 200_000, added: Long = 0) = Track(
        id = "local:$title",
        title = title,
        artist = artist,
        album = "",
        durationMs = duration,
        uri = "content://x/$title",
        artworkUri = null,
        source = TrackSource.LOCAL,
        addedAt = added,
    )

    private val songs = listOf(
        song("banana", artist = "Zed", duration = 100_000, added = 5),
        song("Apple", artist = "Amy", duration = 300_000, added = 9),
        song("cherry", artist = "amy", duration = 200_000, added = 1),
    )

    @Test
    fun titleIgnoresCase() {
        assertEquals(listOf("Apple", "banana", "cherry"), sortSongs(songs, SongSort.TITLE).map { it.title })
    }

    @Test
    fun artistThenTitle() {
        assertEquals(listOf("Apple", "cherry", "banana"), sortSongs(songs, SongSort.ARTIST).map { it.title })
    }

    @Test
    fun newestFirstUsesTheAddedDate() {
        assertEquals(listOf("Apple", "banana", "cherry"), sortSongs(songs, SongSort.NEWEST).map { it.title })
    }

    @Test
    fun longestFirst() {
        assertEquals(listOf("Apple", "cherry", "banana"), sortSongs(songs, SongSort.LONGEST).map { it.title })
    }

    @Test
    fun sortingNeverLosesOrDuplicatesSongs() {
        for (sort in SongSort.entries) {
            assertEquals(songs.map { it.id }.toSet(), sortSongs(songs, sort).map { it.id }.toSet())
        }
    }
}
