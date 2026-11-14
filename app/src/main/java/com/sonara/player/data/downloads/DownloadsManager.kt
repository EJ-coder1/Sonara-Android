package com.sonara.player.data.downloads

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.sonara.player.data.DownloadRecord
import com.sonara.player.data.JamendoRepository
import com.sonara.player.data.LibraryStore
import com.sonara.player.data.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

enum class DownloadStatus { QUEUED, RUNNING, DONE, FAILED }

data class DownloadItem(
    val track: Track,
    val status: DownloadStatus,
    val bytes: Long,
    val totalBytes: Long,
) {
    /** 0..1, or 0 while the size is not known yet. */
    val progress: Float get() = if (totalBytes > 0L) (bytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
}

enum class EnqueueResult { STARTED, ALREADY, NOT_ALLOWED, NO_STORAGE, FAILED }

/**
 * Downloads online songs (only those whose artist allows it) for offline listening.
 *
 * The actual transfer is done by Android's own DownloadManager, so it keeps going when the app is
 * closed and shows the system download notification. Files are saved in Sonara's own music folder
 * (they are removed when the app is uninstalled, and other apps can't see them).
 */
class DownloadsManager(
    private val context: Context,
    private val store: LibraryStore,
    private val jamendo: JamendoRepository,
    private val scope: CoroutineScope,
) {
    private val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    private val _items = MutableStateFlow<List<DownloadItem>>(emptyList())
    val items: StateFlow<List<DownloadItem>> = _items.asStateFlow()

    private val _downloadedIds = MutableStateFlow<Set<String>>(emptySet())
    /** Ids of songs that are fully downloaded (used to show the offline badge). */
    val downloadedIds: StateFlow<Set<String>> = _downloadedIds.asStateFlow()

    private val _diskBytes = MutableStateFlow(0L)
    /** Space taken by finished downloads. */
    val diskBytes: StateFlow<Long> = _diskBytes.asStateFlow()

    @Volatile private var records: List<DownloadRecord> = emptyList()
    @Volatile private var readyFiles: Map<String, File> = emptyMap()
    @Volatile private var wifiOnly = true
    private val wake = Channel<Unit>(Channel.CONFLATED)
    /** "Download all" can ask about many songs at once; this keeps the look-ups to a few at a time. */
    private val lookupGate = Semaphore(3)

    init {
        scope.launch { store.downloadRecords.collect { list -> records = list; wake.trySend(Unit) } }
        scope.launch { store.offlinePrefs.collect { wifiOnly = it.wifiOnlyDownloads } }
        scope.launch { pollLoop() }
    }

    /** Checks on the downloads every second while any is running, otherwise sleeps until something changes. */
    private suspend fun pollLoop() {
        while (true) {
            val active = refresh()
            if (active) delay(1000) else wake.receive()
        }
    }

    private class Snap(val status: Int, val bytes: Long, val total: Long)

    /** Returns true while any download is still waiting or running. */
    private suspend fun refresh(): Boolean = withContext(Dispatchers.IO) {
        val recs = records
        if (recs.isEmpty()) {
            _items.value = emptyList()
            _downloadedIds.value = emptySet()
            _diskBytes.value = 0L
            readyFiles = emptyMap()
            return@withContext false
        }

        val snaps = HashMap<Long, Snap>()
        try {
            val ids = recs.map { it.systemId }.filter { it >= 0L }.toLongArray()
            if (ids.isNotEmpty()) {
                dm.query(DownloadManager.Query().setFilterById(*ids))?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
                    val statusCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                    val soFarCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val totalCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    while (c.moveToNext()) {
                        snaps[c.getLong(idCol)] = Snap(c.getInt(statusCol), c.getLong(soFarCol), c.getLong(totalCol))
                    }
                }
            }
        } catch (e: Exception) {
            // Keep whatever we knew; the next pass tries again.
        }

        val items = ArrayList<DownloadItem>(recs.size)
        val ready = HashMap<String, File>()
        val newlyDone = HashSet<Long>()
        var disk = 0L
        var active = false

        for (r in recs) {
            val file = fileFor(r)
            val fileOk = file != null && file.exists() && file.length() > 0L
            val snap = snaps[r.systemId]
            val status = when {
                r.done -> if (fileOk) DownloadStatus.DONE else DownloadStatus.FAILED
                snap == null -> if (fileOk) DownloadStatus.DONE else DownloadStatus.FAILED
                snap.status == DownloadManager.STATUS_SUCCESSFUL -> if (fileOk) DownloadStatus.DONE else DownloadStatus.FAILED
                snap.status == DownloadManager.STATUS_RUNNING -> DownloadStatus.RUNNING
                snap.status == DownloadManager.STATUS_PENDING || snap.status == DownloadManager.STATUS_PAUSED -> DownloadStatus.QUEUED
                else -> DownloadStatus.FAILED
            }
            if (status == DownloadStatus.QUEUED || status == DownloadStatus.RUNNING) active = true
            if (status == DownloadStatus.DONE && file != null) {
                ready[r.track.id] = file
                disk += file.length()
                if (!r.done) newlyDone += r.systemId
            }
            items += DownloadItem(
                track = r.track,
                status = status,
                bytes = if (status == DownloadStatus.DONE) (file?.length() ?: 0L) else (snap?.bytes ?: 0L).coerceAtLeast(0L),
                totalBytes = if (status == DownloadStatus.DONE) (file?.length() ?: 0L) else (snap?.total ?: 0L).coerceAtLeast(0L),
            )
        }

        readyFiles = ready
        _items.value = items
        _downloadedIds.value = ready.keys.toSet()
        _diskBytes.value = disk
        if (newlyDone.isNotEmpty()) {
            store.updateDownloads { list -> list.map { if (it.systemId in newlyDone) it.copy(done = true) else it } }
        }
        active
    }

    /** The downloaded copy of [track] when there is one, so it plays from the phone. */
    fun resolve(track: Track): Track {
        if (!track.isOnline) return track
        val file = readyFiles[track.id] ?: return track
        return track.copy(uri = Uri.fromFile(file).toString())
    }

    /** Starts the download and tells the caller how it went (on the main thread). */
    fun request(track: Track, onResult: (EnqueueResult) -> Unit) {
        scope.launch { onResult(download(track)) }
    }

    suspend fun download(track: Track): EnqueueResult {
        if (!track.isOnline) return EnqueueResult.NOT_ALLOWED

        // Songs saved before downloads existed don't know their download address: look it up.
        var url = track.downloadUrl
        if (url == null && track.id.startsWith("jam:") && jamendo.isConfigured) {
            url = lookupGate.withPermit { jamendo.trackById(track.id.removePrefix("jam:")).getOrNull()?.downloadUrl }
        }
        if (url == null) return EnqueueResult.NOT_ALLOWED

        val existing = records.firstOrNull { it.track.id == track.id }
        val state = _items.value.firstOrNull { it.track.id == track.id }?.status
        if (existing != null && state != null && state != DownloadStatus.FAILED) return EnqueueResult.ALREADY

        val base = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: return EnqueueResult.NO_STORAGE
        if (existing != null) {
            try { dm.remove(existing.systemId) } catch (e: Exception) { /* already gone */ }
        }
        val fileName = fileNameFor(track)
        File(File(base, FOLDER), fileName).delete()

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(track.title)
            .setDescription(track.artist)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_MUSIC, "$FOLDER/$fileName")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(!wifiOnly)
            .setAllowedOverRoaming(false)
        val systemId = try {
            dm.enqueue(request)
        } catch (e: Exception) {
            return EnqueueResult.FAILED
        }
        val record = DownloadRecord(track.copy(downloadUrl = url), systemId, fileName, done = false)
        store.updateDownloads { list -> list.filterNot { it.track.id == track.id } + record }
        wake.trySend(Unit)
        return EnqueueResult.STARTED
    }

    /** Cancels a running download or deletes a finished one. */
    fun remove(trackId: String) {
        val rec = records.firstOrNull { it.track.id == trackId } ?: return
        deleteFiles(rec)
        scope.launch { store.updateDownloads { list -> list.filterNot { it.track.id == trackId } } }
    }

    fun removeAll() {
        records.forEach { deleteFiles(it) }
        scope.launch { store.updateDownloads { emptyList() } }
    }

    private fun deleteFiles(rec: DownloadRecord) {
        try { dm.remove(rec.systemId) } catch (e: Exception) { /* already gone */ }
        fileFor(rec)?.delete()
    }

    private fun fileFor(rec: DownloadRecord): File? {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: return null
        return File(File(base, FOLDER), rec.fileName)
    }

    private companion object {
        const val FOLDER = "Sonara"

        /** "jam:123" -> "jam_123.mp3" */
        fun fileNameFor(track: Track): String =
            track.id.replace(Regex("[^A-Za-z0-9]"), "_") + ".mp3"
    }
}
