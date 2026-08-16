package dev.tagalong.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
            Button(onClick = {
                pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
            }) {
                Text("Pick video")
            }
        } else {
            val player = rememberVideoPlayer(source.file)
            VideoPreview(player = player)
            TrimRangeSlider(
                durationMs = source.durationMs,
                startMs = uiState.startMs,
                endMs = uiState.endMs,
                player = player,
                onRangeChanged = viewModel::onRangeChanged,
            )
            Button(
                onClick = { viewModel.runCut() },
                enabled = uiState.cutState != CutState.Working,
            ) {
                Text("Cut and save")
            }
            Button(onClick = {
                pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
            }) {
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
