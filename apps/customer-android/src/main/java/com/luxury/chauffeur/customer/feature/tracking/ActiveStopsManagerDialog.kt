package com.luxury.chauffeur.customer.feature.tracking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.luxury.chauffeur.customer.core.designsystem.*
import com.luxury.chauffeur.customer.core.network.*
import kotlinx.coroutines.launch

@Composable
fun ActiveStopsManagerDialog(
    rideId: String,
    currentStops: List<RideStopDto>,
    onDismiss: () -> Unit,
    onStopsUpdated: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var showAddForm by remember { mutableStateOf(false) }
    var newAddress by remember { mutableStateOf("") }
    var newDwellMinutes by remember { mutableIntStateOf(10) }
    var newInstructions by remember { mutableStateOf("") }
    var newLandmark by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var editingStopId by remember { mutableStateOf<String?>(null) }
    var editDwellMinutes by remember { mutableIntStateOf(10) }
    var editInstructions by remember { mutableStateOf("") }

    val dwellPresets = listOf(5, 10, 15, 30, 45, 60)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(LuxuryDimensions.RadiusMedium),
            color = LuxuryColors.CardSurface,
            border = BorderStroke(1.dp, LuxuryColors.ChampagneGold),
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("ACTIVE TRIP WAYPOINTS", style = LuxuryTypography.TitleGold.copy(fontSize = 15.sp))
                    TextButton(onClick = onDismiss) {
                        Text("✕", color = LuxuryColors.SubtleGray, fontSize = 16.sp)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Add, remove, or modify intermediate stops with chauffeur dwell times during your active journey.",
                    style = LuxuryTypography.BodySubtle
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (showAddForm) {
                    Text("ADD INTERMEDIATE STOP", style = LuxuryTypography.BodyRegular.copy(fontWeight = FontWeight.Bold))
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = newAddress,
                        onValueChange = { newAddress = it },
                        label = { Text("Stop Address / Landmark") },
                        textStyle = LuxuryTypography.BodyRegular,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Text("DWELL TIME (WAITING PERIOD)", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold))
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        dwellPresets.forEach { mins ->
                            val isSelected = newDwellMinutes == mins
                            Surface(
                                onClick = { newDwellMinutes = mins },
                                shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                                color = if (isSelected) LuxuryColors.ChampagneGold else LuxuryColors.ObsidianBlack,
                                border = BorderStroke(1.dp, if (isSelected) LuxuryColors.ChampagneGold else LuxuryColors.CardBorder),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(modifier = Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        "${mins}m",
                                        style = LuxuryTypography.BodySubtle.copy(
                                            color = if (isSelected) LuxuryColors.ObsidianBlack else LuxuryColors.PlatinumWhite,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newInstructions,
                        onValueChange = { newInstructions = it },
                        label = { Text("Chauffeur & Passenger Instructions") },
                        placeholder = { Text("e.g. Wait at VIP lobby with umbrella", color = LuxuryColors.SubtleGray) },
                        textStyle = LuxuryTypography.BodyRegular,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newLandmark,
                        onValueChange = { newLandmark = it },
                        label = { Text("Entrance / Landmark Guidance") },
                        placeholder = { Text("e.g. Gate 4 South Portico", color = LuxuryColors.SubtleGray) },
                        textStyle = LuxuryTypography.BodyRegular,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (newAddress.isBlank() || isSubmitting) return@Button
                                isSubmitting = true
                                scope.launch {
                                    try {
                                        ApiClient.getService().addRideStop(
                                            rideId,
                                            AddStopRequest(
                                                address = newAddress.trim(),
                                                latitude = 19.0760,
                                                longitude = 72.8777,
                                                sequenceOrder = currentStops.size + 1,
                                                dwellTimeMinutes = newDwellMinutes,
                                                passengerInstructions = newInstructions.ifBlank { null },
                                                entranceLandmark = newLandmark.ifBlank { null }
                                            )
                                        )
                                        showAddForm = false
                                        newAddress = ""
                                        newInstructions = ""
                                        newLandmark = ""
                                        onStopsUpdated()
                                    } catch (_: Exception) {}
                                    isSubmitting = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.ChampagneGold),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (isSubmitting) "ADDING..." else "CONFIRM STOP", color = LuxuryColors.ObsidianBlack, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { showAddForm = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("CANCEL", color = LuxuryColors.PlatinumWhite)
                        }
                    }
                } else {
                    if (currentStops.isEmpty()) {
                        Text(
                            "No intermediate stops currently scheduled for this journey. You may add waypoints with designated waiting periods.",
                            style = LuxuryTypography.BodySubtle,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 240.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(currentStops) { stop ->
                                Surface(
                                    shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                                    color = LuxuryColors.ObsidianBlack,
                                    border = BorderStroke(1.dp, LuxuryColors.CardBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                GoldBadge("Stop ${stop.sequenceOrder}")
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("${stop.dwellTimeMinutes} mins dwell", style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.ChampagneGold))
                                            }

                                            Row {
                                                IconButton(
                                                    onClick = {
                                                        editingStopId = stop.id
                                                        editDwellMinutes = stop.dwellTimeMinutes
                                                        editInstructions = stop.passengerInstructions ?: ""
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Text("✎", fontSize = 12.sp, color = LuxuryColors.ChampagneGold)
                                                }
                                                IconButton(
                                                    onClick = {
                                                        scope.launch {
                                                            try {
                                                                ApiClient.getService().deleteRideStop(rideId, stop.id)
                                                                onStopsUpdated()
                                                            } catch (_: Exception) {}
                                                        }
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Text("🗑", fontSize = 12.sp, color = LuxuryColors.CrimsonAlert)
                                                }
                                            }
                                        }

                                        Text(stop.address, style = LuxuryTypography.BodyRegular, maxLines = 1)
                                        if (!stop.passengerInstructions.isNullOrBlank()) {
                                            Text("Instructions: ${stop.passengerInstructions}", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))
                                        }
                                        if (!stop.entranceLandmark.isNullOrBlank()) {
                                            Text("Landmark: ${stop.entranceLandmark}", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp, color = LuxuryColors.ChampagneGold))
                                        }

                                        if (editingStopId == stop.id) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Divider(color = LuxuryColors.CardBorder)
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text("Update Dwell Time:", style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                dwellPresets.forEach { m ->
                                                    Button(
                                                        onClick = { editDwellMinutes = m },
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = if (editDwellMinutes == m) LuxuryColors.ChampagneGold else LuxuryColors.CardBorder
                                                        ),
                                                        modifier = Modifier.weight(1f).height(28.dp),
                                                        contentPadding = PaddingValues(0.dp)
                                                    ) {
                                                        Text("${m}m", fontSize = 10.sp, color = if (editDwellMinutes == m) LuxuryColors.ObsidianBlack else LuxuryColors.PlatinumWhite)
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            OutlinedTextField(
                                                value = editInstructions,
                                                onValueChange = { editInstructions = it },
                                                label = { Text("Instructions") },
                                                textStyle = LuxuryTypography.BodyRegular,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Button(
                                                    onClick = {
                                                        scope.launch {
                                                            try {
                                                                ApiClient.getService().updateRideStop(
                                                                    rideId,
                                                                    stop.id,
                                                                    UpdateStopRequest(
                                                                        dwellTimeMinutes = editDwellMinutes,
                                                                        passengerInstructions = editInstructions.ifBlank { null }
                                                                    )
                                                                )
                                                                editingStopId = null
                                                                onStopsUpdated()
                                                            } catch (_: Exception) {}
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.ChampagneGold),
                                                    modifier = Modifier.weight(1f).height(32.dp),
                                                    contentPadding = PaddingValues(0.dp)
                                                ) {
                                                    Text("SAVE", color = LuxuryColors.ObsidianBlack, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                                }
                                                OutlinedButton(
                                                    onClick = { editingStopId = null },
                                                    modifier = Modifier.weight(1f).height(32.dp),
                                                    contentPadding = PaddingValues(0.dp)
                                                ) {
                                                    Text("CANCEL", fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { showAddForm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.ChampagneGold),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("+ ADD INTERMEDIATE STOP", color = LuxuryColors.ObsidianBlack, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
