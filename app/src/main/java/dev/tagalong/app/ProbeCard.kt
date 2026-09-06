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
import androidx.compose.ui.unit.dp
import dev.tagalong.engine.MediaProbe
import java.time.Instant

/**
 * Displays a [MediaProbe] as a labelled card with a curated summary and an
 * expandable section for all remaining raw tags.
 *
 * Curated rows: creation_time, location tags, rotation, video codec + dimensions,
 * and every tag key that starts with "com." (across format + stream maps).
 * The expandable section shows every other key-value pair sorted alphabetically.
 */
@Composable
fun ProbeCard(label: String, probe: MediaProbe) {
    var expanded by remember { mutableStateOf(false) }

    // Merge all three tag maps; later maps overwrite earlier on key collision.
    val allTags: Map<String, String> = buildMap {
        putAll(probe.formatTags)
        putAll(probe.videoStreamTags)
        putAll(probe.audioStreamTags)
    }

    // com.* tags sorted for stable display order.
    val comTags: Map<String, String> = allTags.filterKeys { it.startsWith("com.") }.toSortedMap()

    // Keys already shown in the curated section — excluded from the raw dump.
    val curatedKeys: Set<String> = setOf("creation_time", "location", "location-eng") + comTags.keys

    // Remaining tags: everything not in the curated set, sorted alphabetically.
    val remainingTags: Map<String, String> = allTags.filterKeys { it !in curatedKeys }.toSortedMap()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

            // creation_time
            ProbeRow(
                key = "creation_time",
                value = allTags["creation_time"]?.let { formatCreationTime(it) } ?: "—",
            )

            // FFprobe normalizes several physical MP4 representations to this logical value.
            val locationValue = allTags["location"] ?: allTags["location-eng"]
            ProbeRow(key = "location", value = locationValue ?: "—")
            ProbeRow(
                key = "location representation",
                value = probe.locationRepresentation.representation.label,
            )

            // rotation
            ProbeRow(
                key = "rotation",
                value = probe.videoRotationDegrees?.let { "${it}°" } ?: "—",
            )

            // video codec + dimensions
            val codec = buildString {
                append(probe.videoMime ?: "—")
                if (probe.videoWidth != null && probe.videoHeight != null) {
                    append(" · ${probe.videoWidth}×${probe.videoHeight}")
                }
            }
            ProbeRow(key = "video", value = codec)

            // com.* tags (covers device-specific and Android metadata tags)
            comTags.forEach { (key, value) -> ProbeRow(key, value) }

            // Expand / collapse remaining raw tags
            if (remainingTags.isNotEmpty()) {
                TextButton(
                    onClick = { expanded = !expanded },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(
                        text = if (expanded) "▲ hide ${remainingTags.size} tags"
                               else "▼ ${remainingTags.size} more tags",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                if (expanded) {
                    remainingTags.forEach { (key, value) -> ProbeRow(key, value) }
                }
            }
        }
    }
}

@Composable
private fun ProbeRow(key: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "$key:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun formatCreationTime(raw: String): String = try {
    // "2024-03-15T10:30:22.000000Z" → "2024-03-15 10:30:22"
    Instant.parse(raw).toString().replace('T', ' ').substringBefore('.')
} catch (_: Exception) {
    raw
}
