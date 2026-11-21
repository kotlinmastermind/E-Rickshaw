package com.vaisentra.mobility.driver.feature.support

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaisentra.mobility.driver.components.*
import com.vaisentra.mobility.driver.data.DriverRepository
import com.vaisentra.mobility.driver.theme.*
import kotlinx.coroutines.launch

/**
 * Screen 44: Electric Academy Training (GET /api/v1/driver-onboarding/training/curriculum)
 */
@Composable
fun AcademyTrainingScreen(
    onNavigateBack: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    val modules by DriverRepository.trainingModules.collectAsState()

    LaunchedEffect(Unit) {
        DriverRepository.fetchTrainingCurriculum()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Electric Academy",
            subtitle = "Skill training certifications & pilot road safety",
            onBack = onNavigateBack
        )

        if (modules.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                Text("No training modules assigned yet.", color = CockpitTextMuted, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(modules) { module ->
                    val isDone = module.isCompleted || module.assessmentPassed
                    val statusText = if (isDone) "Completed" else "${module.durationMinutes} min Module"
                    val tint = if (isDone) EmeraldPrimary else ElectricCyan
                    CockpitCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(module.title, color = CockpitTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(module.description, color = CockpitTextSecondary, fontSize = 12.sp)
                            }
                            CockpitBadge(statusText, tint)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Screen 46: Found Lost Item in Cab (POST /api/v1/drivers/safety/incidents)
 */
@Composable
fun FoundItemScreen(
    onNavigateBack: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    var itemCategory by remember { mutableStateOf("Mobile Phone / Tablet") }
    var itemNotes by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    val rideId by DriverRepository.currentRideId.collectAsState()
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Found Lost Item",
            subtitle = "Report passenger belongings left behind in cab",
            onBack = onNavigateBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                CockpitCard {
                    Text("SELECT ITEM CATEGORY", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    val categories = listOf("Mobile Phone / Tablet", "Wallet / Purse / Cards", "Umbrella / Water Bottle", "Backpack / Shopping Bag", "Keys / Glasses")
                    categories.forEach { cat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = itemCategory == cat,
                                onClick = { itemCategory = cat },
                                colors = RadioButtonDefaults.colors(selectedColor = ElectricCyan)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(cat, color = CockpitTextPrimary, fontSize = 13.sp)
                        }
                    }
                }
            }

            item {
                CockpitTextField(
                    value = itemNotes,
                    onValueChange = { itemNotes = it },
                    label = "Description / Identifying Marks",
                    placeholder = "Color, brand, where in cab it was found..."
                )
            }

            item {
                CockpitButton(
                    text = if (isSubmitting) "Submitting..." else "Report Found Item & Lock Ticket",
                    enabled = !isSubmitting && itemNotes.isNotBlank(),
                    onClick = {
                        isSubmitting = true
                        scope.launch {
                            val res = DriverRepository.reportFoundItem(rideId, itemCategory, itemNotes)
                            isSubmitting = false
                            if (res.isSuccess) {
                                onShowToast("Found item logged!")
                                onNavigateBack()
                            } else {
                                onShowToast("Failed to report item: ${res.exceptionOrNull()?.message}")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Screen 47: Driver Helpdesk Tickets (GET/POST /api/v1/driver-onboarding/tickets)
 */
@Composable
fun DriverHelpdeskScreen(
    onNavigateBack: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    val tickets by DriverRepository.driverTickets.collectAsState()
    var showCreateForm by remember { mutableStateOf(false) }
    var subject by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        DriverRepository.fetchDriverTickets()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Driver Helpdesk",
            subtitle = "24x7 pilot partner support & ticket tracking",
            onBack = onNavigateBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                CockpitButton(
                    text = if (showCreateForm) "Cancel New Ticket" else "+ Raise New Helpdesk Ticket",
                    onClick = { showCreateForm = !showCreateForm },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (showCreateForm) {
                item {
                    CockpitCard {
                        Text("NEW SUPPORT TICKET", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        CockpitTextField(
                            value = subject,
                            onValueChange = { subject = it },
                            label = "Subject",
                            placeholder = "Brief issue summary"
                        )
                        Spacer(Modifier.height(8.dp))
                        CockpitTextField(
                            value = message,
                            onValueChange = { message = it },
                            label = "Message",
                            placeholder = "Describe your issue in detail..."
                        )
                        Spacer(Modifier.height(10.dp))
                        CockpitButton(
                            text = if (isSubmitting) "Submitting..." else "Submit Ticket",
                            enabled = !isSubmitting && subject.trim().length >= 3 && message.trim().length >= 5,
                            onClick = {
                                isSubmitting = true
                                scope.launch {
                                    val res = DriverRepository.createDriverTicket(subject, message)
                                    isSubmitting = false
                                    if (res.isSuccess) {
                                        subject = ""
                                        message = ""
                                        showCreateForm = false
                                        onShowToast("Support ticket created!")
                                    } else {
                                        onShowToast("Failed: ${res.exceptionOrNull()?.message}")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            if (tickets.isEmpty()) {
                item {
                    CockpitCard {
                        Text("No support tickets found.", color = CockpitTextMuted, fontSize = 13.sp)
                    }
                }
            } else {
                items(tickets) { ticket ->
                    val statusColor = if (ticket.status.equals("RESOLVED", ignoreCase = true) ||
                        ticket.status.equals("CLOSED", ignoreCase = true)
                    ) EmeraldPrimary else AmberWarning
                    CockpitCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(ticket.ticketId.take(10), color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(ticket.subject, color = CockpitTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Created: ${ticket.createdAt}", color = CockpitTextSecondary, fontSize = 11.sp)
                            }
                            CockpitBadge(ticket.status, statusColor)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Screen 49: Push Notifications Center (GET /api/v1/notifications/inbox & PATCH .../read)
 */
@Composable
fun NotificationsCenterScreen(
    onNavigateBack: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    val notifications by DriverRepository.notifications.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        DriverRepository.fetchNotifications()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Pilot Notifications",
            subtitle = "Surge triggers, weather warnings & announcements",
            onBack = onNavigateBack
        )

        if (notifications.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                Text("No notifications yet.", color = CockpitTextMuted, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(notifications) { notif ->
                    val tint = if (notif.isRead) CockpitTextMuted else EmeraldPrimary
                    CockpitCard(
                        glow = !notif.isRead,
                        modifier = Modifier.clickable {
                            if (!notif.isRead) {
                                scope.launch { DriverRepository.markNotificationAsRead(notif.id) }
                            }
                        }
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(tint.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Notifications, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(notif.title, color = CockpitTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(notif.createdAt?.take(10) ?: "", color = CockpitTextMuted, fontSize = 11.sp)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(notif.body, color = CockpitTextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}