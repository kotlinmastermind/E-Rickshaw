package com.luxury.chauffeur.customer.feature.fares

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.luxury.chauffeur.customer.core.designsystem.*
import com.luxury.chauffeur.customer.core.network.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun FareEstimateBreakdownDialog(
    estimate: FareEstimateResponse,
    onDismiss: () -> Unit,
    onPriceLockToggled: (Boolean) -> Unit = {},
    isPriceLocked: Boolean = false
) {
    var secondsLeft by remember { mutableIntStateOf(estimate.secondsRemaining ?: 900) }

    LaunchedEffect(key1 = estimate.estimateId) {
        while (secondsLeft > 0) {
            delay(1000)
            secondsLeft -= 1
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(LuxuryDimensions.RadiusLarge),
            color = LuxuryColors.CardSurface,
            border = BorderStroke(1.dp, LuxuryColors.ChampagneGold)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(LuxuryDimensions.PaddingLarge)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "FARE BREAKDOWN",
                            style = LuxuryTypography.HeadlineMedium.copy(color = LuxuryColors.ChampagneGold, fontSize = 18.sp)
                        )
                        Text(
                            text = "Transparent luxury pricing guaranteed",
                            style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp)
                        )
                    }

                    // Feature 79: Expiration Indicator
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (secondsLeft > 120) LuxuryColors.CharcoalSurface else LuxuryColors.CrimsonAlert.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, if (secondsLeft > 120) LuxuryColors.ChampagneGold.copy(alpha = 0.5f) else LuxuryColors.CrimsonAlert)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (secondsLeft > 120) LuxuryColors.EmeraldGreen else LuxuryColors.CrimsonAlert)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${secondsLeft / 60}:${(secondsLeft % 60).toString().padStart(2, '0')}",
                                style = LuxuryTypography.BodyRegular.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (secondsLeft > 120) LuxuryColors.ChampagneGold else LuxuryColors.CrimsonAlert
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Feature 77: Confidence Range Box
                Surface(
                    shape = RoundedCornerShape(LuxuryDimensions.RadiusMedium),
                    color = LuxuryColors.ObsidianBlack,
                    border = BorderStroke(1.dp, LuxuryColors.CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "ESTIMATED RANGE",
                                style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp, letterSpacing = 1.sp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val minF = estimate.fareMinRupees ?: (estimate.estimatedFareCents * 0.94 / 100)
                            val maxF = estimate.fareMaxRupees ?: (estimate.estimatedFareCents * 1.08 / 100)
                            Text(
                                text = "₹${minF.toInt()} – ₹${maxF.toInt()}",
                                style = LuxuryTypography.HeadlineMedium.copy(color = LuxuryColors.PlatinumWhite, fontSize = 20.sp)
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = LuxuryColors.ChampagneGold.copy(alpha = 0.15f),
                                border = BorderStroke(0.5.dp, LuxuryColors.ChampagneGold)
                            ) {
                                Text(
                                    text = "${estimate.confidenceLevel ?: "HIGH"} CONFIDENCE (${estimate.confidencePercentage ?: 95}%)",
                                    style = LuxuryTypography.BodySubtle.copy(
                                        color = LuxuryColors.ChampagneGold,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${estimate.distanceKm} km • ${estimate.estimatedDurationMinutes.toInt()} mins",
                                style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp)
                            )
                        }
                    }
                }

                // Feature 81: Fare Cap Banner
                if (estimate.isCapped == true) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = LuxuryColors.EmeraldGreen.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, LuxuryColors.EmeraldGreen.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🛡️", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Luxury Fare Cap applied: You saved ₹${(estimate.capDiscountRupees ?: 0.0).toInt()} under our metropolitan ceiling guarantee.",
                                style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.EmeraldGreen, fontSize = 11.sp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Feature 76: Detailed Breakdown List
                Text("ITEMIZED TARIFF", style = LuxuryTypography.TitleGold.copy(fontSize = 12.sp, letterSpacing = 1.sp))
                Spacer(modifier = Modifier.height(8.dp))

                BreakdownLine("Base Chauffeur Flagfall", "₹${(estimate.baseFareRupees ?: 500.0).toInt()}.00")
                BreakdownLine("Distance Charge (${estimate.distanceKm} km)", "₹${(estimate.distanceChargeRupees ?: 0.0).toInt()}.00")
                BreakdownLine("Transit Duration (${estimate.estimatedDurationMinutes.toInt()} mins)", "₹${(estimate.timeChargeRupees ?: 0.0).toInt()}.00")
                BreakdownLine("Bespoke In-Cabin Amenities", "₹${(estimate.amenitiesFeeRupees ?: 50.0).toInt()}.00")
                BreakdownLine("GST Tax (SAC 9964 • 5%)", "₹${(estimate.taxRupees ?: 0.0).toInt()}.00")

                HorizontalDivider(
                    color = LuxuryColors.CardBorder,
                    modifier = Modifier.padding(vertical = 10.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("TOTAL VERIFIED FARE", style = LuxuryTypography.BodyRegular.copy(fontWeight = FontWeight.Bold))
                    Text(
                        text = "₹${(estimate.estimatedFareRupees ?: (estimate.estimatedFareCents / 100.0)).toInt()}.00",
                        style = LuxuryTypography.HeadlineMedium.copy(color = LuxuryColors.ChampagneGold, fontSize = 20.sp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Feature 82: Price Lock for Scheduled Bookings
                Surface(
                    shape = RoundedCornerShape(LuxuryDimensions.RadiusMedium),
                    color = LuxuryColors.ObsidianBlack,
                    border = BorderStroke(1.dp, if (isPriceLocked) LuxuryColors.ChampagneGold else LuxuryColors.CardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPriceLockToggled(!isPriceLocked) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🔒", fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "PRICE LOCK GUARANTEE",
                                    style = LuxuryTypography.BodyRegular.copy(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isPriceLocked) LuxuryColors.ChampagneGold else LuxuryColors.PlatinumWhite
                                    )
                                )
                            }
                            Text(
                                text = "Locks this fare against future peak demand surges.",
                                style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp)
                            )
                        }
                        Switch(
                            checked = isPriceLocked,
                            onCheckedChange = onPriceLockToggled,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = LuxuryColors.ChampagneGold,
                                checkedTrackColor = LuxuryColors.CardSurface
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                LuxuryGoldButton(
                    text = "CLOSE BREAKDOWN",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun BreakdownLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = LuxuryTypography.BodySubtle.copy(fontSize = 12.sp))
        Text(value, style = LuxuryTypography.BodyRegular.copy(fontSize = 12.sp, color = LuxuryColors.PlatinumWhite))
    }
}

