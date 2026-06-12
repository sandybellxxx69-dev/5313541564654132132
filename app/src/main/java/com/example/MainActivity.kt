package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.MainScreen
import com.example.ui.SetupScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Handle result if needed
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Request Notification Permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val prefs = PreferencesManager(this)
        LogManager.init(prefs)

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(prefs = prefs)
                }
            }
        }
    }
}

@Composable
fun AppNavigation(prefs: PreferencesManager) {
    val navController = rememberNavController()
    val startDest = if (prefs.userSdmx != null) "main" else "setup"

    NavHost(navController = navController, startDestination = startDest) {
        composable("setup") {
            SetupScreen(
                prefs = prefs,
                onSetupComplete = {
                    val context = navController.context
                    // Programar primera vez
                    SdmxWorker.enqueuePeriodic(context, prefs.intervalHours)
                    navController.navigate("main") {
                        popUpTo("setup") { inclusive = true }
                    }
                }
            )
        }
        composable("main") {
            MainScreen(
                prefs = prefs,
                onResetSetup = {
                    prefs.userSdmx = null
                    prefs.passSdmx = null
                    navController.navigate("setup") {
                        popUpTo("main") { inclusive = true }
                    }
                }
            )
        }
    }
}
