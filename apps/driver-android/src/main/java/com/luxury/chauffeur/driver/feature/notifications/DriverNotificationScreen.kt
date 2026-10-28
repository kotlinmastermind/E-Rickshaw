package com.luxury.chauffeur.driver.feature.notifications

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.luxury.chauffeur.driver.core.designsystem.*
import com.luxury.chauffeur.driver.core.network.DriverApiClient
import kotlinx.coroutines.launch

@Composable
fun DriverNotificationScreen(onBack: () -> Unit, onOpenRide: (String) -> Unit = {}) {
    val api = remember { DriverApiClient.getService() }; val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var prefs by remember { mutableStateOf<Map<String, Any>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }; var error by remember { mutableStateOf<String?>(null) }
    var unreadOnly by remember { mutableStateOf(false) }; var notice by remember { mutableStateOf<String?>(null) }; var editPreferences by remember { mutableStateOf(false) }
    suspend fun load() {
        loading = true; error = null
        try {
            val response = api.getNotificationInbox(unreadOnly)
            if (response.isSuccessful) { val data = response.body().orEmpty(); rows = (data["items"] as? List<Map<String, Any>>).orEmpty(); prefs = api.getNotificationPreferences().body().orEmpty() }
            else error = "Inbox could not be loaded (${response.code()})."
        } catch (_: Exception) { error = "Unable to reach the notification service. Retry when connected." }
        loading = false
    }
    LaunchedEffect(unreadOnly) { load() }
    Scaffold(containerColor = DriverColors.MidnightBlack, topBar = {
        Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column { Text("DRIVER INBOX", style = DriverTypography.Title.copy(color = DriverColors.ChauffeurGold)); Text("Operational and account updates", color = DriverColors.TextMuted) }
            TextButton(onClick = onBack) { Text("Back", color = DriverColors.ChauffeurGold) }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = !unreadOnly, onClick = { unreadOnly = false }, label = { Text("All") }); Spacer(Modifier.width(8.dp))
                FilterChip(selected = unreadOnly, onClick = { unreadOnly = true }, label = { Text("Unread") }); Spacer(Modifier.weight(1f))
                TextButton(onClick = { editPreferences = true }) { Text("Preferences", color = DriverColors.ChauffeurGold) }
                TextButton(onClick = { scope.launch { try { api.markAllNotificationsRead(); load(); notice = "Inbox marked as read." } catch (_: Exception) { error = "Could not update inbox." } } }) { Text("Mark all read", color = DriverColors.ChauffeurGold) }
            }
            Text("Push status: ${prefs["push_enabled"]?.let { if (it == true) "On" else "Off" } ?: "On"} · In-app inbox remains available", color = DriverColors.TextMuted, style = MaterialTheme.typography.bodySmall)
            notice?.let { Text(it, color = DriverColors.ChauffeurGold) }
            when {
                loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = DriverColors.ChauffeurGold) }
                error != null -> Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Text(error!!, color = DriverColors.AlertDanger); TextButton(onClick = { scope.launch { load() } }) { Text("Retry") } }
                rows.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text(if (unreadOnly) "You are all caught up." else "No notifications to show.", color = DriverColors.TextMuted) }
                else -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(rows, key = { it["id"].toString() }) { row ->
                        Card(colors = CardDefaults.cardColors(containerColor = DriverColors.SurfaceCard), border = BorderStroke(1.dp, DriverColors.SurfaceBorder)) {
                            Column(Modifier.fillMaxWidth().padding(15.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) { Text(row["title"].toString(), style = DriverTypography.Body.copy(fontWeight = FontWeight.Bold)); Spacer(Modifier.weight(1f)); if (row["is_read"] == false) Text("NEW", color = DriverColors.ChauffeurGold) }
                                Text(row["body"].toString(), color = DriverColors.TextMuted, modifier = Modifier.padding(top = 5.dp))
                                Text("${row["event_type"] ?: "UPDATE"} · ${row["delivery_status"] ?: "IN APP"}", color = DriverColors.TextMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
                                (row["action_url"] as? String)?.takeIf { it.startsWith("/rides/") }?.let { path -> TextButton(onClick = { val id = path.removePrefix("/rides/").substringBefore('/'); if (id.matches(Regex("[A-Za-z0-9_-]{1,80}"))) onOpenRide(id) }) { Text("Open trip", color = DriverColors.ChauffeurGold) } }
                                if (row["is_read"] == false) TextButton(onClick = { scope.launch { try { api.markNotificationRead(row["id"].toString()); load() } catch (_: Exception) { error = "Could not update this item." } } }) { Text("Mark read", color = DriverColors.ChauffeurGold) }
                                if (row["is_read"] == true) TextButton(onClick = { scope.launch { try { api.markNotificationUnread(row["id"].toString()); load() } catch (_: Exception) { error = "Could not update this item." } } }) { Text("Mark unread", color = DriverColors.TextMuted) }
                            }
                        }
                    }
                }
            }
        }
    }
    if (editPreferences) DriverNotificationPreferencesDialog(prefs, onDismiss = { editPreferences = false }, onSave = { values -> scope.launch {
        try { val response = api.updateNotificationPreferences(values); if (response.isSuccessful) { prefs = response.body().orEmpty(); editPreferences = false; notice = "Notification preferences saved." } else error = "Preferences could not be saved (${response.code()})." }
        catch (_: Exception) { error = "Preferences could not be saved. Check your connection." }
    } })
}

