package dev.tagalong.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController

@Composable
fun HomeScreen(navController: NavController, viewModel: CutViewModel) {
    // Navigate to trim once the ViewModel signals the video is ready (materialised + probed).
    // Using a SharedFlow event rather than observing source directly avoids re-triggering when
    // the user returns to this screen with source still non-null in the ViewModel.
    LaunchedEffect(Unit) {
        viewModel.navigateToTrim.collect {
            navController.navigate("trim") { launchSingleTop = true }
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
    // the output automatically.
    //
    // WHY THE PERMISSION IS REQUESTED ONLY FROM THE HOME-SCREEN CONTROL:
    // The runtime request is owned exclusively by the "Enable media location access" button
    // below; the pick flow never requests it and is never blocked by permission state.  This
    // keeps the pick tap to a single system dialog (the picker's own media consent on modern
    // Android) and gives the permission a persistent, explainable home instead of a transient
    // mid-flow prompt.  A denial degrades the cut output (GPS is stripped by the framework);
    // the persistent status panel is the sole place this is communicated.
    val context = LocalContext.current
    var locationPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION)
                == PackageManager.PERMISSION_GRANTED
        )
    }

    // Re-check on every resume so grants/revokes made in system settings while the app was
    // backgrounded are reflected without a process restart (the status line must never go stale).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                locationPermissionGranted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_MEDIA_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onVideoPicked(uri)
        // Navigation to "trim" happens via the navigateToTrim LaunchedEffect above,
        // once the ViewModel's async materialise-and-probe coroutine completes.
    }

    // The Enable button's request path.  Results only update the status state; nothing is
    // chained onto it — the pick flow is independent of the permission by design.
    //
    // Single behavior (design D2, revised): always issue the request, never navigate to
    // settings.  While the OS silently auto-denies (after repeated declines) a tap shows no
    // dialog — accepted: the status line keeps reporting the truth and the suppression
    // decays with inactivity or a process restart, so the dialog eventually returns.
    val requestLocationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        locationPermissionGranted = granted
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // About icon — top right
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            IconButton(onClick = { navController.navigate("about") }) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = "About",
                )
            }
        }

        // Content area: title at ~25% from top, button exactly centred
        BoxWithConstraints(
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            // Title: horizontally centred, pinned at 25% down
            Text(
                text = "Tagalong",
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = maxHeight * 0.25f),
            )

            // Media-location status block (above the primary action) + "Pick video", centred.
            Column(
                modifier = Modifier.align(Alignment.Center).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Persistent media-location access status.  This is the app's only permission
                // prompt point; state derives from checkSelfPermission, refreshed on resume.
                if (locationPermissionGranted) {
                    // Inert status line — revocation intentionally stays in system settings.
                    Text(
                        text = "\uD83D\uDCCD Media location access granted \u2713",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "Media location access is off",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        // Mechanism-only copy: describes what the permission reads and what is
                        // lost without it — no behavioral disclaimers (deliberate, design D5).
                        Text(
                            text = "GPS coordinates are stored inside your video files. " +
                                "Without permission to read them, Android strips GPS from every cut.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // Secondary styling: the permission control must not compete with
                        // the app's primary action ("Pick video" stays the filled button).
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                requestLocationPermission.launch(
                                    Manifest.permission.ACCESS_MEDIA_LOCATION
                                )
                            },
                        ) {
                            Text("Enable media location access")
                        }
                    }
                }

                // Always enabled: permission state degrades output quality, never blocks the flow.
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    onClick = { pickVideo.launch(arrayOf("video/*")) },
                ) {
                    Text("Pick video")
                }
            }
        }
    }
}
