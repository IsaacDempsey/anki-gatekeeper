package com.example.ankilauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ankilauncher.ui.theme.AnkiLauncherTheme

enum class LauncherScreen { GATE, HOME }

class MainActivity : ComponentActivity() {

    // Starts true so the gate shows on first launch / after every screen-off.
    private val gateRequired = mutableStateOf(true)

    // Registered for the lifetime of the Activity so it catches SCREEN_OFF even when paused.
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            gateRequired.value = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        enableEdgeToEdge()
        setContent {
            AnkiLauncherTheme {
                AnkiLauncherApp(
                    screen = if (gateRequired.value) LauncherScreen.GATE else LauncherScreen.HOME,
                    onProceedToHome = { gateRequired.value = false },
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(screenOffReceiver)
    }
}

@Composable
fun AnkiLauncherApp(screen: LauncherScreen, onProceedToHome: () -> Unit) {
    when (screen) {
        LauncherScreen.GATE -> GateScreen(onProceed = onProceedToHome)
        LauncherScreen.HOME -> HomeScreen()
    }
}

@Composable
fun GateScreen(onProceed: () -> Unit) {
    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Anki Launcher",
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onProceed) {
                Text("Go to Home")
            }
        }
    }
}

@Composable
fun HomeScreen() {
    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Home",
                style = MaterialTheme.typography.headlineMedium,
            )
        }
    }
}
