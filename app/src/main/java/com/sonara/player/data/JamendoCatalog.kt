package com.sonara.player.data

/** Lightweight catalogue models. Playback continues to use the existing Track model. */
data class JamendoAlbum(
    val id: String,
    val name: String,
    val artistId: String,
    val artistName: String,
    val imageUrl: String?,
    val releaseDate: String,
    val shareUrl: String?,
)

data class JamendoArtist(
    val id: String,
    val name: String,
    val imageUrl: String?,
    val countryCode: String? = null,
    val city: String? = null,
    val shareUrl: String? = null,
)

data class JamendoPlaylist(
    val id: String,
    val name: String,
    val creatorName: String,
    val shareUrl: String?,
)
