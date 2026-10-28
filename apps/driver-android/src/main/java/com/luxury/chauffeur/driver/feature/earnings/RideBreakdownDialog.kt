package com.luxury.chauffeur.driver.feature.earnings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.luxury.chauffeur.driver.core.designsystem.DriverColors
import com.luxury.chauffeur.driver.core.designsystem.DriverTypography
import com.luxury.chauffeur.driver.core.designsystem.GoldBadge

@Composable
fun RideBreakdownDialog(
    breakdown: RideBreakdownDto,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = DriverColors.CockpitDark,
            border = BorderStroke(1.dp, DriverColors.ChauffeurGold),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("RIDE FARE BREAKDOWN", style = DriverTypography.Title.copy(fontSize = 17.sp, color = DriverColors.ChauffeurGold))
                    GoldBadge(breakdown.settlementStatus)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Addresses
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DriverColors.SurfaceCard,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Pickup: ${breakdown.pickupAddress}", style = DriverTypography.Body.copy(fontSize = 12.sp, color = DriverColors.TextPure), maxLines = 1)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Destination: ${breakdown.destinationAddress}", style = DriverTypography.Body.copy(fontSize = 12.sp, color = DriverColors.TextMuted), maxLines = 1)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("${breakdown.distanceKm} km", style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.ChauffeurGold))
                            Text("${breakdown.durationMinutes.toInt()} mins", style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.ChauffeurGold))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text("GROSS FARE COMPONENTS", style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.TextMuted))
                Spacer(modifier = Modifier.height(6.dp))

                BreakdownRow("Base Chauffeur Flag-drop", "₹%,.2f".format(breakdown.fareBreakdown.baseFareCents / 100.0))
                BreakdownRow("Distance Fare", "₹%,.2f".format(breakdown.fareBreakdown.distanceFareCents / 100.0))
                BreakdownRow("Time & Waiting Fare", "₹%,.2f".format(breakdown.fareBreakdown.timeFareCents / 100.0))
                BreakdownRow("VIP Amenities & Concierge", "₹%,.2f".format(breakdown.fareBreakdown.amenitiesConciergeCents / 100.0))

                Divider(color = DriverColors.SurfaceBorder, modifier = Modifier.padding(vertical = 8.dp))
                BreakdownRow("Gross Customer Fare", "₹%,.2f".format(breakdown.fareBreakdown.grossFareCents / 100.0), isBold = true)

                Spacer(modifier = Modifier.height(12.dp))
                Text("DEDUCTIONS & SUPPLEMENTS", style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.TextMuted))
                Spacer(modifier = Modifier.height(6.dp))

                BreakdownRow("Platform Commission (20%)", "-₹%,.2f".format(breakdown.deductions.platformCommissionCents / 100.0), color = DriverColors.AlertDanger)
                BreakdownRow("Customer Gratuity (100% kept)", "+₹%,.2f".format(breakdown.deductions.customerTipCents / 100.0), color = DriverColors.StatusOnline)
                if (breakdown.deductions.tollReimbursementCents > 0) {
                    BreakdownRow("Toll & Airport Pass Reimbursement", "+₹%,.2f".format(breakdown.deductions.tollReimbursementCents / 100.0), color = DriverColors.StatusOnline)
                }

                Divider(color = DriverColors.ChauffeurGold.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 10.dp))

                // Net take-home
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DriverColors.SurfaceCard,
                    border = BorderStroke(1.dp, DriverColors.StatusOnline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("NET CHAUFFEUR EARNINGS", style = DriverTypography.Body.copy(fontSize = 10.sp, color = DriverColors.TextMuted))
                            Text("Credited to Instant Wallet", style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.StatusOnline))
                        }
                        Text(
                            "₹%,.2f".format(breakdown.netDriverEarningsRupees),
                            style = DriverTypography.MetricValue.copy(fontSize = 22.sp, color = DriverColors.StatusOnline)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DriverColors.ChauffeurGold, contentColor = DriverColors.MidnightBlack),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) {
                    Text("CLOSE BREAKDOWN", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun BreakdownRow(
    label: String,
    value: String,
    isBold: Boolean = false,
    color: androidx.compose.ui.graphics.Color = DriverColors.TextPure
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = DriverTypography.Body.copy(fontSize = 12.sp, color = if (isBold) DriverColors.TextPure else DriverColors.TextMuted, fontWeight = if (isBold) FontWeight.SemiBold else FontWeight.Normal))
        Text(value, style = DriverTypography.Body.copy(fontSize = 12.sp, color = color, fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal))
    }
}
