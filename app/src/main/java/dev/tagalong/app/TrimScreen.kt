package dev.tagalong.app

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun TrimScreen(navController: NavController, viewModel: CutViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val cutState = uiState.cutState

    // Guard: if source is null (e.g. after process-death restore of a picked session), pop
    // back to home so the user can pick again. On a share-launched session Trim IS the start
    // destination and no "home" entry exists to pop to — popBackStack reports failure and the
    // busy state below carries the screen until the intake copy/probe lands (design D2/D3).
    val source = uiState.source
    LaunchedEffect(source) {
        if (source == null) {
            navController.popBackStack("home", inclusive = false)
        }
    }

    if (source == null) {
        // Busy placeholder: shown while a share intake materialises (Trim is the start
        // destination, Home is never displayed), and for the single frame of a guard-pop.
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val player = rememberVideoPlayer(source.file)

    // Single Trim exit, shared by the visible arrow and the system back gesture (design D1/D2).
    //
    // Pause first: rememberVideoPlayer only pauses on ON_STOP, which fires after the slide
    // transition completes — late enough for a still-playing SurfaceView to trail across the
    // screen. Pausing at the call site puts the stop ahead of the animation.
    //
    // A plain pop covers both launch modes: an in-app pick (home → trim) pops to Home, while a
    // share-launched session has no Home on the stack, so popBackStack returns false and
    // finish() returns to the sending app — the exit home-screen's "Back from Trim after a
    // share" scenario requires. finish() is required rather than relying on popBackStack alone:
    // BackHandler consumes the gesture, so at a share root a no-op pop would swallow the press
    // and never run the default start-destination exit that this branch now performs.
    val activity = LocalContext.current as? Activity
    val onBack: () -> Unit = {
        player.pause()
        if (!navController.popBackStack()) activity?.finish()
    }
    BackHandler(onBack = onBack)

    // Navigate to the result screen as soon as the cut succeeds. ResultScreen calls
    // viewModel.resetCutState() before popping back, so this LaunchedEffect will not
    // re-trigger when returning to this screen (cutState will be Idle again).
    //
    // Declared below the player deliberately: the preview has to stop before the transition to
    // ResultScreen begins, not when this screen's lifecycle is downgraded afterwards — the
    // outgoing entry's ON_STOP only fires once the transition animation has completed, which is
    // late enough for a still-playing SurfaceView to trail across the screen. CutState.Saved
    // always implies a non-null source, so living behind the null-source guard above does not
    // change when this effect is able to fire.
    LaunchedEffect(cutState) {
        if (cutState is CutState.Saved) {
            player.pause()
            navController.navigate("result") { launchSingleTop = true }
        }
    }
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

    // Opaque background: see the same note in HomeScreen.kt. Must precede windowInsetsPadding
    // so the strips under the status and nav bars are painted during a screen transition.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                // Optical alignment, not geometric. The root Column pads 16dp, and IconButton
                // insets its 24dp glyph ~12dp inside a 48dp target, so -12dp is what puts the
                // glyph's *box* on the 16dp content edge. But the arrow_back path's leftmost
                // point is x=4 in its own 24dp viewport, so -12dp leaves the visible arrowhead
                // starting 4dp right of the text below it. -16dp brings the tip itself onto the
                // content edge. Touch target stays 12dp clear of the screen edge.
                modifier = Modifier.offset(x = (-16).dp),
                onClick = onBack,
            ) {
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
