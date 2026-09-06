package dev.tagalong.app

import android.content.Context
import java.io.File
import java.util.Locale

/** Discovers and materializes the sample videos packaged in the instrumentation APK. */
object TestSamples {
    private val supportedExtensions = setOf("mp4", "mov", "m4v", "3gp", "webm", "mkv")

    data class SampleVideo(val fileName: String) {
        val stem: String
            get() = fileName.substringBeforeLast('.', fileName)
    }

    fun discover(assetContext: Context): List<SampleVideo> {
        val names = assetContext.assets.list("").orEmpty()
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
            "Sample-video stems must be unique for picker selection; ambiguous samples: " +
                ambiguousStems.values.flatten().sorted()
        }

        return names.map(::SampleVideo)
    }

    /** Copies one selected sample from the instrumentation APK to a unique cache path. */
    fun materialize(
        context: Context,
        assetContext: Context,
        sample: SampleVideo,
    ): File {
        val file = File(File(context.cacheDir, "e2e-samples").apply { mkdirs() }, sample.fileName)
        assetContext.assets.open(sample.fileName).use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        return file
    }

    fun deleteMaterialized(context: Context, sample: SampleVideo) {
        File(context.cacheDir, "e2e-samples/${sample.fileName}").delete()
        File(context.cacheDir, "probe-output-${sample.fileName}").delete()
    }
}
