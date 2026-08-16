package dev.tagalong.engine

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

object TestFixtures {
    const val ASSET_NAME = "xiaomi-poco-x5.mp4"

    fun appContext(): Context = InstrumentationRegistry.getInstrumentation().targetContext

    /** Copies the bundled fixture asset to a real filesystem path (assets can't be opened by path). */
    fun sourceFile(context: Context = appContext()): File {
        val dest = File(context.cacheDir, ASSET_NAME)
        if (!dest.exists() || dest.length() == 0L) {
            context.assets.open(ASSET_NAME).use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return dest
    }

    fun outputFile(context: Context = appContext(), name: String): File {
        val dir = File(context.cacheDir, "cutdebug-out").apply { mkdirs() }
        return File(dir, name)
    }
}
