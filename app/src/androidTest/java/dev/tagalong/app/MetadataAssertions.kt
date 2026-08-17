package dev.tagalong.app

/**
 * Local copy of the engine's MetadataAssertions for use in app instrumented tests.
 * The original lives in engine's androidTest source set and is not accessible as a
 * library artifact; this copy keeps the same API so test code reads identically.
 *
 * Preservation rule: all source tags must appear in the output unchanged.
 * Output-only tags (e.g. ffmpeg's `encoder`) are allowed and are not checked.
 */
object MetadataAssertions {

    data class TagDiff(
        val missing: Map<String, String>,
        val changed: Map<String, Pair<String, String>>,
    ) {
        val isSubset: Boolean get() = missing.isEmpty() && changed.isEmpty()
    }

    fun sourceTagsSubsetOfOutput(source: Map<String, String>, output: Map<String, String>): TagDiff {
        val missing = LinkedHashMap<String, String>()
        val changed = LinkedHashMap<String, Pair<String, String>>()
        for ((key, sourceValue) in source) {
            val outputValue = output[key]
            when {
                outputValue == null -> missing[key] = sourceValue
                outputValue != sourceValue -> changed[key] = sourceValue to outputValue
            }
        }
        return TagDiff(missing, changed)
    }
}
