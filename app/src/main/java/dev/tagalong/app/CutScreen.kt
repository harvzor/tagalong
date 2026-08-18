package dev.tagalong.app

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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun CutScreen(viewModel: CutViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    // WHY OpenDocument AND NOT PickVisualMedia:
    // This app's core guarantee is lossless metadata preservation — every container tag in
    // the source (GPS location, creation_time, device make/model, brand-specific Xiaomi tags)
    // must survive the cut unchanged.  The Android Photo Picker (PickVisualMedia / ACTION_PICK)
    // makes this guarantee impossible to fulfil:
    //
    //   1. GPS tags — The Google Photo Picker module (com.google.android.providers.media.module)
    //      strips location tags from the openInputStream byte stream regardless of whether
    //      ACCESS_MEDIA_LOCATION is declared.  There is no public API to request unredacted
    //      stream access through the Photo Picker path.
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
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onVideoPicked(uri)
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
                    onClick = {
                        pickVideo.launch(arrayOf("video/*"))
                    },
                ) {
                    Text("Pick video")
                }
            }
        } else {
            val player = rememberVideoPlayer(source.file)
            // Gallery-relative path label (spec cut-workflow; design D2).
            Text(
                text = source.displayPath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Full-width preview; height capped so portrait clips don't push controls off-screen
            // (design D6). PlayerView honours the aspect ratio once the video is decoded.
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
                enabled = uiState.cutState != CutState.Working,
            ) {
                Text("Cut and save")
            }
            // Demoted to OutlinedButton so "Cut and save" has clear visual priority (design D4).
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    pickVideo.launch(arrayOf("video/*"))
                },
            ) {
                Text("Pick a different video")
            }
        }

        CutStateStatus(uiState.cutState)
    }
}

@Composable
private fun CutStateStatus(cutState: CutState) {
    when (cutState) {
        is CutState.Idle -> Unit
        is CutState.Working -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator()
            Text("Cutting…")
        }
        is CutState.Saved -> Text(
            text = cutState.galleryDateMillis?.let { "Saved — gallery date ${formatGalleryDate(it)}" }
                ?: "Saved — gallery date could not be confirmed",
            color = MaterialTheme.colorScheme.primary,
        )
        is CutState.Error -> Text(
            text = "Failed: ${cutState.message}",
            color = MaterialTheme.colorScheme.error,
        )
    }
}
