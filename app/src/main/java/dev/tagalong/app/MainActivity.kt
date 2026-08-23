package dev.tagalong.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // viewModel() here is outside the NavHost, so it is scoped to the Activity
                    // and shared by both destinations. Inside a composable{} block within NavHost,
                    // viewModel() would be scoped to the NavBackStackEntry instead, giving each
                    // screen a separate instance — which would lose all state on navigation.
                    val viewModel: CutViewModel = viewModel()
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "trim") {
                        composable("trim") { TrimScreen(navController, viewModel) }
                        composable("result") { ResultScreen(navController, viewModel) }
                    }
                }
            }
        }
    }
}
