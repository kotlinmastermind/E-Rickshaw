package com.luxury.chauffeur.driver.feature.earnings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.luxury.chauffeur.driver.core.designsystem.DriverColors
import com.luxury.chauffeur.driver.core.designsystem.DriverTypography

@Composable
fun WeeklyGoalEditDialog(
    currentGoalRupees: Double,
    onDismiss: () -> Unit,
    onSaveGoal: (newTargetCents: Long) -> Unit
) {
    var targetText by remember { mutableStateOf(currentGoalRupees.toInt().toString()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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
                Text(
                    "SET WEEKLY EARNINGS TARGET",
                    style = DriverTypography.Title.copy(fontSize = 17.sp, color = DriverColors.ChauffeurGold)
                )
                Text(
                    "Set your weekly net revenue target to activate intelligent pace tracking.",
                    style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.TextMuted)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Quick presets
                val presets = listOf(15000, 25000, 35000, 50000)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { preset ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (targetText == preset.toString()) DriverColors.ChauffeurGold.copy(alpha = 0.2f) else DriverColors.SurfaceCard,
                            border = BorderStroke(1.dp, if (targetText == preset.toString()) DriverColors.ChauffeurGold else DriverColors.SurfaceBorder),
                            modifier = Modifier.weight(1f).clickable { targetText = preset.toString() }
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                                Text(
                                    "₹${preset / 1000}k",
                                    style = DriverTypography.Body.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (targetText == preset.toString()) DriverColors.ChauffeurGold else DriverColors.TextMuted)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = targetText,
                    onValueChange = { targetText = it },
                    label = { Text("Target Net Earnings (₹ INR)", color = DriverColors.TextMuted, fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DriverColors.ChauffeurGold,
                        unfocusedBorderColor = DriverColors.SurfaceBorder,
                        focusedTextColor = DriverColors.TextPure,
                        unfocusedTextColor = DriverColors.TextPure
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(errorMessage!!, style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.AlertDanger))
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, DriverColors.SurfaceBorder),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Text("CANCEL", color = DriverColors.TextMuted, fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            val amt = targetText.toLongOrNull()
                            if (amt == null || amt < 5000L || amt > 200000L) {
                                errorMessage = "Target must be between ₹5,000 and ₹200,000."
                                return@Button
                            }
                            onSaveGoal(amt * 100L)
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DriverColors.ChauffeurGold, contentColor = DriverColors.MidnightBlack),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Text("SAVE TARGET", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
