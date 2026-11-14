package com.sonara.player.playback

/** Decides which cached songs to delete so the play cache fits the size the user chose. */
object CacheTrimPlanner {
    data class Entry(val key: String, val bytes: Long, val lastTouch: Long)

    /**
     * Oldest-used songs go first. Songs in [protectedKeys] (what is playing right now) are never chosen.
     */
    fun keysToEvict(
        entries: List<Entry>,
        totalBytes: Long,
        limitBytes: Long,
        protectedKeys: Set<String> = emptySet(),
    ): List<String> {
        if (totalBytes <= limitBytes) return emptyList()
        val out = ArrayList<String>()
        var total = totalBytes
        for (e in entries.sortedBy { it.lastTouch }) {
            if (total <= limitBytes) break
            if (e.key in protectedKeys) continue
            out += e.key
            total -= e.bytes
        }
        return out
    }
}