// -----------------------------------------------------------------------------
// Feature 78: Vehicle Class Fare Comparison Dialog
// -----------------------------------------------------------------------------
@Composable
fun VehicleClassComparisonDialog(
    comparisons: List<VehicleFareComparisonDto>,
    selectedCategoryId: String,
    onCategorySelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(LuxuryDimensions.RadiusLarge),
            color = LuxuryColors.CardSurface,
            border = BorderStroke(1.dp, LuxuryColors.ChampagneGold)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(LuxuryDimensions.PaddingLarge)
            ) {
                Text(
                    text = "COMPARE VEHICLE TIERS",
                    style = LuxuryTypography.HeadlineMedium.copy(color = LuxuryColors.ChampagneGold, fontSize = 20.sp)
                )
                Text(
                    text = "Side-by-side rates, cabin specifications, and travel time",
                    style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(comparisons) { item ->
                        val isSelected = item.categoryId == selectedCategoryId
                        Surface(
                            shape = RoundedCornerShape(LuxuryDimensions.RadiusMedium),
                            color = if (isSelected) LuxuryColors.CharcoalSurface else LuxuryColors.ObsidianBlack,
                            border = BorderStroke(
                                if (isSelected) 1.5.dp else 1.dp,
                                if (isSelected) LuxuryColors.ChampagneGold else LuxuryColors.CardBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onCategorySelected(item.categoryId) }
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = item.name.uppercase(),
                                            style = LuxuryTypography.BodyRegular.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) LuxuryColors.ChampagneGold else LuxuryColors.PlatinumWhite
                                            )
                                        )
                                        Text(
                                            text = "${item.capacity} Passengers • Executive Amenities",
                                            style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp)
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "₹${(item.estimate.estimatedFareRupees ?: (item.estimate.estimatedFareCents / 100.0)).toInt()}",
                                            style = LuxuryTypography.HeadlineMedium.copy(
                                                color = LuxuryColors.ChampagneGold,
                                                fontSize = 18.sp
                                            )
                                        )
                                        val minF = item.estimate.fareMinRupees ?: (item.estimate.estimatedFareCents * 0.94 / 100)
                                        val maxF = item.estimate.fareMaxRupees ?: (item.estimate.estimatedFareCents * 1.08 / 100)
                                        Text(
                                            text = "Range: ₹${minF.toInt()}–₹${maxF.toInt()}",
                                            style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = item.description,
                                    style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp)
                                )

                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Base: ₹${(item.estimate.baseFareRupees ?: 500.0).toInt()} • ${item.estimate.distanceKm} km",
                                        style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp)
                                    )
                                    if (isSelected) {
                                        GoldBadge("SELECTED TIER")
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                LuxuryGoldButton(
                    text = "CONFIRM SELECTION",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
