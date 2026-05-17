package com.example.ankilauncher

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.ankilauncher.ui.theme.AnkiLauncherTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class DeckPickerState {
    object Loading : DeckPickerState()
    object PermissionNeeded : DeckPickerState()
    data class Ready(val decks: List<DeckInfo>) : DeckPickerState()
}

sealed class CardScreenState {
    object Loading : CardScreenState()
    object AnkiNotInstalled : CardScreenState()
    object PermissionDenied : CardScreenState()
    object Error : CardScreenState()
    data class ShowingFront(val card: CardInfo, val startTime: Long) : CardScreenState()
    data class ShowingBack(val card: CardInfo, val startTime: Long) : CardScreenState()
}

class MainActivity : ComponentActivity() {

    private val dpm by lazy { getSystemService(DevicePolicyManager::class.java) }
    private val adminComponent by lazy { ComponentName(this, AdminReceiver::class.java) }

    private val overlayGranted = mutableStateOf(false)
    private val notificationsGranted = mutableStateOf(false)
    private val mediaGranted = mutableStateOf(false)
    private val isDeviceOwner = mutableStateOf(false)
    private val deckSelected = mutableStateOf(false)
    private val showSettings = mutableStateOf(false)
    private val deckPickerState = mutableStateOf<DeckPickerState>(DeckPickerState.Loading)
    private val cardScreenState = mutableStateOf<CardScreenState>(CardScreenState.Loading)

