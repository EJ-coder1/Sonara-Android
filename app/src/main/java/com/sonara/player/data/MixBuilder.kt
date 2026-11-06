package com.sonara.player.data

import kotlin.random.Random

/**
 * "Start mix with this song": the seed first, then songs by the same artist, then from the same
 * album, then everything else shuffled. Works on whatever songs are available on the phone
 * (plus optional extra tracks), so it needs no account and sends nothing anywhere.
 */
object MixBuilder {

    private fun known(value: String, unknown: String) = value.isNotBlank() && !value.equals(unknown, ignoreCase = true)

    fun build(
        seed: Track,
        library: List<Track>,
        extra: List<Track> = emptyList(),
        random: Random = Random.Default,
        max: Int = 40,
    ): List<Track> {
        val pool = (library + extra).distinctBy { it.id }.filter { it.id != seed.id }
        val artistKnown = known(seed.artist, "Unknown artist")
        val albumKnown = known(seed.album, "Unknown album")

        val sameArtist = if (artistKnown) pool.filter { it.artist.equals(seed.artist, ignoreCase = true) } else emptyList()
        val artistIds = sameArtist.mapTo(HashSet()) { it.id }
        val sameAlbum = if (albumKnown) {
            pool.filter { it.id !in artistIds && it.album.equals(seed.album, ignoreCase = true) }
        } else emptyList()
        val albumIds = sameAlbum.mapTo(HashSet()) { it.id }
        val rest = pool.filter { it.id !in artistIds && it.id !in albumIds }

        return (listOf(seed) + sameArtist.shuffled(random) + sameAlbum.shuffled(random) + rest.shuffled(random))
            .take(max.coerceAtLeast(1))
    }
}
