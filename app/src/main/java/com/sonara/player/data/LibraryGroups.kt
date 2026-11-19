package com.sonara.player.data

/** One album or artist in the Library, with a song to take the cover from. */
data class LibraryGroup(val name: String, val count: Int, val sample: Track)

/** Groups the songs on the phone into albums and artists (songs with no real tag are left out). */
object LibraryGroups {
    private val unknownNames = setOf("", "unknown artist", "unknown album", "<unknown>")

    fun artists(songs: List<Track>): List<LibraryGroup> = group(songs) { it.artist }

    fun albums(songs: List<Track>): List<LibraryGroup> = group(songs) { it.album }

    private fun group(songs: List<Track>, key: (Track) -> String): List<LibraryGroup> =
        songs
            .filter { key(it).trim().lowercase() !in unknownNames }
            .groupBy { key(it).trim().lowercase() }
            .map { (_, list) -> LibraryGroup(key(list.first()).trim(), list.size, list.first()) }
            .sortedBy { it.name.lowercase() }
}
