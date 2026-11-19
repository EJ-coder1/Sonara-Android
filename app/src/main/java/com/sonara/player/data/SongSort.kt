package com.sonara.player.data

/** How the Songs list in Library is ordered. */
enum class SongSort(val label: String) {
    TITLE("Title (A to Z)"),
    ARTIST("Artist (A to Z)"),
    NEWEST("Newest first"),
    LONGEST("Longest first"),
}

fun sortSongs(songs: List<Track>, sort: SongSort): List<Track> = when (sort) {
    SongSort.TITLE -> songs.sortedWith(compareBy<Track, String>(String.CASE_INSENSITIVE_ORDER) { it.title })
    SongSort.ARTIST -> songs.sortedWith(
        compareBy<Track, String>(String.CASE_INSENSITIVE_ORDER) { it.artist }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
    )
    SongSort.NEWEST -> songs.sortedWith(
        compareByDescending<Track> { it.addedAt }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
    )
    SongSort.LONGEST -> songs.sortedWith(
        compareByDescending<Track> { it.durationMs }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
    )
}
