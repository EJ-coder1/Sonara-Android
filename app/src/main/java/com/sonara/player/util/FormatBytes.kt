package com.sonara.player.util

import java.util.Locale

/** 1536 -> "1.5 KB", 5_242_880 -> "5.0 MB". Used for download and cache sizes. */
fun formatBytes(bytes: Long): String {
    val b = bytes.coerceAtLeast(0L)
    return when {
        b < 1024L -> "$b B"
        b < 1024L * 1024L -> String.format(Locale.ROOT, "%.1f KB", b / 1024.0)
        b < 1024L * 1024L * 1024L -> String.format(Locale.ROOT, "%.1f MB", b / (1024.0 * 1024.0))
        else -> String.format(Locale.ROOT, "%.2f GB", b / (1024.0 * 1024.0 * 1024.0))
    }
}
