package dev.tagalong.engine

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Returned by [DateTakenStore.registerAndReadBack] — carries the confirmed gallery date
 * and the absolute storage path of the newly-registered file (may be null if the legacy
 * DATA column is not populated on this device).
 */
data class SaveResult(
    val dateTakenMillis: Long?,
    val absolutePath: String?,
)

/**
 * Writes a cut output into MediaStore with `DATE_TAKEN` set to the source's original
 * capture date, then reads it back after a scan — spec "Gallery date is preserved" (task 2.4).
 */
object DateTakenStore {
    private const val TAG = "DateTakenStore"

    /**
     * @param relativePath Gallery folder the output is registered under. Defaults to the
     * app's real save location; callers (e.g. the frozen `:cutdebug` bake-off harness) may
     * still pass their own.
     * @param displayName MediaStore file name. Defaults to a collision-proof name so
     * repeated test runs don't clash; a real save should pass something user-meaningful.
     */
    fun registerAndReadBack(
        context: Context,
        file: File,
        captureTimeMillis: Long,
        relativePath: String = "Movies/Tagalong",
        displayName: String = "${System.nanoTime()}-${file.name}",
    ): SaveResult {
        val resolver = context.contentResolver
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        val insertValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.DATE_TAKEN, captureTimeMillis)
            put(MediaStore.Video.Media.RELATIVE_PATH, relativePath)
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, insertValues) ?: error("MediaStore insert failed for ${file.name}")

        resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            ?: error("Could not open output stream for $uri")

        // Committing IS_PENDING=0 is what makes MediaProvider index/scan the row on API 29+.
        val commitValues = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
        resolver.update(uri, commitValues, null, null)

        // Construct the absolute path from the known parameters rather than querying the
        // DATA column — DATA can be null under scoped storage even for app-inserted rows
        // on API 30+ emulators. The primary external storage root is stable on all Android
        // devices targeted by this app (minSdk 31).
        val absolutePath = Environment.getExternalStorageDirectory().absolutePath +
            "/" + relativePath.trimEnd('/') + "/" + displayName

        rescanBestEffort(context, uri)

        return SaveResult(
            dateTakenMillis = readDateTaken(context, uri),
            absolutePath = absolutePath,
        )
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
