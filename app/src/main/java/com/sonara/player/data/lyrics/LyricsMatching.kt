package com.sonara.player.data.lyrics

import java.util.Locale

/** Conservative metadata checks used before displaying lyrics for a track. */
object LyricsMatching {
    private val versionMarkers = linkedMapOf(
        "live" to Regex("\\b(?:live|concert|session)\\b", RegexOption.IGNORE_CASE),
        "unplugged" to Regex("\\bunplugged\\b", RegexOption.IGNORE_CASE),
        "acoustic" to Regex("\\bacoustic\\b", RegexOption.IGNORE_CASE),
        "remix" to Regex("\\bremix\\b", RegexOption.IGNORE_CASE),
        "remaster" to Regex("\\bremaster(?:ed)?\\b", RegexOption.IGNORE_CASE),
        "reimagined" to Regex("\\breimagined\\b", RegexOption.IGNORE_CASE),
        "radio-edit" to Regex("\\bradio\\s+edit\\b", RegexOption.IGNORE_CASE),
        "extended" to Regex("\\bextended(?:\\s+mix)?\\b", RegexOption.IGNORE_CASE),
        "cover" to Regex("\\bcover\\b", RegexOption.IGNORE_CASE),
        "karaoke" to Regex("\\bkaraoke\\b", RegexOption.IGNORE_CASE),
        "instrumental" to Regex("\\binstrumental\\b", RegexOption.IGNORE_CASE),
        "slowed" to Regex("\\bslowed\\b", RegexOption.IGNORE_CASE),
        "sped" to Regex("\\b(?:sped\\s?up|speed\\s?up|nightcore)\\b", RegexOption.IGNORE_CASE),
        "demo" to Regex("\\bdemo\\b", RegexOption.IGNORE_CASE),
        "lullaby" to Regex("\\blullaby\\b", RegexOption.IGNORE_CASE),
        "8d" to Regex("\\b8d\\b", RegexOption.IGNORE_CASE),
    )

    fun cleanTitle(raw: String): String {
        var title = raw.replace('_', ' ')
        // Remove featured-artist credits, but keep recording/version tags such as (Live) or (Remix).
        title = title.replace(
            Regex("(?i)\\s*[\\[(](?:feat\\.?|ft\\.?|featuring|with)[^\\])]*[\\])]"),
            " ",
        )
        title = title.replace(Regex("(?i)\\s+(?:feat\\.?|ft\\.?|featuring)\\s+.*$"), " ")
        title = title.replace(
            Regex("(?i)\\s*[\\[(]\\s*(?:official\\s*)?(?:music\\s*)?(?:video|audio|visualizer|lyrics?\\s*video|lyrics?|m/v|hd|hq|4k|full\\s*song)\\s*[\\])]"),
            " ",
        )
        title = title.replace(Regex("(?i)\\s*[\\[(]\\s*official\\s*[\\])]"), " ")
        return title.replace(Regex("\\s+"), " ").trim().ifBlank { raw.trim() }
    }

    fun cleanArtist(raw: String): String = raw
        .replace(Regex("(?i)\\s*[\\[(](?:feat\\.?|ft\\.?)[^\\])]*[\\])]"), " ")
        .replace(Regex("(?i)\\s*(?:feat\\.?|ft\\.?|featuring)\\s+.*$"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    /** Recording variants (live, remix, remaster, reimagined, acoustic, cover, etc.) must match. */
    fun sameVersion(requestTitle: String, candidateTitle: String): Boolean =
        versionTags(requestTitle) == versionTags(candidateTitle)

    fun matches(
        candidateTitle: String,
        candidateArtist: String,
        requestedTitle: String,
        requestedArtist: String?,
    ): Boolean {
        if (!titleMatches(candidateTitle, requestedTitle, requestedArtist)) return false
        if (!sameVersion(requestedTitle, candidateTitle)) return false
        return requestedArtist.isNullOrBlank() || artistMatches(candidateArtist, requestedArtist)
    }

    fun titleMatches(candidate: String, requested: String, requestedArtist: String? = null): Boolean {
        val wanted = normalize(cleanTitle(requested))
        if (wanted.isBlank()) return false
        val candidates = mutableSetOf(normalize(cleanTitle(candidate)))
        if (!requestedArtist.isNullOrBlank()) {
            val prefix = Regex("^\\s*${Regex.escape(requestedArtist.trim())}\\s*[-–—:]\\s*", RegexOption.IGNORE_CASE)
            candidates += normalize(cleanTitle(candidate.replace(prefix, "")))
        }
        return candidates.any { strictTitleMatch(it, wanted) }
    }

    fun artistMatches(candidate: String, requested: String): Boolean {
        val a = normalize(cleanArtist(candidate))
        val b = normalize(cleanArtist(requested))
        if (a.isBlank() || b.isBlank()) return false
        if (a == b) return true
        val aa = a.split(' ').filter { it.isNotBlank() }.toSet()
        val bb = b.split(' ').filter { it.isNotBlank() }.toSet()
        if (aa.isEmpty() || bb.isEmpty()) return false
        val common = aa.intersect(bb).size
        // The shorter artist name must be substantially represented; this rejects Ann vs Annie.
        return common.toFloat() / minOf(aa.size, bb.size) >= 0.8f &&
            common.toFloat() / maxOf(aa.size, bb.size) >= 0.6f
    }

    private fun strictTitleMatch(a: String, b: String): Boolean {
        if (a.isBlank() || b.isBlank()) return false
        if (a == b) return true
        val ratio = minOf(a.length, b.length).toFloat() / maxOf(a.length, b.length)
        if ((a.contains(b) || b.contains(a)) && ratio >= 0.78f) return true
        val aa = a.split(' ').filter { it.isNotBlank() }.toSet()
        val bb = b.split(' ').filter { it.isNotBlank() }.toSet()
        if (aa.isEmpty() || bb.isEmpty()) return false
        val common = aa.intersect(bb).size
        val coverageA = common.toFloat() / aa.size
        val coverageB = common.toFloat() / bb.size
        return coverageA >= 0.82f && coverageB >= 0.82f
    }

    private fun versionTags(raw: String): Set<String> {
        val segments = buildList {
            Regex("[\\[(](.*?)[\\])]" ).findAll(raw).forEach { add(it.groupValues[1]) }
            Regex("\\s*[-–—:]\\s*([^-–—:\\[\\]]+)\\s*$").find(raw)?.let { add(it.groupValues[1]) }
        }
        return versionMarkers.filterValues { marker -> segments.any(marker::containsMatchIn) }.keys
    }

    private fun normalize(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
