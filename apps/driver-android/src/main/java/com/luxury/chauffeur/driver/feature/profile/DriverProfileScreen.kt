package com.luxury.chauffeur.driver.feature.profile

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luxury.chauffeur.driver.core.designsystem.*
import com.luxury.chauffeur.driver.core.localization.driverLocalizedString
import com.luxury.chauffeur.driver.core.network.DriverApiClient
import kotlinx.coroutines.launch

@Composable
fun DriverProfileScreen(
    onNavigateBack: () -> Unit,
    onNavigateRideHistory: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var fullName by remember { mutableStateOf("Rajeshwar Varma") }
    var phoneNumber by remember { mutableStateOf("+91 98765 00001") }
    var email by remember { mutableStateOf("driver@luxury.com") }
    var city by remember { mutableStateOf("Delhi NCR") }
    var username by remember { mutableStateOf("rajeshwar_varma") }
    var avatarUrl by remember { mutableStateOf<String?>(null) }
    var vehicleModel by remember { mutableStateOf("Mahindra Treo Electric") }
    var vehiclePlate by remember { mutableStateOf("DL-01-ER-1001") }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showVehicleChangeDialog by remember { mutableStateOf(false) }

    // Load real driver profile from backend API
    LaunchedEffect(Unit) {
        try {
            val res = DriverApiClient.getService().getMe()
            if (res.isSuccessful && res.body() != null) {
                val me = res.body()!!
                fullName = me["full_name"] as? String ?: fullName
                phoneNumber = me["phone_number"] as? String ?: phoneNumber
                email = me["email"] as? String ?: email
                username = me["username"] as? String ?: username
                avatarUrl = me["avatar_url"] as? String
            }
        } catch (_: Exception) {}
    }

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
                Text(driverLocalizedString("chauffeur_credentials", "CHAUFFEUR CREDENTIALS"), style = DriverTypography.Title.copy(fontSize = 18.sp, color = DriverColors.ChauffeurGold))
                TextButton(onClick = onNavigateBack) {
                    Text("BACK", color = DriverColors.ChauffeurGold, fontWeight = FontWeight.Bold)
                }
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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (statusMessage != null) {
                    Surface(
                        color = DriverColors.AlertSuccess.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, DriverColors.AlertSuccess),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = statusMessage!!,
                            color = DriverColors.AlertSuccess,
                            style = DriverTypography.Body.copy(fontSize = 11.sp),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                // Personal Details & Profile Photo Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = DriverColors.SurfaceCard,
                    border = BorderStroke(1.dp, DriverColors.SurfaceBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = DriverColors.CockpitDark,
                                border = BorderStroke(1.5.dp, DriverColors.ChauffeurGold),
                                modifier = Modifier.size(60.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = fullName.take(2).uppercase(),
                                        style = DriverTypography.Title.copy(color = DriverColors.ChauffeurGold, fontSize = 18.sp)
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(fullName, style = DriverTypography.Title.copy(fontSize = 16.sp))
                                Text("@$username • Master Pilot", style = DriverTypography.Subtitle.copy(fontSize = 12.sp))
                                Text("Phone: $phoneNumber", style = DriverTypography.Body.copy(color = DriverColors.TextMuted, fontSize = 11.sp))
                                Text("City: $city", style = DriverTypography.Body.copy(color = DriverColors.ChauffeurGold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold))
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { showEditProfileDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = DriverColors.ChauffeurGold, contentColor = DriverColors.MidnightBlack),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).height(36.dp)
                            ) {
                                Text("Edit Profile", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = onNavigateRideHistory,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = DriverColors.ChauffeurGold),
                                border = BorderStroke(1.dp, DriverColors.ChauffeurGold),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).height(36.dp)
                            ) {
                                Text("Ride History", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Vehicle Details & Vehicle Change Request
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = DriverColors.CockpitDark,
                    border = BorderStroke(1.dp, DriverColors.SurfaceBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("ASSIGNED ELECTRIC VEHICLE", style = DriverTypography.Subtitle.copy(fontSize = 12.sp, color = DriverColors.ChauffeurGold))
                            StatusChip("ACTIVE FLEET", DriverColors.StatusOnline)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(vehicleModel, style = DriverTypography.Body.copy(fontWeight = FontWeight.Bold))
                        Text("Plate: $vehiclePlate", style = DriverTypography.Body.copy(color = DriverColors.TextMuted))
                        Spacer(modifier = Modifier.height(10.dp))

                        SecondaryButton(
                            text = "REQUEST VEHICLE CHANGE / UPGRADE",
                            onClick = { showVehicleChangeDialog = true }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            PrimaryButton(
                text = driverLocalizedString("back_to_cockpit", "BACK TO COCKPIT"),
                onClick = onNavigateBack
            )
        }
    }

    // Vehicle Change Request Dialog
    if (showVehicleChangeDialog) {
        VehicleChangeRequestDialog(
            onDismiss = { showVehicleChangeDialog = false },
            onSubmitSuccess = {
                statusMessage = "Vehicle change request submitted for compliance review!"
            }
        )
    }

    // Profile Edit Dialog
    if (showEditProfileDialog) {
        var editName by remember { mutableStateOf(fullName) }
        var editCity by remember { mutableStateOf(city) }
        var editPhone by remember { mutableStateOf(phoneNumber) }

        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            containerColor = DriverColors.MidnightBlack,
            title = {
                Text("EDIT PILOT PROFILE", style = DriverTypography.Subtitle.copy(color = DriverColors.ChauffeurGold))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Full Name", color = DriverColors.TextMuted) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editCity,
                        onValueChange = { editCity = it },
                        label = { Text("Operating City", color = DriverColors.TextMuted) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editPhone,
                        onValueChange = { editPhone = it },
                        label = { Text("Mobile Phone", color = DriverColors.TextMuted) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                PrimaryButton(
                    text = "SAVE CHANGES",
                    onClick = {
                        fullName = editName
                        city = editCity
                        phoneNumber = editPhone
                        showEditProfileDialog = false
                        statusMessage = "Profile updated successfully!"
                    }
                )
            },
            dismissButton = {
                SecondaryButton(
                    text = "CANCEL",
                    onClick = { showEditProfileDialog = false }
                )
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DriverProfileScreenPreview() {
    DriverProfileScreen(onNavigateBack = {})
}
