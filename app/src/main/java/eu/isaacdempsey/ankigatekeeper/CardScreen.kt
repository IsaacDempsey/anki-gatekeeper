package eu.isaacdempsey.ankigatekeeper

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnkiCardScreen(
    state: CardScreenState,
    onRequestPermission: () -> Unit,
    onRetry: () -> Unit,
    onSettings: () -> Unit,
    onShowAnswer: () -> Unit,
    onAnswer: (Int) -> Unit,
    onSkip: () -> Unit,
    onExit: (() -> Unit)?,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                is CardScreenState.Loading ->
                    CircularProgressIndicator()

                is CardScreenState.Error ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(horizontal = 32.dp),
                    ) {
                        Text(
                            "Couldn't reach AnkiDroid. Make sure the AnkiDroid API is enabled:\n\n" +
                            "AnkiDroid → Settings → Advanced → Enable AnkiDroid API",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Button(onClick = onRetry) { Text("Retry") }
                        TextButton(onClick = onSkip) { Text("Skip") }
                    }

                is CardScreenState.AnkiNotInstalled ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("AnkiDroid is not installed.", style = MaterialTheme.typography.bodyLarge)
                        Button(onClick = onSkip) { Text("Skip") }
                    }

                is CardScreenState.PermissionDenied ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(horizontal = 32.dp),
                    ) {
                        Text(
                            "AnkiDroid access is needed to fetch your due cards.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Button(onClick = onRequestPermission) { Text("Grant Access") }
                        TextButton(onClick = onSkip) { Text("Skip") }
                    }

                is CardScreenState.ShowingFront ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CardWebView(
                            html = state.card.question,
                            css = state.card.css,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                        Button(
                            onClick = onShowAnswer,
                            modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
                        ) { Text("Show Answer") }
                        if (onExit != null) {
                            OutlinedButton(
                                onClick = onExit,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            ) { Text("Exit") }
                        } else {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                is CardScreenState.ShowingBack ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CardWebView(
                            html = state.card.answer,
                            css = state.card.css,
                            autoPlay = true,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            EaseButton(Modifier.weight(1f), "Again", Color(0xFFE53935), 1, onAnswer)
                            EaseButton(Modifier.weight(1f), "Hard",  Color(0xFFFF7043), 2, onAnswer)
                            EaseButton(Modifier.weight(1f), "Good",  Color(0xFF43A047), 3, onAnswer)
                            EaseButton(Modifier.weight(1f), "Easy",  Color(0xFF1E88E5), 4, onAnswer)
                        }
                        if (onExit != null) {
                            OutlinedButton(
                                onClick = onExit,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            ) { Text("Exit") }
                        } else {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
            }
        }
    }
}

@Composable
private fun EaseButton(modifier: Modifier, label: String, color: Color, ease: Int, onAnswer: (Int) -> Unit) {
    Button(
        modifier = modifier,
        onClick = { onAnswer(ease) },
        colors = ButtonDefaults.buttonColors(containerColor = color),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
    ) {
        Text(label, softWrap = false)
    }
}
