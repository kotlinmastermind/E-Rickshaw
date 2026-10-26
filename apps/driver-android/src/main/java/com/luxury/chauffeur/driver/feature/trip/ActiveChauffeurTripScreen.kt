package com.luxury.chauffeur.driver.feature.trip

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luxury.chauffeur.driver.core.designsystem.*
import com.luxury.chauffeur.driver.core.network.DriverApiClient
import com.luxury.chauffeur.driver.core.network.StartTripPayload
import com.luxury.chauffeur.driver.core.network.FinishTripPayload
import kotlinx.coroutines.launch

enum class ChauffeurTripStage {
    NAVIGATING_TO_PICKUP,
    ARRIVED_AT_PICKUP,
    OTP_VERIFICATION,
    TRIP_IN_PROGRESS,
    TRIP_FINISHED
}

@Composable
fun ActiveChauffeurTripScreen(
    rideId: String,
    pickupAddress: String,
    destinationAddress: String,
    onTripFinished: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var stage by remember { mutableStateOf(ChauffeurTripStage.NAVIGATING_TO_PICKUP) }
    var enteredOtp by remember { mutableStateOf("") }
    var otpError by remember { mutableStateOf<String?>(null) }
    var cashCollectedConfirmed by remember { mutableStateOf(false) }
    var isActionLoading by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = DriverColors.MidnightBlack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                // Stage Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (stage) {
                            ChauffeurTripStage.NAVIGATING_TO_PICKUP -> "HEADING TO PICKUP"
                            ChauffeurTripStage.ARRIVED_AT_PICKUP -> "AT PICKUP LOCATION"
                            ChauffeurTripStage.OTP_VERIFICATION -> "VERIFY GUEST PASSCODE"
                            ChauffeurTripStage.TRIP_IN_PROGRESS -> "EN ROUTE TO DESTINATION"
                            ChauffeurTripStage.TRIP_FINISHED -> "JOURNEY CONCLUDED"
                        },
                        style = DriverTypography.Subtitle.copy(color = DriverColors.ChauffeurGold, fontWeight = FontWeight.Bold)
                    )
                    Text("Ride #$rideId", style = DriverTypography.Body.copy(color = DriverColors.TextMuted, fontSize = 12.sp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Simulated Google Navigation Banner & Turn-by-Turn Telemetry
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = DriverColors.CockpitDark,
                    border = BorderStroke(1.dp, DriverColors.SurfaceBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (stage == ChauffeurTripStage.NAVIGATING_TO_PICKUP || stage == ChauffeurTripStage.ARRIVED_AT_PICKUP)
                                    "PICKUP: $pickupAddress"
                                else
                                    "DESTINATION: $destinationAddress",
                                style = DriverTypography.Body.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = when (stage) {
                                    ChauffeurTripStage.NAVIGATING_TO_PICKUP -> "In 200m, turn left onto Marine Drive • ETA: 3 min"
                                    ChauffeurTripStage.ARRIVED_AT_PICKUP -> "Vehicle parked at Portico Entrance"
                                    ChauffeurTripStage.OTP_VERIFICATION -> "Waiting for guest security verification"
                                    ChauffeurTripStage.TRIP_IN_PROGRESS -> "Take Western Express Flyover • 18.2 km remaining"
                                    ChauffeurTripStage.TRIP_FINISHED -> "VIP Passenger Disembarked"
                                },
                                style = DriverTypography.Body.copy(color = DriverColors.ChauffeurGold, fontSize = 13.sp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Phase 3: Pooled Passenger Manifest & Real-Time Capacity Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DriverColors.CockpitDark,
                    border = BorderStroke(1.dp, DriverColors.SurfaceBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("POOLED PASSENGER MANIFEST", style = DriverTypography.Subtitle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = DriverColors.SurfaceCard,
                                border = BorderStroke(1.dp, DriverColors.ChauffeurGold)
                            ) {
                                Text(
                                    text = "OCCUPANCY: 2 / 4 SEATS",
                                    style = DriverTypography.Body.copy(color = DriverColors.ChauffeurGold, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("1. Guest Lord Sterling (2 Seats)", style = DriverTypography.Body.copy(fontSize = 12.sp))
                            Text("BOARDED", style = DriverTypography.Body.copy(color = DriverColors.AlertSuccess, fontSize = 11.sp, fontWeight = FontWeight.Bold))
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("2. Guest Lady Kensington (1 Seat)", style = DriverTypography.Body.copy(fontSize = 12.sp))
                            Text("NEXT PICKUP", style = DriverTypography.Body.copy(color = DriverColors.ChauffeurGold, fontSize = 11.sp, fontWeight = FontWeight.Bold))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Feature 7: Trip Start OTP Verification Step
                if (stage == ChauffeurTripStage.OTP_VERIFICATION) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = DriverColors.SurfaceCard,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("ENTER 6-DIGIT PASSCODE", style = DriverTypography.Subtitle)
                            Text("Kindly request the passcode displayed on the guest's mobile application.", style = DriverTypography.Body.copy(fontSize = 12.sp, color = DriverColors.TextMuted))
                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedTextField(
                                value = enteredOtp,
                                onValueChange = { if (it.length <= 6) enteredOtp = it },
                                label = { Text("6-Digit Code", color = DriverColors.TextMuted) },
                                textStyle = DriverTypography.MetricValue.copy(letterSpacing = 6.sp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = DriverColors.ChauffeurGold,
                                    unfocusedBorderColor = DriverColors.SurfaceBorder,
                                    focusedTextColor = DriverColors.ChauffeurGold,
                                    unfocusedTextColor = DriverColors.ChauffeurGold
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (otpError != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(otpError ?: "", color = DriverColors.AlertDanger, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Feature 9: Finish Ride & Cash Collection Confirmation
                if (stage == ChauffeurTripStage.TRIP_FINISHED) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = DriverColors.SurfaceCard,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("FARE SUMMARY", style = DriverTypography.Subtitle)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total Trip Fare", style = DriverTypography.Body)
                                Text("₹1,600.00", style = DriverTypography.Subtitle)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Chauffeur Net Earning (80%)", style = DriverTypography.Body)
                                Text("₹1,280.00", style = DriverTypography.MetricValue.copy(fontSize = 20.sp))
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = cashCollectedConfirmed,
                                    onCheckedChange = { cashCollectedConfirmed = it },
                                    colors = CheckboxDefaults.colors(checkedColor = DriverColors.ChauffeurGold)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("I confirm direct cash collection / verified UPI receipt", style = DriverTypography.Body.copy(fontSize = 12.sp))
                            }
                        }
                    }
                }
            }

            // Contextual Lifecycle Action Buttons
            Column(modifier = Modifier.padding(bottom = 16.dp)) {
                when (stage) {
                    ChauffeurTripStage.NAVIGATING_TO_PICKUP -> {
                        Button(
                            onClick = {
                                if (isActionLoading) return@Button
                                isActionLoading = true
                                scope.launch {
                                    try {
                                        DriverApiClient.getService().notifyArrived(rideId)
                                    } catch (_: Exception) {}
                                    stage = ChauffeurTripStage.ARRIVED_AT_PICKUP
                                    isActionLoading = false
                                }
                            },
                            enabled = !isActionLoading,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DriverColors.ChauffeurGold, contentColor = DriverColors.MidnightBlack),
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Text(if (isActionLoading) "UPDATING ARRIVAL..." else "I HAVE ARRIVED AT PICKUP", fontWeight = FontWeight.Bold)
                        }
                    }
                    ChauffeurTripStage.ARRIVED_AT_PICKUP -> {
                        Button(
                            onClick = { stage = ChauffeurTripStage.OTP_VERIFICATION },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DriverColors.ChauffeurGold, contentColor = DriverColors.MidnightBlack),
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Text("VERIFY GUEST & START TRIP", fontWeight = FontWeight.Bold)
                        }
                    }
                    ChauffeurTripStage.OTP_VERIFICATION -> {
                        Button(
                            onClick = {
                                if (isActionLoading) return@Button
                                if (enteredOtp.length < 6) {
                                    otpError = "Please enter the full 6-digit passcode."
                                    return@Button
                                }
                                isActionLoading = true
                                otpError = null
                                scope.launch {
                                    try {
                                        val res = DriverApiClient.getService().startTrip(rideId, StartTripPayload(otpCode = enteredOtp.trim()))
                                        if (res.isSuccessful) {
                                            stage = ChauffeurTripStage.TRIP_IN_PROGRESS
                                        } else {
                                            val err = res.errorBody()?.string() ?: ""
                                            otpError = if (err.contains("Incorrect OTP") || err.contains("detail")) {
                                                "Invalid passcode. Please request correct code from guest."
                                            } else {
                                                "Verification failed. Recheck passcode."
                                            }
                                        }
                                    } catch (_: Exception) {
                                        stage = ChauffeurTripStage.TRIP_IN_PROGRESS
                                    } finally {
                                        isActionLoading = false
                                    }
                                }
                            },
                            enabled = !isActionLoading,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DriverColors.StatusOnline, contentColor = DriverColors.MidnightBlack),
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Text(if (isActionLoading) "VERIFYING PASSCODE..." else "AUTHORIZE & COMMENCE TRIP", fontWeight = FontWeight.Bold)
                        }
                    }
                    ChauffeurTripStage.TRIP_IN_PROGRESS -> {
                        Button(
                            onClick = {
                                if (isActionLoading) return@Button
                                isActionLoading = true
                                scope.launch {
                                    try {
                                        DriverApiClient.getService().finishTrip(
                                            rideId,
                                            FinishTripPayload(actualDistanceKm = 24.8, actualDurationMinutes = 38.0)
                                        )
                                    } catch (_: Exception) {}
                                    stage = ChauffeurTripStage.TRIP_FINISHED
                                    isActionLoading = false
                                }
                            },
                            enabled = !isActionLoading,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DriverColors.ChauffeurGold, contentColor = DriverColors.MidnightBlack),
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Text(if (isActionLoading) "CONCLUDING TRIP..." else "COMPLETE VIP TRIP", fontWeight = FontWeight.Bold)
                        }
                    }
                    ChauffeurTripStage.TRIP_FINISHED -> {
                        Button(
                            onClick = onTripFinished,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DriverColors.StatusOnline, contentColor = DriverColors.MidnightBlack),
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Text("CONFIRM SETTLEMENT & RETURN TO COCKPIT", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
