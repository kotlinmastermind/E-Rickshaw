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
fun FinancialSupportDialog(
    onDismiss: () -> Unit,
    onSubmitRequest: (payload: Map<String, Any>) -> Unit
) {
    var requestType by remember { mutableStateOf("MEDICAL_EMERGENCY") }
    var requestedAmountRupees by remember { mutableStateOf("") }
    var reasonDescription by remember { mutableStateOf("") }
    var repaymentPlan by remember { mutableStateOf("WEEKLY_DEDUCTION") }
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
                    "CHAUFFEUR WELFARE FUND",
                    style = DriverTypography.Title.copy(fontSize = 17.sp, color = DriverColors.ChauffeurGold)
                )
                Text(
                    "Interest-free emergency relief and medical assistance for registered chauffeurs.",
                    style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.TextMuted)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Request Types
                val types = listOf(
                    "MEDICAL_EMERGENCY" to "🏥 Medical Emergency",
                    "VEHICLE_REPAIR" to "🔧 Major Vehicle Repair",
                    "FAMILY_ASSISTANCE" to "👨‍👩‍👦 Family Hardship",
                    "ADVANCE_EARNINGS" to "💳 Earnings Advance"
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    types.forEach { (typeKey, typeLabel) ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (requestType == typeKey) DriverColors.ChauffeurGold.copy(alpha = 0.2f) else DriverColors.SurfaceCard,
                            border = BorderStroke(1.dp, if (requestType == typeKey) DriverColors.ChauffeurGold else DriverColors.SurfaceBorder),
                            modifier = Modifier.fillMaxWidth().clickable { requestType = typeKey }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    typeLabel,
                                    style = DriverTypography.Body.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (requestType == typeKey) DriverColors.ChauffeurGold else DriverColors.TextPure)
                                )
                                if (requestType == typeKey) {
                                    Text("SELECTED", style = DriverTypography.Body.copy(fontSize = 10.sp, color = DriverColors.ChauffeurGold, fontWeight = FontWeight.Bold))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = requestedAmountRupees,
                    onValueChange = { requestedAmountRupees = it },
                    label = { Text("Requested Amount (₹ INR, max ₹50,000)", color = DriverColors.TextMuted, fontSize = 12.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DriverColors.ChauffeurGold,
                        unfocusedBorderColor = DriverColors.SurfaceBorder,
                        focusedTextColor = DriverColors.TextPure,
                        unfocusedTextColor = DriverColors.TextPure
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = reasonDescription,
                    onValueChange = { reasonDescription = it },
                    label = { Text("Emergency Justification / Circumstances", color = DriverColors.TextMuted, fontSize = 12.sp) },
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DriverColors.ChauffeurGold,
                        unfocusedBorderColor = DriverColors.SurfaceBorder,
                        focusedTextColor = DriverColors.TextPure,
                        unfocusedTextColor = DriverColors.TextPure
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))
                Text("REPAYMENT PREFERENCE (0% INTEREST)", style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.TextMuted))
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val plans = listOf(
                        "WEEKLY_DEDUCTION" to "Weekly Split",
                        "EXTENDED_12_WEEKS" to "12-Week Term",
                        "LUMP_SUM" to "End of Month"
                    )
                    plans.forEach { (planKey, planLabel) ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (repaymentPlan == planKey) DriverColors.ChauffeurGold.copy(alpha = 0.2f) else DriverColors.SurfaceCard,
                            border = BorderStroke(1.dp, if (repaymentPlan == planKey) DriverColors.ChauffeurGold else DriverColors.SurfaceBorder),
                            modifier = Modifier.weight(1f).clickable { repaymentPlan = planKey }
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                                Text(
                                    planLabel,
                                    style = DriverTypography.Body.copy(fontSize = 10.sp, color = if (repaymentPlan == planKey) DriverColors.ChauffeurGold else DriverColors.TextMuted, fontWeight = FontWeight.SemiBold)
                                )
                            }
                        }
                    }
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(errorMessage!!, style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.AlertDanger))
                }

                Spacer(modifier = Modifier.height(14.dp))

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
                            val amt = requestedAmountRupees.toDoubleOrNull()
                            if (amt == null || amt < 1000.0 || amt > 50000.0) {
                                errorMessage = "Amount must be between ₹1,000 and ₹50,000."
                                return@Button
                            }
                            if (reasonDescription.length < 10) {
                                errorMessage = "Please provide brief details (min 10 characters)."
                                return@Button
                            }
                            val payload = mapOf(
                                "request_type" to requestType,
                                "requested_amount_rupees" to amt,
                                "requested_amount_cents" to (amt * 100).toLong(),
                                "reason_description" to reasonDescription,
                                "repayment_plan" to repaymentPlan
                            )
                            onSubmitRequest(payload)
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DriverColors.ChauffeurGold, contentColor = DriverColors.MidnightBlack),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Text("SUBMIT REQUEST", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
