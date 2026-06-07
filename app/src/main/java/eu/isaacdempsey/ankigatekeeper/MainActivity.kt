package eu.isaacdempsey.ankigatekeeper

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import eu.isaacdempsey.ankigatekeeper.ui.theme.AnkiGatekeeperTheme

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    // Permission states are Activity-bound: re-checked live in onResume
    private val overlayGranted       = mutableStateOf(false)
    private val notificationsGranted = mutableStateOf(false)
    private val mediaGranted         = mutableStateOf(false)
    private val accessibilityEnabled = mutableStateOf(false)

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notificationsGranted.value = granted }

    private val requestMediaPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results -> mediaGranted.value = results.values.any { it } }

    private val requestAnkiPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.onAnkiPermissionGranted() else vm.onAnkiPermissionDenied()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AnkiGatekeeperTheme {
                LaunchedEffect(Unit) {
                    vm.events.collect { finish() }
                }
                when {
                    !overlayGranted.value ->
                        OverlayPermissionPrompt(onOpenSettings = ::openOverlaySettings)
                    !notificationsGranted.value ->
                        NotificationPermissionPrompt(onRequest = ::requestNotifications)
                    !mediaGranted.value ->
                        MediaPermissionPrompt(onRequest = ::requestMedia)
                    !accessibilityEnabled.value ->
                        AccessibilityPrompt(onOpenSettings = ::openAccessibilitySettings)
                    !vm.deckSelected.value || vm.showSettings.value || vm.noDueMode.value ->
                        DeckPickerScreen(
                            state = vm.deckPickerState.value,
                            onRequestPermission = {
                                requestAnkiPermission.launch(AnkiRepository.ANKI_PERMISSION)
                            },
                            onRetry = vm::loadDecks,
                            onCancel = when {
                                vm.showSettings.value -> vm::closeSettings
                                vm.noDueMode.value   -> vm::endSession
                                else                 -> null
                            },
                            onSelect = vm::onDeckSelected,
                        )
                    else ->
                        AnkiCardScreen(
                            state = vm.cardScreenState.value,
                            onRequestPermission = {
                                requestAnkiPermission.launch(AnkiRepository.ANKI_PERMISSION)
                            },
                            onRetry = vm::fetchCard,
                            onSettings = vm::openSettings,
                            onShowAnswer = vm::onShowAnswer,
                            onAnswer = vm::onAnswer,
                            onSkip = vm::endSession,
                            onExit = if (vm.hasAnsweredCard.value) vm::endSession else null,
                        )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        overlayGranted.value       = Settings.canDrawOverlays(this)
        notificationsGranted.value = checkNotificationsGranted()
        mediaGranted.value         = checkMediaGranted()
        accessibilityEnabled.value = checkAccessibilityEnabled()

        if (overlayGranted.value && notificationsGranted.value) {
            startForegroundService(Intent(this, ScreenUnlockService::class.java))
        }

        val cardState = vm.cardScreenState.value
        val shouldLock = !vm.hasAnsweredCard.value && (
            vm.isMidSession() ||
            (vm.showSettings.value && vm.deckSelected.value) ||
            cardState is CardScreenState.ShowingFront ||
            cardState is CardScreenState.ShowingBack
        )
        vm.applyLock(shouldLock)

        if (!vm.deckSelected.value || vm.showSettings.value || vm.noDueMode.value) {
            if (AnkiRepository.hasPermission(this)) {
                if (vm.deckPickerState.value !is DeckPickerState.Ready) vm.loadDecks()
            } else {
                vm.setAnkiPermissionNeeded()
            }
        } else if (vm.cardScreenState.value is CardScreenState.Loading) {
            vm.fetchCard()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val isNewSession = intent.getBooleanExtra(EXTRA_FROM_UNLOCK, false) ||
            intent.getBooleanExtra(EXTRA_FROM_WEB, false)
        if (isNewSession) vm.startNewSession()
    }

    private fun checkNotificationsGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun checkMediaGranted(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.os.Environment.isExternalStorageManager()
        } else {
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
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

    private fun checkAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val component = "$packageName/${packageName}.AnkiGatekeeperAccessibilityService"
        return enabled.split(":").any { it.equals(component, ignoreCase = true) }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun openOverlaySettings() {
        startActivity(Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.fromParts("package", packageName, null),
        ))
    }

    companion object {
        const val EXTRA_FROM_UNLOCK = "from_unlock"
        const val EXTRA_FROM_WEB   = "from_web"
    }
}
