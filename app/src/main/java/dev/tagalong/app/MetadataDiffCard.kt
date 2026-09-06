package dev.tagalong.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.tagalong.engine.MediaProbe
import java.time.Instant

/**
 * Presents every curated tag side-by-side (source vs cut output) so the user can verify
 * that metadata survived the cut unchanged.
 *
 * Curated rows: creation_time, location, rotation, video codec + dimensions, and every
 * tag key that starts with "com." — the same set as [ProbeCard].
 *
 * A summary banner at the top indicates whether all tags were preserved or some differ.
 * Rows where the output value is missing or changed are coloured distinctly.
 * The expandable section shows every remaining raw tag in both columns.
 */
@Composable
fun MetadataDiffCard(sourceProbe: MediaProbe, outputProbe: MediaProbe) {
    var expanded by remember { mutableStateOf(false) }

    // Merge tag maps for each probe; later maps overwrite earlier on key collision.
    fun mergedTags(probe: MediaProbe): Map<String, String> = buildMap {
        putAll(probe.formatTags)
        putAll(probe.videoStreamTags)
        putAll(probe.audioStreamTags)
    }

    val sourceTags = mergedTags(sourceProbe)
    val outputTags = mergedTags(outputProbe)

    // com.* tags from the source, sorted for stable display order.
    val comTagKeys = sourceTags.keys.filter { it.startsWith("com.") }.sorted()

    // Keys shown in the curated section — excluded from the overflow dump.
    val curatedKeys: Set<String> = setOf("creation_time", "location", "location-eng") + comTagKeys

    // Remaining keys: everything not in the curated set (union of source + output keys).
    val remainingKeys = (sourceTags.keys + outputTags.keys)
        .filterNot { it in curatedKeys }
        .distinct()
        .sorted()

    // --- curated row data ---

    data class DiffRowData(
        val key: String,
        val sourceValue: String,
        val outputValue: String,
        val changed: Boolean = outputValue != sourceValue,
    )

    fun creationTimeRow(): DiffRowData {
        val src = sourceTags["creation_time"]?.let { formatDiffCreationTime(it) } ?: "—"
        val out = outputTags["creation_time"]?.let { formatDiffCreationTime(it) } ?: "—"
        return DiffRowData("creation_time", src, out)
    }

    fun locationRow(): DiffRowData {
        val src = sourceTags["location"] ?: sourceTags["location-eng"] ?: "—"
        val out = outputTags["location"] ?: outputTags["location-eng"] ?: "—"
        return DiffRowData("location", src, out)
    }

    fun locationRepresentationRow(): DiffRowData {
        val src = sourceProbe.locationRepresentation
        val out = outputProbe.locationRepresentation
        val requiredRepresentationLost =
            (src.hasQuickTime &&
                (!out.hasQuickTime || !src.quickTimePayloadsEqual(out))) ||
                !out.genericMdtaKeys.containsAll(src.genericMdtaKeys)
        return DiffRowData(
            key = "location representation",
            sourceValue = src.representation.label,
            outputValue = out.representation.label,
            changed = requiredRepresentationLost,
        )
    }

    fun rotationRow(): DiffRowData {
        val src = sourceProbe.videoRotationDegrees?.let { "${it}°" } ?: "—"
        val out = outputProbe.videoRotationDegrees?.let { "${it}°" } ?: "—"
        return DiffRowData("rotation", src, out)
    }

    fun videoRow(): DiffRowData {
        fun codec(probe: MediaProbe) = buildString {
            append(probe.videoMime ?: "—")
            if (probe.videoWidth != null && probe.videoHeight != null) {
                append(" · ${probe.videoWidth}×${probe.videoHeight}")
            }
        }
        return DiffRowData("video", codec(sourceProbe), codec(outputProbe))
    }

    val curatedRows: List<DiffRowData> = buildList {
        add(creationTimeRow())
        add(locationRow())
        add(locationRepresentationRow())
        add(rotationRow())
        add(videoRow())
        comTagKeys.forEach { key ->
            add(DiffRowData(key, sourceTags[key] ?: "—", outputTags[key] ?: "—"))
        }
    }

    val remainingRows: List<DiffRowData> = remainingKeys.map { key ->
        val sourceValue = sourceTags[key]
        val outputValue = outputTags[key]
        DiffRowData(
            key = key,
            sourceValue = sourceValue ?: "—",
            outputValue = outputValue ?: "—",
            // Output-only tags such as FFmpeg's `encoder` tag are allowed additions;
            // only a missing or changed source tag is a preservation failure.
            changed = sourceValue != null && sourceValue != outputValue,
        )
    }

    val changedCount = (curatedRows + remainingRows).count { it.changed }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Summary banner
            val locationRepresentationChanged = curatedRows.any {
                it.key == "location representation" && it.changed
            }
            if (locationRepresentationChanged) {
                Text(
                    text = "⚠  Location representation changed; gallery compatibility may be affected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            val bannerText = if (changedCount == 0) {
                "✓  All ${curatedRows.size + remainingRows.size} tags preserved"
            } else {
                "⚠  $changedCount tag${if (changedCount == 1) "" else "s"} missing or changed"
            }
            val bannerColor = if (changedCount == 0) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            }
            Text(
                text = bannerText,
                style = MaterialTheme.typography.titleSmall,
                color = bannerColor,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

            // Column header row
            DiffHeaderRow()

            HorizontalDivider(modifier = Modifier.padding(vertical = 1.dp))

            // Curated rows
            curatedRows.forEach { row -> DiffRow(row.key, row.sourceValue, row.outputValue, row.changed) }

            // Overflow expand/collapse
            if (remainingRows.isNotEmpty()) {
                val overflowChanged = remainingRows.count { it.changed }
                val overflowLabel = if (expanded) {
                    "▲ hide ${remainingRows.size} tags"
                } else {
                    val changedNote = if (overflowChanged > 0) " — $overflowChanged changed" else " — all preserved"
                    "▼ ${remainingRows.size} more tags$changedNote"
                }
                TextButton(
                    onClick = { expanded = !expanded },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(
                        text = overflowLabel,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                if (expanded) {
                    remainingRows.forEach { row ->
                        DiffRow(row.key, row.sourceValue, row.outputValue, row.changed)
                    }
                }
            }
        }
    }
}

@Composable
private fun DiffHeaderRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "Tag",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1.2f),
        )
        Text(
            text = "Source",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1.5f),
        )
        Text(
            text = "Cut output",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1.5f),
        )
    }
}

@Composable
private fun DiffRow(key: String, sourceValue: String, outputValue: String, changed: Boolean) {
    val outputColor: Color = if (changed) MaterialTheme.colorScheme.error else Color.Unspecified
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = key,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1.2f),
        )
        Text(
            text = sourceValue,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1.5f),
        )
        Text(
            text = outputValue,
            style = MaterialTheme.typography.bodySmall,
            color = outputColor,
            modifier = Modifier.weight(1.5f),
        )
    }
}

private fun formatDiffCreationTime(raw: String): String = try {
    Instant.parse(raw).toString().replace('T', ' ').substringBefore('.')
} catch (_: Exception) {
    raw
}
