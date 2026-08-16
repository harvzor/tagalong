package dev.tagalong.cutdebug

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Writes a cut output into MediaStore with `DATE_TAKEN` set to the source's original
 * capture date, then reads it back after a scan — spec "Gallery date is preserved" (task 2.4).
 */
object DateTakenStore {
    private const val TAG = "DateTakenStore"

    fun registerAndReadBack(context: Context, file: File, captureTimeMillis: Long): Long? {
        val resolver = context.contentResolver
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        val insertValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "${System.nanoTime()}-${file.name}")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.DATE_TAKEN, captureTimeMillis)
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CutDebug")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, insertValues) ?: error("MediaStore insert failed for ${file.name}")

        resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            ?: error("Could not open output stream for $uri")

        // Committing IS_PENDING=0 is what makes MediaProvider index/scan the row on API 29+.
        val commitValues = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
        resolver.update(uri, commitValues, null, null)

        rescanBestEffort(context, uri)

        return readDateTaken(context, uri)
    }

    /** Best-effort explicit rescan via the legacy DATA path, to exercise "after the file is
     * scanned by the media scanner" per the spec scenario. Non-fatal: DATA is deprecated
     * under scoped storage and may not resolve; the definitive check is the read-back below. */
    private fun rescanBestEffort(context: Context, uri: Uri) {
        val path = runCatching { legacyPathFor(context, uri) }.getOrNull() ?: return
        val latch = CountDownLatch(1)
        runCatching {
            MediaScannerConnection.scanFile(context, arrayOf(path), arrayOf("video/mp4")) { _, _ -> latch.countDown() }
        }.onFailure { Log.w(TAG, "rescan skipped for $uri", it) }
        latch.await(15, TimeUnit.SECONDS)
    }

    private fun legacyPathFor(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(MediaStore.Video.Media.DATA), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                if (idx >= 0) return cursor.getString(idx)
            }
        }
        return null
    }

    private fun readDateTaken(context: Context, uri: Uri): Long? {
        context.contentResolver.query(uri, arrayOf(MediaStore.Video.Media.DATE_TAKEN), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(MediaStore.Video.Media.DATE_TAKEN)
                if (idx >= 0 && !cursor.isNull(idx)) return cursor.getLong(idx)
            }
        }
        return null
    }
}
