package dev.tagalong.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun TrimScreen(navController: NavController, viewModel: CutViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val cutState = uiState.cutState

    // Guard: if source is null (e.g. after process-death restore), pop back to home so the
    // user can pick again. Under normal navigation source is always non-null here.
    val source = uiState.source
    LaunchedEffect(source) {
        if (source == null) {
            navController.popBackStack("home", inclusive = false)
        }
    }

    // Navigate to the result screen as soon as the cut succeeds. ResultScreen calls
    // viewModel.resetCutState() before popping back, so this LaunchedEffect will not
    // re-trigger when returning to this screen (cutState will be Idle again).
    LaunchedEffect(cutState) {
        if (cutState is CutState.Saved) {
            navController.navigate("result") { launchSingleTop = true }
        }
    }

    if (source == null) return  // render nothing while the guard effect fires

    val player = rememberVideoPlayer(source.file)
    // Stop playback when the playhead reaches the trim end point (design D2). The effect
    // key is endMs only — any change to the trim end restarts the coroutine so the new
    // boundary is picked up immediately. Polling at 100 ms keeps CPU impact negligible
    // while limiting overshoot to ~one poll interval.
    LaunchedEffect(uiState.endMs) {
        while (true) {
            if (player.isPlaying && player.currentPosition >= uiState.endMs) {
                player.pause()
            }
            delay(100)
        }
    }
    // When play starts from before the trim in-point, snap to startMs so pressing
    // play always begins the preview at the chosen cut point rather than wherever
    // the playhead happens to be. Seeking within [startMs, endMs] is unaffected.
    val startMs = uiState.startMs
    val endMs = uiState.endMs
    DisposableEffect(player, startMs, endMs) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // Snap to startMs if the playhead is outside [startMs, endMs] when
                // play is pressed — covers both fresh videos (position 0 < startMs)
                // and replay after the trim end stop (position >= endMs).
                if (isPlaying && (player.currentPosition < startMs || player.currentPosition >= endMs)) {
                    player.seekTo(startMs)
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { navController.popBackStack("home", inclusive = false) }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to home",
                )
            }
            Text(
                text = "Trim",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        // Scrollable content — probe card below controls doesn't overflow the screen (design D5).
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Absolute path label — falls back to gallery-relative path, then filename
            // only, when the absolute path could not be resolved (spec cut-workflow; design D2).
            Text(
                text = source.absolutePath ?: source.displayPath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Full-width preview; height capped so portrait clips don't push controls
            // off-screen (design D6). PlayerView honours the aspect ratio once decoded.
            VideoPreview(
                player = player,
                modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
            )
            TrimRangeSlider(
                durationMs = source.durationMs,
                startMs = uiState.startMs,
                endMs = uiState.endMs,
                player = player,
                onRangeChanged = viewModel::onRangeChanged,
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { viewModel.runCut() },
                enabled = cutState != CutState.Working,
            ) {
                Text("Cut and save")
            }
            // Demoted to OutlinedButton so "Cut and save" has clear visual priority (design D4).
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { navController.popBackStack("home", inclusive = false) },
            ) {
                Text("Pick a different video")
            }
            // Source probe card — reference while trimming (probe-viewer spec).
            // Output metadata is shown on the ResultScreen after a successful cut.
            uiState.sourceProbe?.let { ProbeCard("Source", it) }
        }

        TrimCutStateStatus(cutState)
    }
}

/** Status row shown at the bottom of the trim screen. "Saved" is not shown here — navigation
 *  to ResultScreen fires immediately on that transition. */
@Composable
private fun TrimCutStateStatus(cutState: CutState) {
    when (cutState) {
        is CutState.Idle, is CutState.Saved -> Unit
        is CutState.Working -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator()
            Text("Cutting…")
        }
        is CutState.Error -> Text(
            text = "Failed: ${cutState.message}",
            color = MaterialTheme.colorScheme.error,
        )
    }
}
