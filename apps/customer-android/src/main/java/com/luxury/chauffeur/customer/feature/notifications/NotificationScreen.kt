package com.luxury.chauffeur.customer.feature.notifications

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.clickable
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
import com.luxury.chauffeur.customer.core.designsystem.*
import com.luxury.chauffeur.customer.core.network.ApiClient
import com.luxury.chauffeur.customer.core.network.NotificationDto
import kotlinx.coroutines.launch

@Composable
fun NotificationScreen(onNavigateBack: () -> Unit, onOpenAction: (String) -> Unit = {}) {
    val api = remember { ApiClient.getService() }
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<NotificationDto>>(emptyList()) }
    var unread by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var unreadOnly by remember { mutableStateOf(false) }
    var eventFilter by remember { mutableStateOf("") }
    var eventTypes by remember { mutableStateOf<List<String>>(emptyList()) }
    var settings by remember { mutableStateOf<Map<String, Any>?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var receiptRows by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var receiptTitle by remember { mutableStateOf("") }
    suspend fun load() {
        loading = true; error = null
        try {
            val response = api.getNotificationInbox(unreadOnly, eventFilter.ifBlank { null })
            if (!response.isSuccessful) error = "Notifications could not be loaded (${response.code()})."
            else { items = response.body()?.items.orEmpty(); unread = response.body()?.unreadCount ?: 0; eventTypes = (eventTypes + items.map { it.eventType }).filter { it.isNotBlank() }.distinct().sorted() }
        } catch (_: Exception) { error = "Unable to reach notifications. Check your connection and retry." }
        loading = false
    }
    LaunchedEffect(unreadOnly, eventFilter) { load(); try { settings = api.getNotificationPreferences().body() } catch (_: Exception) { } }
    Scaffold(containerColor = LuxuryColors.ObsidianBlack, topBar = {
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column { Text("NOTIFICATIONS", style = LuxuryTypography.HeadlineMedium.copy(color = LuxuryColors.ChampagneGold)); Text("$unread unread · private to your account", style = LuxuryTypography.BodySubtle) }
            TextButton(onClick = { showSettings = true }) { Text("Preferences", color = LuxuryColors.ChampagneGold) }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = !unreadOnly, onClick = { unreadOnly = false }, label = { Text("All") })
                FilterChip(selected = unreadOnly, onClick = { unreadOnly = true }, label = { Text("Unread") })
                var expanded by remember { mutableStateOf(false) }
                Box { TextButton(onClick = { expanded = true }) { Text(eventFilter.ifBlank { "All types" }, color = LuxuryColors.ChampagneGold) }
                    DropdownMenu(expanded, { expanded = false }) { (listOf("") + eventTypes).forEach { type -> DropdownMenuItem(text = { Text(type.ifBlank { "All types" }) }, onClick = { eventFilter = type; expanded = false }) } }
                }
                if (unread > 0) TextButton(onClick = { scope.launch { try { api.markAllNotificationsRead(); load(); notice = "All notifications marked as read." } catch (_: Exception) { error = "Could not update read status." } } }) { Text("Mark all read", color = LuxuryColors.ChampagneGold) }
            }
            notice?.let { Text(it, color = LuxuryColors.ChampagneGold, modifier = Modifier.padding(4.dp)) }
            when {
                loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = LuxuryColors.ChampagneGold) }
                error != null -> Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Text(error!!, color = MaterialTheme.colorScheme.error); TextButton(onClick = { scope.launch { load() } }) { Text("Retry") } }
                items.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text(if (unreadOnly) "You are all caught up." else "Your notification inbox is empty.", color = LuxuryColors.SubtleGray) }
                else -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) { items(items, key = { it.id }) { item ->
                    LuxuryCard(modifier = Modifier.fillMaxWidth().clickable { if (!item.isRead) scope.launch { try { api.markNotificationRead(item.id); load() } catch (_: Exception) { error = "Could not update this notification." } } }) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(item.title, style = LuxuryTypography.BodyRegular.copy(fontWeight = FontWeight.Bold)); if (!item.isRead) GoldBadge("NEW") }
                        Text(item.body, style = LuxuryTypography.BodySubtle, modifier = Modifier.padding(top = 5.dp))
                        Text("${item.eventType} · ${item.createdAt.take(16).replace('T', ' ')} · ${item.deliveryStatus ?: "IN APP"}", style = LuxuryTypography.BodySubtle, modifier = Modifier.padding(top = 8.dp))
                        item.actionUrl?.takeIf { it.startsWith("/rides/") }?.let { path -> TextButton(onClick = { onOpenAction(path) }) { Text("Open trip details", color = LuxuryColors.ChampagneGold) } }
                        TextButton(onClick = { scope.launch { try { val response = api.getNotificationReceipts(item.id); if (response.isSuccessful) { receiptRows = response.body().orEmpty(); receiptTitle = item.title } else error = "Delivery details are not available." } catch (_: Exception) { error = "Delivery details could not be loaded." } } }) { Text("Delivery details", color = LuxuryColors.ChampagneGold) }
                        if (item.isRead) TextButton(onClick = { scope.launch { try { api.markNotificationUnread(item.id); load() } catch (_: Exception) { error = "Could not update this notification." } } }) { Text("Mark unread", color = LuxuryColors.SubtleGray) }
                    }
                } } }
            LuxuryButton(text = "BACK TO JOURNEY", isSecondary = true, onClick = onNavigateBack, modifier = Modifier.padding(bottom = 12.dp))
        }
    }
    if (receiptTitle.isNotBlank()) AlertDialog(onDismissRequest = { receiptTitle = "" }, title = { Text("Delivery details") }, text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(receiptTitle, fontWeight = FontWeight.Bold); if (receiptRows.isEmpty()) Text("No external delivery attempts are recorded. Your in-app notification is retained.") else receiptRows.forEach { Text("${it["channel"] ?: ""} · ${it["status"] ?: ""} · ${it["attempted_at"] ?: ""}") } } }, confirmButton = { TextButton(onClick = { receiptTitle = "" }) { Text("Done") } })
    if (showSettings) NotificationPreferencesDialog(settings, onDismiss = { showSettings = false }, onSave = { values -> scope.launch { try { val response = api.updateNotificationPreferences(values); if (response.isSuccessful) { settings = response.body(); showSettings = false; notice = "Notification preferences saved." } else error = "Preferences could not be saved (${response.code()})." } catch (_: Exception) { error = "Preferences could not be saved. Check your connection." } } })
}

