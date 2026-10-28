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
fun AddPayoutAccountDialog(
    onDismiss: () -> Unit,
    onSubmitAccount: (payload: Map<String, Any>) -> Unit
) {
    var accountType by remember { mutableStateOf("BANK_ACCOUNT") }
    var holderName by remember { mutableStateOf("") }
    var bankName by remember { mutableStateOf("") }
    var accountNumber by remember { mutableStateOf("") }
    var ifscCode by remember { mutableStateOf("") }
    var upiId by remember { mutableStateOf("") }
    var setAsPrimary by remember { mutableStateOf(true) }
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
                    "ADD SETTLEMENT ACCOUNT",
                    style = DriverTypography.Title.copy(fontSize = 17.sp, color = DriverColors.ChauffeurGold)
                )
                Text(
                    "Automatic ₹1 penny-drop validation will confirm beneficiary details immediately.",
                    style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.TextMuted)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Account Type Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (accountType == "BANK_ACCOUNT") DriverColors.ChauffeurGold.copy(alpha = 0.2f) else DriverColors.SurfaceCard,
                        border = BorderStroke(1.dp, if (accountType == "BANK_ACCOUNT") DriverColors.ChauffeurGold else DriverColors.SurfaceBorder),
                        modifier = Modifier.weight(1f).clickable { accountType = "BANK_ACCOUNT" }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
                            Text(
                                "BANK ACCOUNT",
                                style = DriverTypography.Body.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (accountType == "BANK_ACCOUNT") DriverColors.ChauffeurGold else DriverColors.TextMuted)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (accountType == "UPI") DriverColors.ChauffeurGold.copy(alpha = 0.2f) else DriverColors.SurfaceCard,
                        border = BorderStroke(1.dp, if (accountType == "UPI") DriverColors.ChauffeurGold else DriverColors.SurfaceBorder),
                        modifier = Modifier.weight(1f).clickable { accountType = "UPI" }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
                            Text(
                                "UPI VPA ID",
                                style = DriverTypography.Body.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (accountType == "UPI") DriverColors.ChauffeurGold else DriverColors.TextMuted)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Beneficiary Name
                OutlinedTextField(
                    value = holderName,
                    onValueChange = { holderName = it },
                    label = { Text("Account Holder / Legal Name", color = DriverColors.TextMuted, fontSize = 12.sp) },
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

                if (accountType == "BANK_ACCOUNT") {
                    OutlinedTextField(
                        value = bankName,
                        onValueChange = { bankName = it },
                        label = { Text("Bank Name (e.g. HDFC Bank, ICICI)", color = DriverColors.TextMuted, fontSize = 12.sp) },
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
                        value = accountNumber,
                        onValueChange = { accountNumber = it },
                        label = { Text("Account Number", color = DriverColors.TextMuted, fontSize = 12.sp) },
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
                        value = ifscCode,
                        onValueChange = { ifscCode = it.uppercase() },
                        label = { Text("IFSC Code (11 alphanumeric, e.g. HDFC0001234)", color = DriverColors.TextMuted, fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DriverColors.ChauffeurGold,
                            unfocusedBorderColor = DriverColors.SurfaceBorder,
                            focusedTextColor = DriverColors.TextPure,
                            unfocusedTextColor = DriverColors.TextPure
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = upiId,
                        onValueChange = { upiId = it },
                        label = { Text("UPI Virtual Payment Address (e.g. arjun@okhdfcbank)", color = DriverColors.TextMuted, fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DriverColors.ChauffeurGold,
                            unfocusedBorderColor = DriverColors.SurfaceBorder,
                            focusedTextColor = DriverColors.TextPure,
                            unfocusedTextColor = DriverColors.TextPure
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().clickable { setAsPrimary = !setAsPrimary },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = setAsPrimary,
                        onCheckedChange = { setAsPrimary = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = DriverColors.ChauffeurGold,
                            checkmarkColor = DriverColors.MidnightBlack
                        )
                    )
                    Text("Set as primary account for scheduled automated payouts", style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.TextMuted))
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
                            if (holderName.isBlank()) {
                                errorMessage = "Please enter beneficiary name."
                                return@Button
                            }
                            if (accountType == "BANK_ACCOUNT") {
                                if (accountNumber.length < 8) {
                                    errorMessage = "Please enter a valid bank account number."
                                    return@Button
                                }
                                if (ifscCode.length != 11) {
                                    errorMessage = "IFSC code must be exactly 11 characters."
                                    return@Button
                                }
                            } else {
                                if (!upiId.contains("@")) {
                                    errorMessage = "Please enter a valid UPI ID (e.g. user@bank)."
                                    return@Button
                                }
                            }
                            val map = mutableMapOf<String, Any>(
                                "account_type" to accountType,
                                "holder_name" to holderName,
                                "set_as_primary" to setAsPrimary
                            )
                            if (accountType == "BANK_ACCOUNT") {
                                map["bank_name"] = bankName.ifBlank { "National Bank" }
                                map["account_number"] = accountNumber
                                map["ifsc_code"] = ifscCode
                            } else {
                                map["upi_id"] = upiId
                            }
                            onSubmitAccount(map)
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DriverColors.ChauffeurGold, contentColor = DriverColors.MidnightBlack),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Text("VERIFY & SAVE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
