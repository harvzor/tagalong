package dev.tagalong.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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

    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
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
                        pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
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
                    pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
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