@Composable
private fun NotificationPreferencesDialog(settings: Map<String, Any>?, onDismiss: () -> Unit, onSave: (Map<String, Any>) -> Unit) {
    val context = LocalContext.current
    var push by remember(settings) { mutableStateOf(settings?.get("push_enabled") == true) }
    var email by remember(settings) { mutableStateOf(settings?.get("email_enabled") == true) }
    var sms by remember(settings) { mutableStateOf(settings?.get("sms_enabled") == true) }
    var quiet by remember(settings) { mutableStateOf(settings?.get("quiet_hours_enabled") == true) }
    var start by remember(settings) { mutableStateOf(settings?.get("quiet_start")?.toString() ?: "22:00") }
    var end by remember(settings) { mutableStateOf(settings?.get("quiet_end")?.toString() ?: "07:00") }
    var language by remember(settings) { mutableStateOf(settings?.get("language")?.toString() ?: "en") }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> push = granted }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Notification preferences") }, text = { Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(push, { enabled -> if (enabled && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else push = enabled }); Text("Push notifications") }
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(email, { email = it }); Text("Email (provider unavailable)") }
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(sms, { sms = it }); Text("SMS (provider unavailable)") }
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(quiet, { quiet = it }); Text("Quiet hours") }
        if (quiet) { OutlinedTextField(start, { start = it }, label = { Text("Start · HH:MM") }, singleLine = true); OutlinedTextField(end, { end = it }, label = { Text("End · HH:MM") }, singleLine = true) }
        var expanded by remember { mutableStateOf(false) }
        Box { TextButton(onClick = { expanded = true }) { Text("Language: ${language.uppercase()}") }; DropdownMenu(expanded, { expanded = false }) { listOf("en", "hi", "mr", "gu").forEach { DropdownMenuItem(text = { Text(it.uppercase()) }, onClick = { language = it; expanded = false }) } } }
        Text("Inbox copies remain available. Email and SMS delivery depend on provider setup. Urgent safety notices may still use configured push.", style = MaterialTheme.typography.bodySmall)
    } }, confirmButton = { TextButton(onClick = { if (start.matches(Regex("([01]\\d|2[0-3]):[0-5]\\d")) && end.matches(Regex("([01]\\d|2[0-3]):[0-5]\\d"))) onSave(mapOf("push_enabled" to push, "email_enabled" to email, "sms_enabled" to sms, "quiet_hours_enabled" to quiet, "quiet_start" to start, "quiet_end" to end, "language" to language, "timezone" to java.util.TimeZone.getDefault().id)) }) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}


