package dev.tagalong.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController

@Composable
fun TrimScreen(navController: NavController, viewModel: CutViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val cutState = uiState.cutState

    // Navigate to the result screen as soon as the cut succeeds. ResultScreen calls
    // viewModel.resetCutState() before popping back, so this LaunchedEffect will not
    // re-trigger when returning to this screen (cutState will be Idle again).
    LaunchedEffect(cutState) {
        if (cutState is CutState.Saved) {
            navController.navigate("result") { launchSingleTop = true }
        }
    }

    // WHY OpenDocument AND NOT PickVisualMedia:
    // This app's core guarantee is lossless metadata preservation — every container tag in
    // the source (GPS location, creation_time, device make/model, brand-specific Xiaomi tags)
    // must survive the cut unchanged.  The Android Photo Picker (PickVisualMedia / ACTION_PICK)
    // makes this guarantee impossible to fulfil:
    //
    //   1. GPS tags — The Google Photo Picker module (com.google.android.providers.media.module)
    //      strips location tags from the openInputStream byte stream regardless of whether
    //      ACCESS_MEDIA_LOCATION is declared or granted at runtime (verified on-device,
    //      2026-08-23: granting the permission via RequestPermission before launching
    //      PickVisualMedia still produced output with no location tag).  There is no public
    //      API to request unredacted stream access through the Photo Picker path.
    //
    //   2. Real filename — DISPLAY_NAME is replaced with the picker's internal numeric ID
    //      (e.g. "1000000072"), so the output file inherits a meaningless name.
    //
    //   3. Gallery path — RELATIVE_PATH is nulled out, breaking the path label in the UI.
    //
    // ACTION_OPEN_DOCUMENT is the standard Android mechanism for granting an app direct,
    // persistent, unredacted access to a specific file the user explicitly selects.  The app
    // accesses only that single file; it does not request broad media access.  This is the
    // narrowest permission model that satisfies the app's metadata-preservation contract.
    //
    // WHY ACCESS_MEDIA_LOCATION IS ALSO REQUIRED:
    // Even with ACTION_OPEN_DOCUMENT, Android's media framework strips GPS location tags from
    // ContentResolver.openInputStream unless the calling app holds ACCESS_MEDIA_LOCATION.  The
    // stripping happens at the MediaDocumentsProvider stream level — it is not unique to the
    // Photo Picker path.  Holding this permission causes the framework to deliver the raw,
    // unredacted bytes so that location tags reach the cut engine and are copied through to
    // the output automatically.  The permission is requested just before the picker launches;
    // if the user denies it the pick still proceeds but a warning is shown.
    val context = LocalContext.current
    var locationPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION)
                == PackageManager.PERMISSION_GRANTED
        )
    }

    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onVideoPicked(uri)
    }

    // Request ACCESS_MEDIA_LOCATION; on result update state and — if granted — open the picker.
    // If denied the user still gets to pick, just without unredacted GPS bytes in the stream.
    val requestLocationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        locationPermissionGranted = granted
        pickVideo.launch(arrayOf("video/*"))
    }

    // Use wherever the picker should launch: request the permission first if not yet granted.
    val launchPick: () -> Unit = {
        if (locationPermissionGranted) {
            pickVideo.launch(arrayOf("video/*"))
        } else {
            requestLocationPermission.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val source = uiState.source
        if (source == null) {
            // Empty state — button centred in the thumb zone (design D5).
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = launchPick,
                ) {
                    Text("Pick video")
                }
                // Shown only when the user has denied ACCESS_MEDIA_LOCATION — GPS tags will
                // likely be absent from the stream so the warning prepares them for that outcome.
                if (!locationPermissionGranted) {
                    Text(
                        text = "GPS location may not be preserved — location access was denied",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        } else {
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
            // Scrollable inner Column so the probe card below the controls doesn't overflow
            // the screen (design D5). The outer Column remains fillMaxSize so the empty-
            // state button still centres via weight; only the source-picked path scrolls.
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
                    onClick = launchPick,
                ) {
                    Text("Pick a different video")
                }
                // Source probe card — reference while trimming (probe-viewer spec).
                // Output metadata is shown on the ResultScreen after a successful cut.
                uiState.sourceProbe?.let { ProbeCard("Source", it) }
            }
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
