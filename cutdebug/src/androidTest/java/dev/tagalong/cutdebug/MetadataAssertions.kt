package dev.tagalong.cutdebug

/**
 * Preservation = "no source tag lost" (D4). Added output tags (e.g. ffmpeg's `encoder`)
 * are allowed and not checked; only a source key that is missing, or present with a
 * different value, in the output counts as a failure — spec "Lossless cut preserves all
 * source file-level metadata".
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
