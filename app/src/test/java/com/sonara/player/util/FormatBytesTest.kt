package com.sonara.player.util

import org.junit.Test
import kotlin.test.assertEquals

class FormatBytesTest {
    @Test
    fun smallSizesAreInBytes() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("1023 B", formatBytes(1023))
    }

    @Test
    fun largerSizesUseOneUnitWithADecimal() {
        assertEquals("1.5 KB", formatBytes(1536))
        assertEquals("5.0 MB", formatBytes(5L * 1024 * 1024))
        assertEquals("3.00 GB", formatBytes(3L * 1024 * 1024 * 1024))
    }

    @Test
    fun negativeSizesShowAsZero() {
        assertEquals("0 B", formatBytes(-5))
    }
}
