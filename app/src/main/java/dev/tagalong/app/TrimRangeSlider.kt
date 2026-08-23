package dev.tagalong.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay

/** Which trim handle is currently open in the nudge/edit dialog (design D2). */
sealed interface EditTarget {
    object Start : EditTarget
    object End : EditTarget
}

/**
 * Material3 `RangeSlider` over the clip's duration. Dragging a handle seeks the preview to it;
 * seeks are debounced so a fast drag doesn't flood the player (design D5).
 *
 * Below the slider the Start and End times are shown in a compact three-column row (Start |
 * Length | End). Tapping Start or End opens a focused dialog with four nudge buttons and an
 * editable `M:SS.t` field — nudge seeks the player for live preview; changes apply on OK only
 * (design D1 revised).
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
    // Debounced seek for slider drag (design D5 — dialog nudge bypasses this path)
    var seekTargetMs by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(seekTargetMs) {
        val target = seekTargetMs ?: return@LaunchedEffect
        delay(50)
        player.seekTo(target)
    }

    // Dialog state (design D2)
    var editTarget by remember { mutableStateOf<EditTarget?>(null) }
    var dialogText by remember { mutableStateOf("") }

    // Auto-focus the text field when the dialog opens (design D6)
    val dialogFocusRequester = remember { FocusRequester() }
    LaunchedEffect(editTarget) {
        if (editTarget != null) {
            try { dialogFocusRequester.requestFocus() } catch (_: Exception) { /* not yet composed */ }
        }
    }

    // Commits the current dialogText if valid; otherwise leaves the dialog open (design D4)
    fun commitDialog() {
        val parsed = parseMmSsTenths(dialogText) ?: return
        val valid = when (editTarget) {
            is EditTarget.Start -> parsed in 0 until endMs
            is EditTarget.End   -> parsed in (startMs + 1)..durationMs
            null                -> false
        }
        if (!valid) return
        when (editTarget) {
            is EditTarget.Start -> { player.seekTo(parsed); onRangeChanged(parsed, endMs) }
            is EditTarget.End   -> { player.seekTo(parsed); onRangeChanged(startMs, parsed) }
            null -> {}
        }
        editTarget = null
    }

    // Reverts the player to the handle's original position and closes the dialog (design D6)
    fun dismissDialog() {
        player.seekTo(if (editTarget is EditTarget.Start) startMs else endMs)
        editTarget = null
    }

    // Nudge the handle by delta ms; updates dialogText and seeks for live preview (design D4, D5)
    fun nudgeDialog(delta: Long) {
        val current = parseMmSsTenths(dialogText) ?: return  // no-op if text is unparseable
        val next = when (editTarget) {
            is EditTarget.Start -> (current + delta).coerceIn(0L, endMs - 100L)
            is EditTarget.End   -> (current + delta).coerceIn(startMs + 100L, durationMs)
            null                -> return
        }
        dialogText = formatMmSsTenths(next)
        player.seekTo(next)
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

        // Compact three-column row: Start | Length | End (design D1 revised)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Start — chip signals tappable
            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    text = "Start",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SuggestionChip(
                    onClick = {
                        editTarget = EditTarget.Start
                        dialogText = formatMmSsTenths(startMs)
                    },
                    label = {
                        Text(
                            text = formatMmSsTenths(startMs),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                )
            }

            // Length — derived, non-interactive
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Length",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatMmSsTenths(endMs - startMs),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp), // align vertically with chips
                )
            }

            // End — chip signals tappable
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "End",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SuggestionChip(
                    onClick = {
                        editTarget = EditTarget.End
                        dialogText = formatMmSsTenths(endMs)
                    },
                    label = {
                        Text(
                            text = formatMmSsTenths(endMs),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                )
            }
        }
    }

    // Nudge / edit dialog (design D1 revised, D4, D5, D6)
    if (editTarget != null) {
        val title = if (editTarget is EditTarget.Start) "Start" else "End"

        // Derived validation for the dialog text field (design D4)
        val parseResult = parseMmSsTenths(dialogText)
        val isError = when {
            parseResult == null -> true
            editTarget is EditTarget.Start && parseResult !in 0 until endMs -> true
            editTarget is EditTarget.End && parseResult !in (startMs + 1)..durationMs -> true
            else -> false
        }

        AlertDialog(
            onDismissRequest = { dismissDialog() },
            title = { Text(title) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = dialogText,
                        onValueChange = { newText ->
                            dialogText = newText
                            // Seek for live preview while typing (design D5)
                            parseMmSsTenths(newText)?.let { player.seekTo(it) }
                        },
                        isError = isError,
                        singleLine = true,
                        label = { Text("M:SS.t") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { commitDialog() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(dialogFocusRequester),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        TextButton(onClick = { nudgeDialog(-1000L) }) { Text("−1s") }
                        TextButton(onClick = { nudgeDialog(-100L) }) { Text("−0.1s") }
                        TextButton(onClick = { nudgeDialog(+100L) }) { Text("+0.1s") }
                        TextButton(onClick = { nudgeDialog(+1000L) }) { Text("+1s") }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { commitDialog() },
                    enabled = !isError,
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { dismissDialog() }) { Text("Cancel") }
            },
        )
    }
}
