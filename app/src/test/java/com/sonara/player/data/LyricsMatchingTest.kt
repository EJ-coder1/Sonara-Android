package com.sonara.player.data

import com.sonara.player.data.lyrics.LyricsMatching
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LyricsMatchingTest {
    @Test
    fun acceptsTitleAndArtistWithBenignMetadata() {
        assertTrue(
            LyricsMatching.matches(
                candidateTitle = "Lifetime",
                candidateArtist = "Ben&Ben feat. Guest Singer",
                requestedTitle = "Lifetime (Official Audio)",
                requestedArtist = "Ben&Ben",
            ),
        )
    }

    @Test
    fun rejectsDifferentSongWithOverlappingTitle() {
        assertFalse(LyricsMatching.titleMatches("Love Me Like You Do", "Love"))
    }

    @Test
    fun rejectsDifferentRecordingVersions() {
        assertFalse(LyricsMatching.sameVersion("Lifetime", "Lifetime (Live)"))
        assertFalse(LyricsMatching.sameVersion("Lifetime", "Lifetime (Reimagined)"))
        assertTrue(LyricsMatching.sameVersion("Lifetime (Live)", "Lifetime (Live at Studio)"))
    }

    @Test
    fun rejectsSimilarButDifferentArtistNames() {
        assertFalse(LyricsMatching.artistMatches("Annie", "Ann"))
    }
}
