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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaisentra.mobility.driver.components.*
import com.vaisentra.mobility.driver.data.DriverRepository
import com.vaisentra.mobility.driver.data.DriverLocationManager
import com.vaisentra.mobility.driver.data.DriverServiceAreaRequestDto
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
    val liveIncidents by DriverRepository.driverIncidents.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        DriverRepository.fetchDriverIncidents()
    }

    val foundItems = remember(liveIncidents) {
        liveIncidents.filter {
            it.category == "FOUND_LOST_ITEM" || it.description.contains("Found item", ignoreCase = true)
        }
    }

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
                                colors = RadioButtonDefaults.colors(selectedColor = PilotElectricCyan)
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
                    text = if (isSubmitting) "Submitting to Supabase..." else "Report Found Item & Lock Ticket",
                    enabled = !isSubmitting && itemNotes.trim().isNotEmpty(),
                    onClick = {
                        val cleanNotes = itemNotes.trim()
                        val validNotes = if (cleanNotes.length < 10) "Found item ($itemCategory): $cleanNotes (in cab)" else cleanNotes
                        isSubmitting = true
                        scope.launch {
                            val res = DriverRepository.reportFoundItem(rideId, itemCategory, validNotes)
                            isSubmitting = false
                            if (res.isSuccess) {
                                itemNotes = ""
                                onShowToast("Found item logged successfully to Supabase!")
                                DriverRepository.fetchDriverIncidents()
                            } else {
                                onShowToast("Failed to report item: ${res.exceptionOrNull()?.message}")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("LOGGED CAB BELONGINGS", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("Table: incident_reports", color = PilotElectricCyan, fontSize = 10.sp)
                }
            }

            if (foundItems.isEmpty()) {
                item {
                    CockpitCard {
                        Text(
                            text = "No lost & found items recorded yet. Any passenger belongings logged will be recorded in Supabase.",
                            color = CockpitTextMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                items(foundItems) { itemReport ->
                    val isResolved = itemReport.status.uppercase() == "RESOLVED"
                    val badgeColor = if (isResolved) PilotOnlineGreen else PilotAmber

                    CockpitCard(cornerRadius = 12.dp) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = itemReport.ticketNumber.ifBlank { "INC-LOST-ITEM" },
                                color = CockpitTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            CockpitBadge(
                                text = itemReport.status,
                                backgroundColor = badgeColor.copy(alpha = 0.2f),
                                textColor = badgeColor
                            )
                        }

                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = itemReport.description,
                            color = CockpitTextSecondary,
                            fontSize = 12.sp
                        )

                        if (!itemReport.createdAt.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Logged: ${itemReport.createdAt.take(19).replace("T", " ")}",
                                color = CockpitTextMuted,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
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
    var serviceState by remember { mutableStateOf("") }
    var serviceDistrict by remember { mutableStateOf("") }
    var serviceLocality by remember { mutableStateOf("") }
    var servicePostalCode by remember { mutableStateOf("") }
    var serviceAreaRequests by remember { mutableStateOf<List<DriverServiceAreaRequestDto>>(emptyList()) }
    var isAreaSubmitting by remember { mutableStateOf(false) }
    val currentLocation by DriverLocationManager.currentLocation.collectAsState()
    val hasFreshGpsFix = currentLocation.isLiveGps && currentLocation.hasValidCoordinates() &&
        currentLocation.lastUpdatedTimestamp > 0L &&
        System.currentTimeMillis() - currentLocation.lastUpdatedTimestamp <= 120_000L
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        DriverRepository.fetchDriverTickets()
        DriverRepository.fetchDriverServiceAreaRequests().onSuccess { serviceAreaRequests = it }
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
                CockpitCard {
                    Text("EXPAND VAISENTRA TO YOUR LOCALITY", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Tell us where you want to operate. A locality request does not activate rides automatically; the operational boundary and tariff must be configured and reviewed first.",
                        color = CockpitTextMuted,
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    CockpitTextField(value = serviceState, onValueChange = { serviceState = it }, label = "State", placeholder = "e.g. Bihar")
                    Spacer(Modifier.height(7.dp))
                    CockpitTextField(value = serviceDistrict, onValueChange = { serviceDistrict = it }, label = "District", placeholder = "e.g. Nalanda")
                    Spacer(Modifier.height(7.dp))
                    CockpitTextField(value = serviceLocality, onValueChange = { serviceLocality = it }, label = "City / Town / Village / Locality", placeholder = "e.g. Bihar Sharif")
                    Spacer(Modifier.height(7.dp))
                    CockpitTextField(value = servicePostalCode, onValueChange = { servicePostalCode = it.filter(Char::isDigit).take(6) }, label = "PIN code (optional)", placeholder = "6-digit PIN")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (hasFreshGpsFix) "GPS verified: ${"%.5f".format(currentLocation.latitude)}, ${"%.5f".format(currentLocation.longitude)}"
                        else "A fresh GPS fix (within 2 minutes) is required to submit this locality.",
                        color = if (hasFreshGpsFix) PilotOnlineGreen else PilotAmber,
                        fontSize = 10.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    CockpitButton(
                        text = if (isAreaSubmitting) "Submitting locality..." else "Request Service in This Locality",
                        enabled = !isAreaSubmitting && hasFreshGpsFix &&
                            serviceState.trim().length >= 2 &&
                            serviceDistrict.trim().length >= 2 &&
                            serviceLocality.trim().length >= 2 &&
                            (servicePostalCode.isBlank() || servicePostalCode.length == 6),
                        onClick = {
                            isAreaSubmitting = true
                            scope.launch {
                                val result = DriverRepository.submitDriverServiceAreaRequest(
                                    stateName = serviceState,
                                    districtName = serviceDistrict,
                                    localityName = serviceLocality,
                                    postalCode = servicePostalCode,
                                    latitude = currentLocation.latitude,
                                    longitude = currentLocation.longitude
                                )
                                isAreaSubmitting = false
                                if (result.isSuccess) {
                                    serviceState = ""
                                    serviceDistrict = ""
                                    serviceLocality = ""
                                    servicePostalCode = ""
                                    DriverRepository.fetchDriverServiceAreaRequests().onSuccess { serviceAreaRequests = it }
                                    onShowToast("Locality request submitted for operational review.")
                                } else {
                                    onShowToast(result.exceptionOrNull()?.message ?: "Locality request failed.")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (serviceAreaRequests.isNotEmpty()) {
                item {
                    Text("MY LOCALITY REQUESTS", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                items(serviceAreaRequests) { request ->
                    CockpitCard(cornerRadius = 12.dp) {
                        Text("${request.localityName}, ${request.districtName}, ${request.stateName}", color = PilotTextWhite, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("Status: ${request.status}", color = if (request.status == "APPROVED") PilotOnlineGreen else PilotAmber, fontSize = 11.sp)
                        request.adminResponse?.takeIf { it.isNotBlank() }?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(it, color = CockpitTextMuted, fontSize = 11.sp)
                        }
                    }
                }
            }

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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("NEW SUPPORT TICKET", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("Target: driver_onboarding_tickets", color = PilotElectricCyan, fontSize = 10.sp)
                        }
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
                            text = if (isSubmitting) "Submitting to Supabase..." else "Submit Ticket",
                            enabled = !isSubmitting && subject.trim().length >= 4 && message.trim().length >= 10,
                            onClick = {
                                isSubmitting = true
                                scope.launch {
                                    val res = DriverRepository.createDriverTicket(subject, message)
                                    isSubmitting = false
                                    if (res.isSuccess) {
                                        subject = ""
                                        message = ""
                                        showCreateForm = false
                                        onShowToast("Support ticket created in Supabase!")
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

            item {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("REGISTERED HELPDESK TICKETS", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("Table: driver_onboarding_tickets", color = PilotElectricCyan, fontSize = 10.sp)
                }
            }

            if (tickets.isEmpty()) {
                item {
                    CockpitCard {
                        Text(
                            text = "No support tickets found in Supabase. Raise a new ticket above to get 24x7 pilot partner support.",
                            color = CockpitTextMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                items(tickets) { ticket ->
                    val isResolved = ticket.status.equals("RESOLVED", ignoreCase = true) ||
                        ticket.status.equals("CLOSED", ignoreCase = true)
                    val statusColor = if (isResolved) PilotOnlineGreen else PilotAmber

                    CockpitCard(cornerRadius = 14.dp) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = ticket.ticketId.take(12).ifBlank { "TICKET-ID" },
                                color = CockpitTextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            CockpitBadge(
                                text = ticket.status,
                                backgroundColor = statusColor.copy(alpha = 0.2f),
                                textColor = statusColor
                            )
                        }

                        Spacer(Modifier.height(6.dp))
                        Text(ticket.subject, color = CockpitTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)

                        if (ticket.message.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(ticket.message, color = CockpitTextSecondary, fontSize = 12.sp)
                        }

                        if (!ticket.adminResponse.isNullOrBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CockpitCardBackground)
                                    .padding(8.dp)
                            ) {
                                Column {
                                    Text("OPERATIONS TEAM RESPONSE", color = PilotElectricCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(2.dp))
                                    Text(ticket.adminResponse, color = CockpitTextPrimary, fontSize = 12.sp)
                                }
                            }
                        }

                        if (ticket.createdAt.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Created: ${ticket.createdAt.take(19).replace("T", " ")}",
                                color = CockpitTextMuted,
                                fontSize = 10.sp
                            )
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

@Preview(showBackground = true)
@Composable
fun DriverHelpdeskScreenPreview() {
    DriverGoTheme {
        DriverHelpdeskScreen()
    }
}

@Preview(showBackground = true)
@Composable
fun NotificationsCenterScreenPreview() {
    DriverGoTheme {
        NotificationsCenterScreen()
    }
}