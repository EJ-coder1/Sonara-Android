package com.sonara.player.data

import org.junit.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MixBuilderTest {

    private fun t(id: String, artist: String, album: String = "") =
        Track(id, "T$id", artist, album, 180_000, "uri$id", null, TrackSource.LOCAL)

    @Test
    fun seedFirstThenSameArtistThenAlbumThenRest() {
        val seed = t("1", "Ana", "Alpha")
        val lib = listOf(
            seed,
            t("2", "Ben", "Beta"),
            t("3", "Ana", "Gamma"),
            t("4", "Cy", "Alpha"),
            t("5", "Dee", "Delta"),
        )
        val mix = MixBuilder.build(seed, lib, random = Random(7))
        assertEquals("1", mix[0].id)
        assertEquals("3", mix[1].id) // same artist
        assertEquals("4", mix[2].id) // same album
        assertEquals(setOf("2", "5"), mix.drop(3).map { it.id }.toSet())
        assertEquals(5, mix.size)
    }

    @Test
    fun noDuplicatesAndRespectsMax() {
        val seed = t("0", "Ana")
        val lib = (0 until 100).map { t("$it", "Ana") }
        val mix = MixBuilder.build(seed, lib, extra = lib, max = 25, random = Random(1))
        assertEquals(25, mix.size)
        assertEquals(mix.size, mix.map { it.id }.toSet().size)
    }

    @Test
    fun unknownArtistDoesNotGroupEverything() {
        val seed = t("1", "Unknown artist", "Unknown album")
        val lib = listOf(seed, t("2", "Unknown artist", "Unknown album"), t("3", "Zed"))
        val mix = MixBuilder.build(seed, lib, random = Random(3))
        assertEquals("1", mix[0].id)
        assertEquals(3, mix.size)
    }

    @Test
    fun worksWithEmptyLibrary() {
        val seed = t("1", "Ana")
        assertEquals(listOf(seed), MixBuilder.build(seed, emptyList()))
        assertTrue(MixBuilder.build(seed, emptyList(), max = 0).size == 1)
    }
}
