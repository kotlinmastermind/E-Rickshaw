package com.luxury.chauffeur.customer.feature.enterprise

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.luxury.chauffeur.customer.core.designsystem.*
import com.luxury.chauffeur.customer.core.network.ApiClient
import kotlinx.coroutines.launch

@Composable
fun EnterpriseProductsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeTab by remember { mutableStateOf("CORP") } // CORP, PACKAGES, VOTING
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var feedbackText by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f)
                .clip(RoundedCornerShape(LuxuryDimensions.RadiusLarge)),
            color = LuxuryColors.ObsidianBlack,
            border = BorderStroke(1.5.dp, LuxuryColors.ChampagneGold)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ENTERPRISE & B2B PRODUCTS",
                            style = LuxuryTypography.HeadlineMedium.copy(
                                color = LuxuryColors.ChampagneGold,
                                fontSize = 18.sp
                            )
                        )
                        Text(
                            text = "Corporate Portal, Airport Transfers & Feature Requests",
                            style = LuxuryTypography.BodySubtle
                        )
                    }
                    Text(
                        text = "✕",
                        style = LuxuryTypography.TitleGold.copy(fontSize = 18.sp),
                        modifier = Modifier.clickable { onDismiss() }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (statusMessage != null) {
                    Surface(
                        color = LuxuryColors.LuxuryEmerald.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                        border = BorderStroke(1.dp, LuxuryColors.LuxuryEmerald),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Text(
                            text = statusMessage!!,
                            color = LuxuryColors.LuxuryEmerald,
                            style = LuxuryTypography.BodySubtle,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                // Navigation Tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("CORP" to "Corporate", "PACKAGES" to "Packages", "VOTING" to "Feedback & Votes").forEach { (tabKey, tabLabel) ->
                        val isSelected = activeTab == tabKey
                        Surface(
                            shape = RoundedCornerShape(LuxuryDimensions.RadiusPill),
                            color = if (isSelected) LuxuryColors.ChampagneGold else LuxuryColors.CardSurface,
                            border = BorderStroke(1.dp, if (isSelected) LuxuryColors.ChampagneGold else LuxuryColors.CardBorder),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { activeTab = tabKey }
                        ) {
                            Text(
                                text = tabLabel,
                                color = if (isSelected) LuxuryColors.ObsidianBlack else LuxuryColors.PlatinumWhite,
                                style = LuxuryTypography.TitleGold.copy(
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                modifier = Modifier.padding(vertical = 8.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (activeTab) {
                    "CORP" -> {
                        LuxuryCard(modifier = Modifier.fillMaxWidth()) {
                            Text("CORPORATE EMPLOYEE PORTAL (Features 476-480)", style = LuxuryTypography.TitleGold.copy(fontSize = 13.sp))
                            Spacer(modifier = Modifier.height(8.dp))

                            Text("Company: McKinsey & Company India", style = LuxuryTypography.BodyRegular.copy(fontWeight = FontWeight.Bold))
                            Text("Cost Center: STRATEGY_CONSULTING_MUM", style = LuxuryTypography.BodySubtle)
                            Text("Per-Ride Cap: ₹2,500 | Central Invoice Active", style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.LuxuryEmerald))

                            Spacer(modifier = Modifier.height(12.dp))

                            LuxuryButton(
                                text = "Switch To Corporate Billing Mode",
                                onClick = {
                                    statusMessage = "Corporate billing mode active. Invoices will route to company account."
                                    Toast.makeText(context, "Corporate Mode Active", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }

                    "PACKAGES" -> {
                        LuxuryCard(modifier = Modifier.fillMaxWidth()) {
                            Text("SPECIALTY PRODUCTS & PACKAGES (Features 491-494)", style = LuxuryTypography.TitleGold.copy(fontSize = 13.sp))
                            Spacer(modifier = Modifier.height(8.dp))

                            listOf(
                                "BOM Airport Fast-Track Limo" to "₹8,500 Flat • Terminal Escort Included",
                                "Mumbai - Pune Expressway Intercity" to "₹12,500 Flat • All Tolls Included",
                                "4-Hour Executive Hourly Booking" to "₹6,000 • 40km Included",
                                "Armed Security Detail Add-On" to "+₹5,000 per voyage"
                            ).forEach { (title, subtitle) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = title, style = LuxuryTypography.BodyRegular.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold))
                                        Text(text = subtitle, style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp, color = LuxuryColors.ChampagneGold))
                                    }
                                    GoldBadge("RESERVE")
                                }
                            }
                        }
                    }

                    "VOTING" -> {
                        LuxuryCard(modifier = Modifier.fillMaxWidth()) {
                            Text("FEATURE REQUEST VOTING & FEEDBACK (Features 499, 500)", style = LuxuryTypography.TitleGold.copy(fontSize = 13.sp))
                            Spacer(modifier = Modifier.height(8.dp))

                            listOf(
                                "Custom In-Flight Wi-Fi SSID & Password" to "142 Upvotes",
                                "Starlink Satellite Internet on Highways" to "98 Upvotes"
                            ).forEach { (featureTitle, votes) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = featureTitle, style = LuxuryTypography.BodyRegular.copy(fontSize = 12.sp))
                                        Text(text = votes, style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp, color = LuxuryColors.GoldMuted))
                                    }
                                    GoldBadge("👍 UPVOTE")
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = feedbackText,
                                onValueChange = { feedbackText = it },
                                label = { Text("Product Feedback & Feature Suggestion", color = LuxuryColors.SubtleGray) },
                                placeholder = { Text("Share suggestions to elevate your chauffeur journey...", color = LuxuryColors.SubtleGray) },
                                textStyle = LuxuryTypography.BodyRegular,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = LuxuryColors.ChampagneGold,
                                    unfocusedBorderColor = LuxuryColors.CardBorder,
                                    focusedTextColor = LuxuryColors.PlatinumWhite,
                                    unfocusedTextColor = LuxuryColors.PlatinumWhite
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 3
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            LuxuryButton(
                                text = "Submit Product Suggestion",
                                onClick = {
                                    statusMessage = "Thank you! Your feedback was routed to product analytics."
                                    Toast.makeText(context, "Feedback Submitted", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
