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
fun LogExpenseDialog(
    onDismiss: () -> Unit,
    onSubmitExpense: (payload: Map<String, Any>) -> Unit
) {
    var category by remember { mutableStateOf("EV_CHARGING") }
    var amountRupees by remember { mutableStateOf("") }
    var quantityUnits by remember { mutableStateOf("") }
    var odometerKm by remember { mutableStateOf("") }
    var vendorName by remember { mutableStateOf("") }
    var receiptNotes by remember { mutableStateOf("") }
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
                    "LOG OPERATING EXPENSE",
                    style = DriverTypography.Title.copy(fontSize = 17.sp, color = DriverColors.ChauffeurGold)
                )
                Text(
                    "Track energy charging, fuel, tolls, and maintenance against your net margin.",
                    style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.TextMuted)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Category chips
                val categories = listOf(
                    "EV_CHARGING" to "⚡ EV Charge",
                    "FUEL_DIESEL" to "⛽ Fuel",
                    "TOLL" to "🛣 Toll",
                    "VEHICLE_MAINTENANCE" to "🔧 Maintenance"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { (catKey, catLabel) ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (category == catKey) DriverColors.ChauffeurGold.copy(alpha = 0.2f) else DriverColors.SurfaceCard,
                            border = BorderStroke(1.dp, if (category == catKey) DriverColors.ChauffeurGold else DriverColors.SurfaceBorder),
                            modifier = Modifier.weight(1f).clickable { category = catKey }
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                                Text(
                                    catLabel,
                                    style = DriverTypography.Body.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = if (category == catKey) DriverColors.ChauffeurGold else DriverColors.TextMuted)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = amountRupees,
                    onValueChange = { amountRupees = it },
                    label = { Text("Total Cost (₹ INR)", color = DriverColors.TextMuted, fontSize = 12.sp) },
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

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val unitLabel = if (category == "EV_CHARGING") "Units (kWh)" else "Volume (Liters)"
                    OutlinedTextField(
                        value = quantityUnits,
                        onValueChange = { quantityUnits = it },
                        label = { Text(unitLabel, color = DriverColors.TextMuted, fontSize = 11.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DriverColors.ChauffeurGold,
                            unfocusedBorderColor = DriverColors.SurfaceBorder,
                            focusedTextColor = DriverColors.TextPure,
                            unfocusedTextColor = DriverColors.TextPure
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = odometerKm,
                        onValueChange = { odometerKm = it },
                        label = { Text("Odometer (km)", color = DriverColors.TextMuted, fontSize = 11.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DriverColors.ChauffeurGold,
                            unfocusedBorderColor = DriverColors.SurfaceBorder,
                            focusedTextColor = DriverColors.TextPure,
                            unfocusedTextColor = DriverColors.TextPure
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = vendorName,
                    onValueChange = { vendorName = it },
                    label = { Text("Station / Vendor Name (e.g. Tata Power SuperFast)", color = DriverColors.TextMuted, fontSize = 12.sp) },
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
                    value = receiptNotes,
                    onValueChange = { receiptNotes = it },
                    label = { Text("Notes / Bill Reference", color = DriverColors.TextMuted, fontSize = 12.sp) },
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
                            val amt = amountRupees.toDoubleOrNull()
                            if (amt == null || amt <= 0.0) {
                                errorMessage = "Please enter a valid expense cost in INR."
                                return@Button
                            }
                            val payload = mutableMapOf<String, Any>(
                                "expense_category" to category,
                                "amount_rupees" to amt,
                                "amount_cents" to (amt * 100).toLong()
                            )
                            quantityUnits.toDoubleOrNull()?.let {
                                payload["quantity_units"] = it
                                payload["unit_type"] = if (category == "EV_CHARGING") "KWH" else "LITERS"
                            }
                            odometerKm.toDoubleOrNull()?.let { payload["odometer_km"] = it }
                            if (vendorName.isNotBlank()) payload["vendor_name"] = vendorName
                            if (receiptNotes.isNotBlank()) payload["receipt_notes"] = receiptNotes

                            onSubmitExpense(payload)
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DriverColors.ChauffeurGold, contentColor = DriverColors.MidnightBlack),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Text("SAVE EXPENSE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