@Composable
private fun DriverNotificationPreferencesDialog(settings: Map<String, Any>, onDismiss: () -> Unit, onSave: (Map<String, Any>) -> Unit) {
    val context = LocalContext.current
    var push by remember(settings) { mutableStateOf(settings["push_enabled"] == true) }
    var email by remember(settings) { mutableStateOf(settings["email_enabled"] == true) }
    var sms by remember(settings) { mutableStateOf(settings["sms_enabled"] == true) }
    var quiet by remember(settings) { mutableStateOf(settings["quiet_hours_enabled"] == true) }
    var start by remember(settings) { mutableStateOf(settings["quiet_start"]?.toString() ?: "22:00") }
    var end by remember(settings) { mutableStateOf(settings["quiet_end"]?.toString() ?: "07:00") }
    var language by remember(settings) { mutableStateOf(settings["language"]?.toString() ?: "en") }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> push = granted }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Notification preferences") }, text = { Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(push, { enabled -> if (enabled && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else push = enabled }); Text("Push notifications") }
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(email, { email = it }); Text("Email · provider unavailable") }
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(sms, { sms = it }); Text("SMS · provider unavailable") }
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(quiet, { quiet = it }); Text("Quiet hours") }
        if (quiet) { OutlinedTextField(start, { start = it }, label = { Text("Start · HH:MM") }, singleLine = true); OutlinedTextField(end, { end = it }, label = { Text("End · HH:MM") }, singleLine = true) }
        var expanded by remember { mutableStateOf(false) }
        Box { TextButton(onClick = { expanded = true }) { Text("Language: ${language.uppercase()}") }; DropdownMenu(expanded, { expanded = false }) { listOf("en", "hi", "mr", "gu").forEach { DropdownMenuItem(text = { Text(it.uppercase()) }, onClick = { language = it; expanded = false }) } } }
        Text("Email and SMS preferences are saved, but delivery needs provider configuration. Urgent safety notices may still use configured push.", style = MaterialTheme.typography.bodySmall)
    } }, confirmButton = { TextButton(onClick = { if (start.matches(Regex("([01]\\d|2[0-3]):[0-5]\\d")) && end.matches(Regex("([01]\\d|2[0-3]):[0-5]\\d"))) onSave(mapOf("push_enabled" to push, "email_enabled" to email, "sms_enabled" to sms, "quiet_hours_enabled" to quiet, "quiet_start" to start, "quiet_end" to end, "language" to language, "timezone" to java.util.TimeZone.getDefault().id)) }) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
