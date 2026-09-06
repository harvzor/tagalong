package dev.tagalong.engine

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Locale

object TestFixtures {
    private val supportedExtensions = setOf("mp4", "mov", "m4v", "3gp", "webm", "mkv")

    data class SampleVideo(val fileName: String) {
        val stem: String
            get() = fileName.substringBeforeLast('.', fileName)
    }

    fun appContext(): Context = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * Discovers the files packaged from the repository's sample-videos/ directory.
     * Asset names are sorted and validated so a new sample enters the matrix without a
     * test-source edit, while ambiguous picker identities fail before any cut runs.
     */
    fun samples(context: Context = appContext()): List<SampleVideo> {
        val names = context.assets.list("").orEmpty()
            .filter { name ->
                name.substringAfterLast('.', "").lowercase(Locale.ROOT) in supportedExtensions
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })

        require(names.isNotEmpty()) {
            "No supported sample videos found in packaged androidTest assets; expected files from sample-videos/."
        }

        val duplicateNames = names.groupBy { it.lowercase(Locale.ROOT) }
            .filterValues { it.size > 1 }
            .keys
        require(duplicateNames.isEmpty()) {
            "Sample-video filenames must be unique case-insensitively; duplicates: ${duplicateNames.sorted()}"
        }

        val ambiguousStems = names.groupBy {
            it.substringBeforeLast('.', it).lowercase(Locale.ROOT)
        }.filterValues { it.size > 1 }
        require(ambiguousStems.isEmpty()) {
            "Sample-video stems must be unique for diagnostics and picker selection; ambiguous samples: " +
                ambiguousStems.values.flatten().sorted()
        }

        return names.map(::SampleVideo)
    }

    /** Copies one packaged sample to an identity-specific filesystem path. */
    fun sourceFile(sample: SampleVideo, context: Context = appContext()): File {
        val dest = File(File(context.cacheDir, "sample-videos").apply { mkdirs() }, sample.fileName)
        if (!dest.exists() || dest.length() == 0L) {
            context.assets.open(sample.fileName).use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return dest
    }

    /** Returns an identity- and mode-specific output path, preventing cross-sample reuse. */
    fun outputFile(
        sample: SampleVideo,
        mode: CutMode,
        engineName: String,
        context: Context = appContext(),
    ): File {
        val dir = File(context.cacheDir, "cutdebug-out/${sample.stem}").apply { mkdirs() }
        return File(dir, "${sample.fileName}-${engineName}-${mode.name.lowercase(Locale.ROOT)}.mp4")
    }
}
