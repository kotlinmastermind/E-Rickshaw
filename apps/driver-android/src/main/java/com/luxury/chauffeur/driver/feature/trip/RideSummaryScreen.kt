package com.luxury.chauffeur.driver.feature.trip

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luxury.chauffeur.driver.core.designsystem.*

/**
 * Itemized fare breakdown data model.
 */
data class ItemizedFareBreakdown(
    val baseFareRupees: Double = 100.0,
    val distanceKm: Double = 8.5,
    val perKmRateRupees: Double = 25.0,
    val distanceFareRupees: Double = 212.5,
    val durationMinutes: Double = 22.0,
    val perMinuteRateRupees: Double = 3.0,
    val timeFareRupees: Double = 66.0,
    val waitingMinutes: Double = 5.0,
    val waitingChargeRupees: Double = 30.0,
    val tollAndSurchargesRupees: Double = 0.0,
    val platformCommissionPercent: Double = 20.0
) {
    val totalFareRupees: Double
        get() = baseFareRupees + distanceFareRupees + timeFareRupees + waitingChargeRupees + tollAndSurchargesRupees

    val commissionRupees: Double
        get() = totalFareRupees * (platformCommissionPercent / 100.0)

    val driverNetEarningsRupees: Double
        get() = totalFareRupees - commissionRupees
}

/**
 * Ride completion summary & payment collection confirmation screen.
 */
@Composable
fun RideSummaryScreen(
    rideId: String,
    pickupAddress: String,
    destinationAddress: String,
    customerName: String = "VIP Commuter",
    fareBreakdown: ItemizedFareBreakdown = ItemizedFareBreakdown(),
    onConfirmAndClose: (paymentCollected: Boolean, customerRating: Int) -> Unit
) {
    var cashCollectedConfirmed by remember { mutableStateOf(false) }
    var selectedRating by remember { mutableIntStateOf(5) }
    var isSubmitting by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = DriverColors.MidnightBlack,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("TRIP SETTLEMENT & SUMMARY", style = DriverTypography.Title.copy(fontSize = 17.sp, color = DriverColors.ChauffeurGold))
                GoldBadge("JOURNEY CONCLUDED")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Total Fare & Net Earnings Card
                LuxCard(
                    borderColor = DriverColors.ChauffeurGold
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "TOTAL TRIP FARE",
                            style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.TextMuted, fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "₹%.2f".format(fareBreakdown.totalFareRupees),
                            style = DriverTypography.MetricValue.copy(fontSize = 36.sp, color = DriverColors.ChauffeurGold)
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = DriverColors.StatusOnline.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, DriverColors.StatusOnline)
                        ) {
                            Text(
                                text = "Pilot Net Payout: ₹%.2f (80%%)".format(fareBreakdown.driverNetEarningsRupees),
                                color = DriverColors.StatusOnline,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Itemized Fare Breakdown Card
                LuxCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "ITEMIZED FARE BREAKUP",
                            style = DriverTypography.Subtitle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DriverColors.ChauffeurGold)
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Base Fare", style = DriverTypography.Body)
                            Text("₹%.2f".format(fareBreakdown.baseFareRupees), style = DriverTypography.Body.copy(fontWeight = FontWeight.SemiBold))
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Distance (%.1f km @ ₹%.0f/km)".format(fareBreakdown.distanceKm, fareBreakdown.perKmRateRupees), style = DriverTypography.Body)
                            Text("₹%.2f".format(fareBreakdown.distanceFareRupees), style = DriverTypography.Body.copy(fontWeight = FontWeight.SemiBold))
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Ride Duration (%.0f min @ ₹%.0f/min)".format(fareBreakdown.durationMinutes, fareBreakdown.perMinuteRateRupees), style = DriverTypography.Body)
                            Text("₹%.2f".format(fareBreakdown.timeFareRupees), style = DriverTypography.Body.copy(fontWeight = FontWeight.SemiBold))
                        }

                        if (fareBreakdown.waitingMinutes > 0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Wait Time (%.0f min)".format(fareBreakdown.waitingMinutes), style = DriverTypography.Body)
                                Text("₹%.2f".format(fareBreakdown.waitingChargeRupees), style = DriverTypography.Body.copy(fontWeight = FontWeight.SemiBold))
                            }
                        }

                        HorizontalDivider(color = DriverColors.SurfaceBorder, thickness = 0.8.dp)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Platform Commission (%.0f%%)".format(fareBreakdown.platformCommissionPercent), style = DriverTypography.Body.copy(color = DriverColors.TextMuted))
                            Text("-₹%.2f".format(fareBreakdown.commissionRupees), style = DriverTypography.Body.copy(color = DriverColors.AlertDanger))
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Net Driver Payout", style = DriverTypography.Subtitle.copy(fontWeight = FontWeight.Bold))
                            Text("₹%.2f".format(fareBreakdown.driverNetEarningsRupees), style = DriverTypography.Subtitle.copy(fontWeight = FontWeight.Bold, color = DriverColors.StatusOnline))
                        }
                    }
                }

                // Route Details
                LuxCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("ROUTE RECAP", style = DriverTypography.Subtitle.copy(fontSize = 12.sp, color = DriverColors.ChauffeurGold))
                        Text("📍 Pickup: $pickupAddress", style = DriverTypography.Body.copy(fontSize = 12.sp))
                        Text("🏁 Destination: $destinationAddress", style = DriverTypography.Body.copy(fontSize = 12.sp))
                    }
                }

                // Rate Passenger
                LuxCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Rate Commuter ($customerName)",
                            style = DriverTypography.Body.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (1..5).forEach { star ->
                                Text(
                                    text = if (star <= selectedRating) "★" else "☆",
                                    fontSize = 28.sp,
                                    color = DriverColors.ChauffeurGold,
                                    modifier = Modifier.clickable { selectedRating = star }
                                )
                            }
                        }
                    }
                }

                // Payment Confirmation Checkbox
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DriverColors.CockpitDark,
                    border = BorderStroke(1.dp, if (cashCollectedConfirmed) DriverColors.StatusOnline else DriverColors.ChauffeurGold),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { cashCollectedConfirmed = !cashCollectedConfirmed }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = cashCollectedConfirmed,
                            onCheckedChange = { cashCollectedConfirmed = it },
                            colors = CheckboxDefaults.colors(checkedColor = DriverColors.ChauffeurGold)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "I confirm receipt of ₹%.2f via Cash / UPI from customer.".format(fareBreakdown.totalFareRupees),
                            style = DriverTypography.Body.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            PrimaryButton(
                text = "CONFIRM SETTLEMENT & RETURN TO COCKPIT",
                isLoading = isSubmitting,
                enabled = cashCollectedConfirmed,
                onClick = {
                    isSubmitting = true
                    onConfirmAndClose(cashCollectedConfirmed, selectedRating)
                }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RideSummaryScreenPreview() {
    RideSummaryScreen(
        rideId = "RIDE-1044",
        pickupAddress = "Patna Junction Railway Station",
        destinationAddress = "AIIMS Patna, Phulwari Sharif",
        customerName = "Ravi Kumar",
        onConfirmAndClose = { _, _ -> }
    )
}