    private var decksJob: Job? = null
    private var fetchJob: Job? = null

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsGranted.value = granted
    }

    private val requestMediaPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        mediaGranted.value = results.values.any { it }
    }

    private val requestAnkiPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (!deckSelected.value || showSettings.value) loadDecks() else fetchCard()
        } else {
            onCardAnswered()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deckSelected.value = hasDeckSelection()
        enableEdgeToEdge()
        setContent {
            AnkiLauncherTheme {
                when {
                    !overlayGranted.value ->
                        OverlayPermissionPrompt(onOpenSettings = ::openOverlaySettings)
                    !notificationsGranted.value ->
                        NotificationPermissionPrompt(onRequest = ::requestNotifications)
                    !mediaGranted.value ->
                        MediaPermissionPrompt(onRequest = ::requestMedia)
                    !isDeviceOwner.value ->
                        DeviceOwnerPrompt()
                    !deckSelected.value || showSettings.value ->
                        DeckPickerScreen(
                            state = deckPickerState.value,
                            onRequestPermission = {
                                requestAnkiPermission.launch(AnkiRepository.ANKI_PERMISSION)
                            },
                            onRetry = ::loadDecks,
                            onCancel = if (showSettings.value) {
                                {
                                    showSettings.value = false
                                    if (cardScreenState.value is CardScreenState.Loading) fetchCard()
                                }
                            } else null,
                            onSelect = { deckId ->
                                saveDeckSelection(deckId)
                                deckSelected.value = true
                                showSettings.value = false
                                cardScreenState.value = CardScreenState.Loading
                                fetchCard()
                            },
                        )
                    else ->
                        AnkiCardScreen(
                            state = cardScreenState.value,
                            onRequestPermission = {
                                requestAnkiPermission.launch(AnkiRepository.ANKI_PERMISSION)
                            },
                            onRetry = ::fetchCard,
                            onSettings = {
                                showSettings.value = true
                                cardScreenState.value = CardScreenState.Loading
                                loadDecks()
                            },
                            onShowAnswer = {
                                val s = cardScreenState.value
                                if (s is CardScreenState.ShowingFront) {
                                    cardScreenState.value = CardScreenState.ShowingBack(s.card, s.startTime)
                                }
                            },
                            onAnswer = { ease ->
                                val s = cardScreenState.value
                                if (s is CardScreenState.ShowingBack) {
                                    val elapsed = SystemClock.elapsedRealtime() - s.startTime
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        AnkiRepository.submitAnswer(
                                            this@MainActivity,
                                            s.card.noteId,
                                            s.card.cardOrd,
                                            ease,
                                            elapsed,
                                        )
                                        withContext(Dispatchers.Main) { onCardAnswered() }
                                    }
                                }
                            },
                            onSkip = ::onCardAnswered,
                        )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        overlayGranted.value = Settings.canDrawOverlays(this)
        notificationsGranted.value = checkNotificationsGranted()
        mediaGranted.value = checkMediaGranted()
        isDeviceOwner.value = dpm.isDeviceOwnerApp(packageName)

        if (overlayGranted.value && notificationsGranted.value) {
            startForegroundService(Intent(this, ScreenUnlockService::class.java))
        }

        if (isDeviceOwner.value) {
            dpm.setLockTaskPackages(adminComponent, arrayOf(packageName))

            val cardState = cardScreenState.value
            val shouldLock = (showSettings.value && deckSelected.value) ||
                cardState is CardScreenState.ShowingFront ||
                cardState is CardScreenState.ShowingBack
            if (shouldLock) {
                try { startLockTask() } catch (_: IllegalStateException) { }
            }

            if (!deckSelected.value || showSettings.value) {
                if (AnkiRepository.hasPermission(this)) {
                    if (deckPickerState.value !is DeckPickerState.Ready) loadDecks()
                } else {
                    deckPickerState.value = DeckPickerState.PermissionNeeded
                }
            } else if (cardScreenState.value is CardScreenState.Loading) {
                fetchCard()
            }
        }
    }

    private fun loadDecks() {
        decksJob?.cancel()
        deckPickerState.value = DeckPickerState.Loading
        decksJob = lifecycleScope.launch(Dispatchers.IO) {
            val decks = AnkiRepository.fetchDecks(this@MainActivity)
            withContext(Dispatchers.Main) {
                deckPickerState.value = DeckPickerState.Ready(decks)
            }
        }
    }

    private fun fetchCard() {
        fetchJob?.cancel()
        cardScreenState.value = CardScreenState.Loading
        val deckId = getSavedDeckId()
        fetchJob = lifecycleScope.launch(Dispatchers.IO) {
            val result = AnkiRepository.fetchDueCard(this@MainActivity, deckId)
            withContext(Dispatchers.Main) {
                cardScreenState.value = when (result) {
                    is CardFetchResult.NoDue -> { onCardAnswered(); CardScreenState.Loading }
                    is CardFetchResult.Error -> { tryStopLockTask(); CardScreenState.Error }
                    is CardFetchResult.NotInstalled -> { tryStopLockTask(); CardScreenState.AnkiNotInstalled }
                    is CardFetchResult.PermissionDenied -> { tryStopLockTask(); CardScreenState.PermissionDenied }
                    is CardFetchResult.Success -> {
                        try { startLockTask() } catch (_: IllegalStateException) { }
                        CardScreenState.ShowingFront(result.card, SystemClock.elapsedRealtime())
                    }
                }
            }
        }
    }

    private fun tryStopLockTask() {
        try { stopLockTask() } catch (_: IllegalStateException) { }
    }

    private fun onCardAnswered() {
        tryStopLockTask()
        finish()
    }

    private fun hasDeckSelection(): Boolean = getPreferences(MODE_PRIVATE).contains("deck_id")
    private fun getSavedDeckId(): Long = getPreferences(MODE_PRIVATE).getLong("deck_id", 0L)
    private fun saveDeckSelection(deckId: Long) {
        getPreferences(MODE_PRIVATE).edit().putLong("deck_id", deckId).apply()
    }

    private fun checkNotificationsGranted(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkMediaGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.os.Environment.isExternalStorageManager()
        } else {
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestMedia() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startActivity(Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.fromParts("package", packageName, null),
            ))
        } else {
            requestMediaPermissions.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
        }
    }

    private fun openOverlaySettings() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.fromParts("package", packageName, null),
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckPickerScreen(
    state: DeckPickerState,
    onRequestPermission: () -> Unit,
    onRetry: () -> Unit,
    onCancel: (() -> Unit)?,
    onSelect: (Long) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Choose Study Deck") },
                actions = {
                    if (onCancel != null) {
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            Text(
                "Cards will be drawn from this deck on each unlock.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))

            when (state) {
                is DeckPickerState.Loading ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                is DeckPickerState.PermissionNeeded ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text("Grant AnkiDroid access to see your decks.")
                            Button(onClick = onRequestPermission) { Text("Grant Access") }
                        }
                    }

                is DeckPickerState.Ready -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            OutlinedButton(
                                onClick = { onSelect(0L) },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("All Decks") }
                        }
                        if (state.decks.isEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "No decks found. Enable the AnkiDroid API first:\n\n" +
                                    "AnkiDroid → Settings → Advanced → Enable AnkiDroid API\n\n" +
                                    "Then come back and tap Retry.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedButton(
                                    onClick = onRetry,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("Retry") }
                            }
                        } else {
                            items(state.decks) { deck ->
                                Card(
                                    onClick = { onSelect(deck.id) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = deck.name,
                                        modifier = Modifier.padding(16.dp),
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

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
                            modifier = Modifier.padding(vertical = 16.dp),
                        ) { Text("Show Answer") }
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
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            EaseButton(Modifier.weight(1f), "Again", Color(0xFFE53935), 1, onAnswer)
                            EaseButton(Modifier.weight(1f), "Hard",  Color(0xFFFF7043), 2, onAnswer)
                            EaseButton(Modifier.weight(1f), "Good",  Color(0xFF43A047), 3, onAnswer)
                            EaseButton(Modifier.weight(1f), "Easy",  Color(0xFF1E88E5), 4, onAnswer)
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
    ) {
        Text(label)
    }
}

@Composable
fun OverlayPermissionPrompt(onOpenSettings: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Allow Display Over Other Apps") },
        text = { Text("Anki Launcher needs this permission to show your review card immediately after unlock.") },
        confirmButton = {
            Button(onClick = onOpenSettings) { Text("Open Settings") }
        },
    )
}

@Composable
fun NotificationPermissionPrompt(onRequest: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Allow Notifications") },
        text = { Text("Anki Launcher needs notification permission to keep the background service running.") },
        confirmButton = {
            Button(onClick = onRequest) { Text("Allow") }
        },
    )
}

@Composable
fun MediaPermissionPrompt(onRequest: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Allow Media Access") },
        text = { Text("Anki Launcher needs \"All files access\" to load images and audio from AnkiDroid's media folder.\n\nTap Open Settings and enable \"Allow access to manage all files\".") },
        confirmButton = {
            Button(onClick = onRequest) { Text("Open Settings") }
        },
    )
}

@Composable
fun DeviceOwnerPrompt() {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Enable Device Owner") },
        text = {
            Text(
                "Run the following ADB command, then reopen the app:\n\n" +
                "adb shell dpm set-device-owner " +
                "com.example.ankilauncher/.AdminReceiver\n\n" +
                "To remove later:\n\n" +
                "adb shell dpm remove-active-admin " +
                "com.example.ankilauncher/.AdminReceiver"
            )
        },
        confirmButton = {},
    )
}
