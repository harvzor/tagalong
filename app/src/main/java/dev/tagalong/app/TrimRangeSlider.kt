package dev.tagalong.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay

/** Which trim handle's precision panel is currently open (design D2). */
sealed interface EditTarget {
    object Start : EditTarget
    object End : EditTarget
}

/**
 * Material3 `RangeSlider` over the clip's duration. Dragging a handle seeks the preview to it;
 * seeks are debounced so a fast drag doesn't flood the player (design D5).
 *
 * Below the slider: a compact Start | Length | End chip row. Tapping a chip expands an inline
 * precision panel (no modal overlay) with four nudge buttons and an `M:SS.t` text field.
 * Nudge applies immediately; the text field commits on IME Done (design D1 revised).
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
    // Debounced seek for slider drag (design D5 — nudge bypasses this path)
    var seekTargetMs by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(seekTargetMs) {
        val target = seekTargetMs ?: return@LaunchedEffect
        delay(50)
        player.seekTo(target)
    }

    // Which handle's panel is open; null = collapsed (design D2)
    var editTarget by remember { mutableStateOf<EditTarget?>(null) }

    // Text field value for the open panel
    var editText by remember { mutableStateOf("") }
    // committed flag: set before IME Done fires so onFocusChanged doesn't revert (design D6)
    var committed by remember { mutableStateOf(false) }

    // Nudge: apply immediately, update the text field to reflect the new value (design D4, D5)
    fun nudge(delta: Long) {
        val newMs = when (editTarget) {
            is EditTarget.Start -> (startMs + delta).coerceIn(0L, endMs - 100L)
            is EditTarget.End   -> (endMs   + delta).coerceIn(startMs + 100L, durationMs)
            null                -> return
        }
        player.seekTo(newMs)
        when (editTarget) {
            is EditTarget.Start -> onRangeChanged(newMs, endMs)
            is EditTarget.End   -> onRangeChanged(startMs, newMs)
            null -> {}
        }
        editText = formatMmSsTenths(newMs)
    }

    // Validation for the text field (design D4)
    val parseResult = parseMmSsTenths(editText)
    val isError = when {
        editTarget == null  -> false
        parseResult == null -> true
        editTarget is EditTarget.Start && parseResult !in 0 until endMs         -> true
        editTarget is EditTarget.End   && parseResult !in (startMs + 1)..durationMs -> true
        else -> false
    }

    // Commit the typed text field value if valid (design D4, D6)
    fun commitText() {
        if (isError || parseResult == null) return
        committed = true
        player.seekTo(parseResult)
        when (editTarget) {
            is EditTarget.Start -> onRangeChanged(parseResult, endMs)
            is EditTarget.End   -> onRangeChanged(startMs, parseResult)
            null -> {}
        }
        editText = formatMmSsTenths(parseResult) // normalise display
    }

    Column(modifier = modifier) {
        RangeSlider(
            value = startMs.toFloat()..endMs.toFloat(),
            valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
            onValueChange = { range ->
                val newStart = range.start.toLong()
                val newEnd = range.endInclusive.toLong()
                seekTargetMs = if (newStart != startMs) newStart else newEnd
                onRangeChanged(newStart, newEnd)
            },
        )

        // Compact three-column chip row: Start | Length | End (design D1 revised)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    text = "Start",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilterChip(
                    selected = editTarget is EditTarget.Start,
                    onClick = {
                        if (editTarget is EditTarget.Start) {
                            editTarget = null
                        } else {
                            editTarget = EditTarget.Start
                            editText = formatMmSsTenths(startMs)
                        }
                    },
                    label = { Text(formatMmSsTenths(startMs), style = MaterialTheme.typography.bodyMedium) },
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Length",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatMmSsTenths(endMs - startMs),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "End",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilterChip(
                    selected = editTarget is EditTarget.End,
                    onClick = {
                        if (editTarget is EditTarget.End) {
                            editTarget = null
                        } else {
                            editTarget = EditTarget.End
                            editText = formatMmSsTenths(endMs)
                        }
                    },
                    label = { Text(formatMmSsTenths(endMs), style = MaterialTheme.typography.bodyMedium) },
                )
            }
        }

        // Inline precision panel — animates in below the chip row, no modal overlay (design D1)
        AnimatedVisibility(
            visible = editTarget != null,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HorizontalDivider()

                // Label: which handle is being edited
                Text(
                    text = if (editTarget is EditTarget.Start) "Adjusting Start" else "Adjusting End",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )

                // Nudge buttons — apply immediately so the slider and preview update live
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { nudge(-1000L) }) { Text("−1s") }
                    TextButton(onClick = { nudge(-100L) })  { Text("−0.1s") }
                    TextButton(onClick = { nudge(+100L) })  { Text("+0.1s") }
                    TextButton(onClick = { nudge(+1000L) }) { Text("+1s") }
                }

                // Text field for direct M:SS.t entry — tap to focus; no auto-focus so the
                // keyboard does not appear automatically when the panel opens (design D6)
                OutlinedTextField(
                    value = editText,
                    onValueChange = { newText ->
                        editText = newText
                        // Seek for live preview while typing (only when format is valid)
                        parseMmSsTenths(newText)?.let { player.seekTo(it) }
                    },
                    isError = isError,
                    singleLine = true,
                    label = { Text("M:SS.t — tap to type an exact time") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { commitText() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            if (!focusState.isFocused) {
                                // Revert to the handle's current value unless this was a
                                // voluntary commit via IME Done (design D6)
                                if (!committed) {
                                    editText = when (editTarget) {
                                        is EditTarget.Start -> formatMmSsTenths(startMs)
                                        is EditTarget.End   -> formatMmSsTenths(endMs)
                                        null -> editText
                                    }
                                }
                                committed = false
                            }
                        },
                )
            }
        }
    }
}
