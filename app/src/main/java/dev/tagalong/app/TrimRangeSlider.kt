package dev.tagalong.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay

/**
 * Material3 `RangeSlider` over the clip's duration (design D3). Dragging a handle seeks the
 * preview to it; seeks are debounced so a fast drag doesn't flood the player.
 */
@Composable
fun TrimRangeSlider(
    durationMs: Long,
    startMs: Long,
    endMs: Long,
    player: ExoPlayer,
    onRangeChanged: (startMs: Long, endMs: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var seekTargetMs by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(seekTargetMs) {
        val target = seekTargetMs ?: return@LaunchedEffect
        delay(50)
        player.seekTo(target)
    }

    Column(modifier = modifier) {
        RangeSlider(
            value = startMs.toFloat()..endMs.toFloat(),
            valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
            onValueChange = { range ->
                val newStart = range.start.toLong()
                val newEnd = range.endInclusive.toLong()
                // Diff against the current range to tell which thumb moved (design D3 risk).
                seekTargetMs = if (newStart != startMs) newStart else newEnd
                onRangeChanged(newStart, newEnd)
            },
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Start ${formatMmSsTenths(startMs)}", style = MaterialTheme.typography.bodySmall)
            Text("Length ${formatMmSsTenths(endMs - startMs)}", style = MaterialTheme.typography.bodySmall)
            Text("End ${formatMmSsTenths(endMs)}", style = MaterialTheme.typography.bodySmall)
        }
    }
}
