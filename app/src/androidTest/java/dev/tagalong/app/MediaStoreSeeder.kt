package dev.tagalong.app

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore

/**
 * Inserts and removes fixture videos from MediaStore for instrumented tests.
 * Uses [MediaStore.VOLUME_EXTERNAL_PRIMARY] so the picker sees items immediately
 * (ContentResolver.insert is synchronous — no media-scan delay).
 */
object MediaStoreSeeder {

    /**
     * Writes the named asset into [MediaStore.Video.Media] and returns the inserted content Uri.
     * Deletes any pre-existing entry with the same display name first so there is exactly one copy.
     *
     * @param context Used for [ContentResolver] operations (typically `targetContext`).
     * @param assetName Name of the asset file (e.g. `"xiaomi-poco-x5.mp4"`).
     * @param assetContext Context whose [android.content.res.AssetManager] holds the asset.
     *   Assets in `androidTest/assets/` live in the instrumentation APK, so pass
     *   `InstrumentationRegistry.getInstrumentation().context` here.
     *   Defaults to [context] when the asset and content resolver share the same APK.
     */
    fun insert(context: Context, assetName: String, assetContext: Context = context): Uri {
        // Clean up any stale copy from a previous run
        findByDisplayName(context, assetName)?.let { stale -> delete(context, stale) }

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, assetName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES)
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val collectionUri = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = context.contentResolver.insert(collectionUri, values)
            ?: error("MediaStore rejected insert for $assetName")

        context.contentResolver.openOutputStream(uri)?.use { out ->
            assetContext.assets.open(assetName).use { input -> input.copyTo(out) }
        } ?: error("Could not open output stream for $uri")

        val clearPending = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
        context.contentResolver.update(uri, clearPending, null, null)

        return uri
    }

    /** Deletes the given Uris from MediaStore (null entries are skipped). */
    fun delete(context: Context, vararg uris: Uri?) {
        for (uri in uris) {
            if (uri != null) context.contentResolver.delete(uri, null, null)
        }
    }

    /**
     * Returns the content Uri of the first video in MediaStore whose [MediaStore.Video.Media.DISPLAY_NAME]
     * equals [displayName], or null if not found.
     */
    fun findByDisplayName(context: Context, displayName: String): Uri? {
        val projection = arrayOf(MediaStore.Video.Media._ID)
        val selection = "${MediaStore.Video.Media.DISPLAY_NAME} = ?"
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            arrayOf(displayName),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
                return ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
            }
        }
        return null
    }

    /**
     * Returns the display name of the video at [uri], or null if the query fails.
     */
    fun getDisplayName(context: Context, uri: Uri): String? =
        context.contentResolver.query(
            uri,
            arrayOf(MediaStore.Video.Media.DISPLAY_NAME),
            null, null, null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0)?.takeIf { it.isNotBlank() } else null
        }

    /**
     * Returns the content Uri of the most recently added video whose
     * [MediaStore.Video.Media.DISPLAY_NAME] contains [nameSubstring] and whose
     * [MediaStore.Video.Media.DATE_ADDED] is > [afterEpochSeconds].
     *
     * Useful when the exact output filename is not known ahead of time (e.g. because the
     * picker URI returns a numeric ID rather than the source filename).
     */
    fun findRecentlyAdded(
        context: Context,
        afterEpochSeconds: Long,
        nameSubstring: String,
    ): Uri? {
        val projection = arrayOf(MediaStore.Video.Media._ID)
        val selection =
            "${MediaStore.Video.Media.DATE_ADDED} > ? AND " +
                "${MediaStore.Video.Media.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf(afterEpochSeconds.toString(), "%${nameSubstring}%")
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Video.Media.DATE_ADDED} DESC",
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
                return ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
            }
        }
        return null
    }
}
