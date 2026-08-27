package dev.tagalong.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun AboutScreen(navController: NavController) {
    val context = LocalContext.current
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }
    }
    val uriHandler = LocalUriHandler.current

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
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                )
            }
            Text(
                text = "About",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // App identity
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Tagalong",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = "v$versionName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Tagalong is licensed under GPL v3.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            HorizontalDivider()

            // Source code
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Source code",
                    style = MaterialTheme.typography.titleSmall,
                )
                TextButton(
                    onClick = { uriHandler.openUri("https://github.com/harvzor/tagalong") },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) {
                    Text(
                        text = "github.com/harvzor/tagalong",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            HorizontalDivider()

            // Open-source attribution
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Open-source software used",
                    style = MaterialTheme.typography.titleSmall,
                )

                // ffmpeg-kit-full-gpl attribution (LGPL v3 §4(a) prominent notice)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "ffmpeg-kit-full-gpl 2.1.0",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "by Anton Karpenko · GPL v3 / LGPL v3",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = { uriHandler.openUri("https://github.com/sk3llo/ffmpeg-kit-flutter") },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) {
                        Text(
                            text = "github.com/sk3llo/ffmpeg-kit-flutter",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
