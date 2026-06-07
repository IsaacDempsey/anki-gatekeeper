package eu.isaacdempsey.ankigatekeeper

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun OverlayPermissionPrompt(onOpenSettings: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Allow Display Over Other Apps") },
        text = { Text("AnkiGatekeeper needs this permission to show your review card immediately after unlock.") },
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
        text = { Text("AnkiGatekeeper needs notification permission to keep the background service running.") },
        confirmButton = {
            Button(onClick = onRequest) { Text("Allow") }
        },
    )
}

@Composable
fun AccessibilityPrompt(onOpenSettings: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Enable Accessibility Service") },
        text = { Text("AnkiGatekeeper needs its accessibility service enabled to keep the card screen in the foreground.\n\nTap Open Settings, find \"AnkiGatekeeper\" in the list, and turn it on.") },
        confirmButton = {
            Button(onClick = onOpenSettings) { Text("Open Settings") }
        },
    )
}

@Composable
fun MediaPermissionPrompt(onRequest: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Allow Media Access") },
        text = { Text("AnkiGatekeeper needs \"All files access\" to load images and audio from AnkiDroid's media folder.\n\nTap Open Settings and enable \"Allow access to manage all files\".") },
        confirmButton = {
            Button(onClick = onRequest) { Text("Open Settings") }
        },
    )
}
