package dev.tagalong.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

/**
 * Parallax slide durations for screen navigation. Forward is marginally longer because the
 * incoming screen crosses the whole width; back navigation is the shorter trip users repeat most.
 * Both stay well under the 400 ms ceiling in the screen-navigation spec, and both are duration
 * based so Compose's MotionDurationScale collapses them to a single frame when the system has
 * animations switched off — no explicit reduced-motion branch is needed.
 */
private const val FORWARD_MILLIS = 300
private const val BACK_MILLIS = 250

/** Divisor for the distance travelled by the screen that is *not* on top. See the NavHost below. */
private const val DEPTH_DIVISOR = 3

/**
 * The app's appearance decision. Every screen reads `MaterialTheme.colorScheme` and none of them
 * names a colour of its own, so this one argument selects light or dark for the whole app,
 * including screens added later.
 *
 * `isSystemInDarkTheme()` reads `Configuration.uiMode`, which is the same source the platform
 * consults to choose between `values/themes.xml` and `values-night/themes.xml`. That is the point:
 * the Compose scheme and the window-level theme (window background, status bar contrast, the base
 * styling that non-Compose views such as PlayerView resolve) cannot drift, because both are
 * answers to the same question. `enableEdgeToEdge()` in onCreate relies on this — it picks its
 * system bar appearance from `uiMode` as well, not from the Compose scheme.
 *
 * Deliberately absent: a user-facing override. An override can put this line out of agreement with
 * `uiMode`, which would then require the app to drive `uiMode` itself. Until that is designed, no
 * screen needs appearance state of its own.
 */
@Composable
private fun TagalongTheme(content: @Composable () -> Unit) {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme, content = content)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TagalongTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // viewModel() here is outside the NavHost, so it is scoped to the Activity
                    // and shared by both destinations. Inside a composable{} block within NavHost,
                    // viewModel() would be scoped to the NavBackStackEntry instead, giving each
                    // screen a separate instance — which would lose all state on navigation.
                    val viewModel: CutViewModel = viewModel()
                    val navController = rememberNavController()
                    NavHost(
                        navController = navController,
                        startDestination = "home",
                        // Parallax slide, replacing navigation-compose's default 700ms crossfade
                        // (fadeIn/fadeOut tween(700), identical in both directions). Two things
                        // were wrong with the default: 700ms of pure alpha gives the eye nothing
                        // to track, and the screens have no backgrounds of their own, so both
                        // were composited at partial alpha on top of each other — every label
                        // double-exposed.
                        //
                        // The screen on top crosses the full width while the one beneath it
                        // crosses a third of it. That difference in rate is what reads as depth;
                        // moving both at 1:1 would tile perfectly but look like one flat image
                        // being dragged.
                        //
                        // The distances are paired deliberately against navigation-compose's own
                        // z-ordering (NavHost.kt gives the incoming entry zIndex +1 going forward
                        // and -1 going back), so the two panels always overlap enough to cover the
                        // width with no gap of bare background between them.
                        //
                        // The panel that crosses the full width decelerates (LinearOutSlowIn) and
                        // the depth panel behind it drifts a third as far on FastOutSlowIn. The
                        // pairing is not just taste: the decelerating panel always leads the depth
                        // drift, which is what guarantees the two overlap on every frame. Swapping
                        // the curves makes the depth panel outrun the incoming one for the first
                        // ~60ms, and the 5-8% of width it gains shows as a flash of bare
                        // background between the two sliding panels.
                        //
                        // Pure translation, no fade: the Trim and Result previews are
                        // SurfaceViews, which do not track their host View's alpha and would stay
                        // at full opacity through a crossfade.
                        enterTransition = {
                            slideInHorizontally(
                                animationSpec = tween(FORWARD_MILLIS, easing = LinearOutSlowInEasing),
                                initialOffsetX = { fullWidth -> fullWidth },
                            )
                        },
                        exitTransition = {
                            slideOutHorizontally(
                                animationSpec = tween(FORWARD_MILLIS, easing = FastOutSlowInEasing),
                                targetOffsetX = { fullWidth -> -fullWidth / DEPTH_DIVISOR },
                            )
                        },
                        // Back navigation is the mirror image. Here the departing screen is the
                        // one on top, so it must clear the full width to uncover what it covers,
                        // while the returning screen underneath only eases into place.
                        popEnterTransition = {
                            slideInHorizontally(
                                animationSpec = tween(BACK_MILLIS, easing = FastOutSlowInEasing),
                                initialOffsetX = { fullWidth -> -fullWidth / DEPTH_DIVISOR },
                            )
                        },
                        popExitTransition = {
                            slideOutHorizontally(
                                animationSpec = tween(BACK_MILLIS, easing = LinearOutSlowInEasing),
                                targetOffsetX = { fullWidth -> fullWidth },
                            )
                        },
                        // sizeTransform is left null on purpose: it re-measures its content on
                        // every animation frame, which would re-layout the AndroidView holding
                        // the preview player mid-transition.
                    ) {
                        composable("home") { HomeScreen(navController, viewModel) }
                        composable("trim") { TrimScreen(navController, viewModel) }
                        composable("result") { ResultScreen(navController, viewModel) }
                        composable("about") { AboutScreen(navController) }
                    }
                }
            }
        }
    }
}
