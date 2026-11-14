package com.sonara.player.data

import org.json.JSONArray
import org.json.JSONObject

/** One song the user chose to download. The file itself lives in the app's own music folder. */
data class DownloadRecord(
    val track: Track,
    /** Id given by Android's DownloadManager. */
    val systemId: Long,
    val fileName: String,
    /** True once the file has been fully downloaded. */
    val done: Boolean,
)

object DownloadRecordCodec {
    fun encode(list: List<DownloadRecord>): String {
        val arr = JSONArray()
        list.forEach { r ->
            arr.put(
                JSONObject().apply {
                    put("track", r.track.toJson())
                    put("sid", r.systemId)
                    put("file", r.fileName)
                    put("done", r.done)
                }
            )
        }
        return arr.toString()
    }

    fun decode(json: String?): List<DownloadRecord> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val track = o.optJSONObject("track")?.toTrack() ?: return@mapNotNull null
                DownloadRecord(
                    track = track,
                    systemId = o.optLong("sid", -1L),
                    fileName = o.optString("file", ""),
                    done = o.optBoolean("done", false),
                ).takeIf { it.fileName.isNotBlank() }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
