package com.sonara.player.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WorldwideMusicCatalogTest {
    @Test
    fun storefrontsPutTheUsersRegionFirstAndIncludeGlobalMarkets() {
        assertEquals(listOf("PH", "US", "GB", "KR"), worldwideStorefronts("ph"))
        assertEquals(listOf("JP", "PH", "US", "GB", "KR"), worldwideStorefronts("jp"))
    }

    @Test
    fun invalidStorefrontFallsBackToPhilippines() {
        assertEquals(listOf("PH", "US", "GB", "KR"), worldwideStorefronts("???"))
    }

    @Test
    fun catalogResultsAreDeduplicatedByTitleAndArtistKeepingFirstStorefront() {
        val philippines = GlobalCatalogTrack("apple:PH:1", "Song", "Artist", "Album", "PH", "https://music.apple.com/ph/album/1", 1000)
        val unitedStatesDuplicate = philippines.copy(id = "apple:US:2", storefront = "US", storeUrl = "https://music.apple.com/us/album/2")
        val another = philippines.copy(id = "apple:PH:3", title = "Another Song")

        assertEquals(listOf(philippines, another), mergeWorldwideCatalogPages(listOf(listOf(philippines), listOf(unitedStatesDuplicate, another))))
    }
}
