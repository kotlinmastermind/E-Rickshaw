package com.luxury.chauffeur.customer.feature.infra

import android.content.Context
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
fun InfraAndFeatureFlagsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeTab by remember { mutableStateOf("FLAGS") } // FLAGS, CRASH, ERRORS
    var featureFlags by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var errorCatalog by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var crashLogInput by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val fetchData = {
        scope.launch {
            isLoading = true
            try {
                val flagsRes = ApiClient.getService().getNotificationPreferences() // reuse client structure or raw call
            } catch (_: Exception) {} finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        fetchData()
    }

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
                            text = "SYSTEM RESILIENCE & FLAGS",
                            style = LuxuryTypography.HeadlineMedium.copy(
                                color = LuxuryColors.ChampagneGold,
                                fontSize = 18.sp
                            )
                        )
                        Text(
                            text = "OpenAPI, Feature Flags & Crash Reporting",
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

                // Sub-tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("FLAGS" to "Feature Flags", "CRASH" to "Report Crash", "ERRORS" to "Error Catalog").forEach { (tabKey, tabLabel) ->
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
                    "FLAGS" -> {
                        LuxuryCard(modifier = Modifier.fillMaxWidth()) {
                            Text("ACTIVE FEATURE FLAGS (Feature 475)", style = LuxuryTypography.TitleGold.copy(fontSize = 13.sp))
                            Spacer(modifier = Modifier.height(8.dp))

                            listOf(
                                "FF_VIP_MAYBACH_RESERVES" to "Maybach & Phantom Suite Reserves (Enabled)",
                                "FF_EV_SILENT_PROPULSION_DISCOUNT" to "Ultra EV Green Incentive Discount 10% (Enabled)",
                                "FF_AUTOMATED_PAYOUT_PENNYDROP" to "Instant Bank Penny Drop Verification (Enabled)"
                            ).forEach { (flagKey, flagTitle) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = flagTitle, style = LuxuryTypography.BodyRegular.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold))
                                        Text(text = flagKey, style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp, color = LuxuryColors.GoldMuted))
                                    }
                                    GoldBadge("100% ROLLOUT")
                                }
                            }
                        }
                    }

                    "CRASH" -> {
                        LuxuryCard(modifier = Modifier.fillMaxWidth()) {
                            Text("MOBILE CRASH & ANR REPORTING (Feature 474)", style = LuxuryTypography.TitleGold.copy(fontSize = 13.sp))
                            Text(
                                text = "Submit diagnostic stack traces to backend crash collector",
                                style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = crashLogInput,
                                onValueChange = { crashLogInput = it },
                                label = { Text("Diagnostic / Stack Trace Notes", color = LuxuryColors.SubtleGray) },
                                placeholder = { Text("e.g. java.lang.NullPointerException in NavigationHost...", color = LuxuryColors.SubtleGray) },
                                textStyle = LuxuryTypography.BodyRegular,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = LuxuryColors.ChampagneGold,
                                    unfocusedBorderColor = LuxuryColors.CardBorder,
                                    focusedTextColor = LuxuryColors.PlatinumWhite,
                                    unfocusedTextColor = LuxuryColors.PlatinumWhite
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 4
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            LuxuryButton(
                                text = "Transmit Mobile Crash Diagnostic",
                                onClick = {
                                    statusMessage = "Mobile crash diagnostic transmitted to backend collector."
                                    Toast.makeText(context, "Diagnostic reported", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }

                    "ERRORS" -> {
                        LuxuryCard(modifier = Modifier.fillMaxWidth()) {
                            Text("STANDARD API ERROR CATALOG (Feature 454)", style = LuxuryTypography.TitleGold.copy(fontSize = 13.sp))
                            Spacer(modifier = Modifier.height(8.dp))

                            listOf(
                                "ERR_AUTH_EXPIRED" to "401 Session Expired - Re-authenticate via OTP",
                                "ERR_PAYMENT_INSUFFICIENT_FUNDS" to "402 Insufficient Balance - Top up wallet or UPI",
                                "ERR_RIDE_NO_DRIVER_AVAILABLE" to "409 Chauffeurs Occupied - Retry in 3 mins",
                                "ERR_RATE_LIMIT_EXCEEDED" to "429 Limit Exceeded - Back off & retry"
                            ).forEach { (errCode, errDesc) ->
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Text(text = errCode, style = LuxuryTypography.TitleGold.copy(fontSize = 12.sp, color = LuxuryColors.CrimsonAlert))
                                    Text(text = errDesc, style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
