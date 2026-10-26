package com.luxury.chauffeur.customer.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luxury.chauffeur.customer.core.designsystem.*

@Composable
fun ProfileEditScreen(
    onNavigateBack: () -> Unit
) {
    var fullName by remember { mutableStateOf("Lord Sterling") }
    var email by remember { mutableStateOf("sterling@luxury.io") }
    var emergencyPhone by remember { mutableStateOf("+91 98111 22233") }
    var preferredPayment by remember { mutableStateOf("UPI") }
    var isSaved by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = LuxuryColors.ObsidianBlack,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = LuxuryDimensions.PaddingLarge, vertical = LuxuryDimensions.PaddingMedium)
            ) {
                Text(
                    text = "GUEST PROFILE",
                    style = LuxuryTypography.HeadlineMedium.copy(color = LuxuryColors.ChampagneGold)
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = LuxuryDimensions.PaddingLarge)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Spacer(modifier = Modifier.height(16.dp))
                
                if (isSaved) {
                    Surface(
                        color = LuxuryColors.LuxuryEmerald.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        Text(
                            text = "Your distinguished profile has been updated.",
                            style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.LuxuryEmerald),
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Full Name", color = LuxuryColors.SubtleGray) },
                    textStyle = LuxuryTypography.BodyRegular,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LuxuryColors.ChampagneGold,
                        unfocusedBorderColor = LuxuryColors.CardBorder,
                        focusedTextColor = LuxuryColors.PlatinumWhite,
                        unfocusedTextColor = LuxuryColors.PlatinumWhite
                    ),
                    shape = RoundedCornerShape(LuxuryDimensions.RadiusMedium),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Executive Email", color = LuxuryColors.SubtleGray) },
                    textStyle = LuxuryTypography.BodyRegular,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LuxuryColors.ChampagneGold,
                        unfocusedBorderColor = LuxuryColors.CardBorder,
                        focusedTextColor = LuxuryColors.PlatinumWhite,
                        unfocusedTextColor = LuxuryColors.PlatinumWhite
                    ),
                    shape = RoundedCornerShape(LuxuryDimensions.RadiusMedium),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = emergencyPhone,
                    onValueChange = { emergencyPhone = it },
                    label = { Text("Emergency Concierge Contact", color = LuxuryColors.SubtleGray) },
                    textStyle = LuxuryTypography.BodyRegular,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LuxuryColors.ChampagneGold,
                        unfocusedBorderColor = LuxuryColors.CardBorder,
                        focusedTextColor = LuxuryColors.PlatinumWhite,
                        unfocusedTextColor = LuxuryColors.PlatinumWhite
                    ),
                    shape = RoundedCornerShape(LuxuryDimensions.RadiusMedium),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))
                Text("PREFERRED SETTLEMENT", style = LuxuryTypography.TitleGold)
                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LuxuryCard(
                        modifier = Modifier.weight(1f),
                        border = if (preferredPayment == "UPI") androidx.compose.foundation.BorderStroke(1.5.dp, LuxuryColors.ChampagneGold) else null,
                        onClick = { preferredPayment = "UPI" }
                    ) {
                        Text("UPI Instant", style = LuxuryTypography.HeadlineMedium.copy(fontSize = 16.sp))
                        Text("Secure QR / VPA", style = LuxuryTypography.BodySubtle)
                    }

                    LuxuryCard(
                        modifier = Modifier.weight(1f),
                        border = if (preferredPayment == "CASH") androidx.compose.foundation.BorderStroke(1.5.dp, LuxuryColors.ChampagneGold) else null,
                        onClick = { preferredPayment = "CASH" }
                    ) {
                        Text("Direct Cash", style = LuxuryTypography.HeadlineMedium.copy(fontSize = 16.sp))
                        Text("Chauffeur Collection", style = LuxuryTypography.BodySubtle)
                    }
                }
            }

            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                LuxuryButton(
                    text = "SAVE PROFILE",
                    onClick = {
                        isSaved = true
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
                LuxuryButton(
                    text = "BACK TO JOURNEY",
                    isSecondary = true,
                    onClick = onNavigateBack
                )
            }
        }
    }
}
