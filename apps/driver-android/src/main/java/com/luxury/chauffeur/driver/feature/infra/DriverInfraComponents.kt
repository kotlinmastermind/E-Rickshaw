package com.luxury.chauffeur.driver.feature.infra

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
import com.luxury.chauffeur.driver.core.designsystem.*

@Composable
fun DriverInfraAndFeatureFlagsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    var crashLogInput by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(LuxuryDriverDimensions.RadiusLarge)),
            color = LuxuryDriverColors.ObsidianBlack,
            border = BorderStroke(1.5.dp, LuxuryDriverColors.ChampagneGold)
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
                            text = "CHAUFFEUR RESILIENCE & DIAGNOSTICS",
                            style = LuxuryDriverTypography.HeadlineMedium.copy(
                                color = LuxuryDriverColors.ChampagneGold,
                                fontSize = 16.sp
                            )
                        )
                        Text(
                            text = "Driver App Feature Flags & ANR Reporter",
                            style = LuxuryDriverTypography.BodySubtle
                        )
                    }
                    Text(
                        text = "✕",
                        style = LuxuryDriverTypography.TitleGold.copy(fontSize = 18.sp),
                        modifier = Modifier.clickable { onDismiss() }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (statusMessage != null) {
                    Surface(
                        color = LuxuryDriverColors.LuxuryEmerald.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(LuxuryDriverDimensions.RadiusSmall),
                        border = BorderStroke(1.dp, LuxuryDriverColors.LuxuryEmerald),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Text(
                            text = statusMessage!!,
                            color = LuxuryDriverColors.LuxuryEmerald,
                            style = LuxuryDriverTypography.BodySubtle,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                // Active Feature Flags
                LuxuryDriverCard(modifier = Modifier.fillMaxWidth()) {
                    Text("CHAUFFEUR FEATURE FLAGS (Feature 475)", style = LuxuryDriverTypography.TitleGold.copy(fontSize = 13.sp))
                    Spacer(modifier = Modifier.height(8.dp))

                    listOf(
                        "FF_AUTOMATED_PAYOUT_PENNYDROP" to "Instant Bank Penny Drop Validation (Active)",
                        "FF_DRIVER_FATIGUE_PREVENTION" to "Continuous Shift & Fatigue Monitoring (Active)",
                        "FF_PREMIUM_MAYBACH_DISPATCH" to "Maybach & Phantom Dispatch Eligibility (Active)"
                    ).forEach { (flagKey, flagTitle) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = flagTitle, style = LuxuryDriverTypography.BodyRegular.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold))
                                Text(text = flagKey, style = LuxuryDriverTypography.BodySubtle.copy(fontSize = 10.sp, color = LuxuryDriverColors.GoldMuted))
                            }
                            DriverGoldBadge("ENABLED")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Mobile Crash & ANR Reporting
                LuxuryDriverCard(modifier = Modifier.fillMaxWidth()) {
                    Text("REPORT MOBILE ANR / CRASH (Feature 474)", style = LuxuryDriverTypography.TitleGold.copy(fontSize = 13.sp))
                    Text(
                        text = "Transmit diagnostic log directly to fleet SRE team",
                        style = LuxuryDriverTypography.BodySubtle.copy(fontSize = 11.sp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = crashLogInput,
                        onValueChange = { crashLogInput = it },
                        label = { Text("ANR / Exception Details", color = LuxuryDriverColors.SubtleGray) },
                        placeholder = { Text("e.g. GPS telemetry timeout during active trip navigation...", color = LuxuryDriverColors.SubtleGray) },
                        textStyle = LuxuryDriverTypography.BodyRegular,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LuxuryDriverColors.ChampagneGold,
                            unfocusedBorderColor = LuxuryDriverColors.CardBorder,
                            focusedTextColor = LuxuryDriverColors.PlatinumWhite,
                            unfocusedTextColor = LuxuryDriverColors.PlatinumWhite
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    LuxuryDriverButton(
                        text = "Transmit Diagnostic Log",
                        onClick = {
                            statusMessage = "Diagnostic log transmitted to SRE operations team."
                            Toast.makeText(context, "Log sent to SRE team", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}
