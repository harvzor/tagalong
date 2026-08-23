package dev.tagalong.app

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun ResultScreen(navController: NavController, viewModel: CutViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    // Snapshot the relevant state on first composition. When the user presses back,
    // resetCutState() clears the ViewModel immediately but the screen is still briefly
    // visible during the transition — without snapshots it would flash "unavailable"
    // fallbacks for one frame before popping. remember{} with no key runs once and holds
    // its value for the entire lifetime of this back-stack entry.
    val cutState        = remember { uiState.cutState as? CutState.Saved }
    val sourceProbe     = remember { uiState.sourceProbe }
    val outputProbe     = remember { uiState.outputProbe }
    val outputCacheFile = remember { uiState.outputCacheFile as java.io.File? }

    // Intercept the system back button/gesture so resetCutState() is always called before
    // popping. Without this, the system back bypasses the in-app arrow's onClick handler,
    // TrimScreen re-enters composition with cutState=Saved, and the LaunchedEffect
    // immediately re-navigates forward (back-forward loop).
    val onBack: () -> Unit = {
        viewModel.resetCutState()
        navController.popBackStack()
    }
    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Top bar with back button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to trim",
                )
            }
            Text(
                text = "Cut result",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        // Output absolute path label — falls back to a message when the path was not captured.
        Text(
            text = cutState?.outputAbsolutePath ?: "Output path unavailable",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        // Scrollable content below path label
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Cut video preview — use the cache File so we avoid scoped-storage permission
            // issues with the MediaStore-registered copy (design D3 / task 6.3).
            if (outputCacheFile != null) {
                val player = rememberVideoPlayer(outputCacheFile)
                VideoPreview(
                    player = player,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                )
            }

            // Metadata diff card — the primary value of this screen.
            if (sourceProbe != null && outputProbe != null) {
                MetadataDiffCard(sourceProbe = sourceProbe, outputProbe = outputProbe)
            } else {
                Text(
                    text = "Metadata comparison unavailable",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
