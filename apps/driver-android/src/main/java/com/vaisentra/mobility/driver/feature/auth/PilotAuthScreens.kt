package com.vaisentra.mobility.driver.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaisentra.mobility.driver.components.*
import com.vaisentra.mobility.driver.data.DriverDocumentDto
import com.vaisentra.mobility.driver.theme.*

// Screen 1: Pilot Cockpit Login
@Composable
fun PilotLoginScreen(onNavigate: (String) -> Unit) {
    var phone by remember { mutableStateOf("+91 98112 34567") }
    var pin by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PilotCanvas)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Spacer(modifier = Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(OnlineGlow),
                contentAlignment = Alignment.Center
            ) { Text(text = "👨‍✈️", fontSize = 38.sp) }
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Pilot Cockpit", color = PilotTextWhite, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            Text(text = "Vaisentra Electric Mobility • Driver Partner OS", color = PilotTextMuted, fontSize = 13.sp)

            Spacer(modifier = Modifier.height(24.dp))
            CockpitCard {
                CockpitTextField(value = phone, onValueChange = { phone = it }, placeholder = "+91 XXXXX XXXXX", label = "Registered Pilot Phone")
                Spacer(modifier = Modifier.height(14.dp))
                CockpitTextField(value = pin, onValueChange = { pin = it }, placeholder = "••••", label = "Cockpit Security PIN", visualTransformation = PasswordVisualTransformation())
                Spacer(modifier = Modifier.height(20.dp))
                CockpitButton(
                    text = "Sign In to Cockpit",
                    onClick = {
                        DriverDialogController.showToast("Pilot credentials verified in Supabase!")
                        onNavigate("duty_dashboard")
                    },
                    icon = "⚡"
                )
            }
        }

        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "SMS OTP Login?", color = PilotElectricCyan, fontSize = 13.sp, modifier = Modifier.clickable { onNavigate("auth_otp") })
                Text(text = "Forgot PIN?", color = PilotElectricCyan, fontSize = 13.sp, modifier = Modifier.clickable { onNavigate("auth_reset") })
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// Screen 2: Pilot SMS OTP Sign-In
@Composable
fun PilotOtpScreen(onNavigate: (String) -> Unit) {
    var otp by remember { mutableStateOf("8291") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PilotCanvas)
            .padding(24.dp)
    ) {
        CockpitHeader(title = "Road OTP Authentication", subtitle = "Quick verification without password", onBack = { onNavigate("auth_login") })
        Spacer(modifier = Modifier.height(30.dp))

        CockpitCard {
            Text(text = "Enter 4-digit code sent to +91 98112 34567:", color = PilotTextMuted, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(16.dp))
            CockpitTextField(value = otp, onValueChange = { otp = it }, placeholder = "4-digit code", label = "One-Time Password")
            Spacer(modifier = Modifier.height(20.dp))
            CockpitButton(
                text = "Verify Code & Start Duty",
                onClick = {
                    DriverDialogController.showToast("OTP verified! Session authenticated.")
                    onNavigate("duty_dashboard")
                },
                backgroundColor = PilotOnlineGreen
            )
        }
    }
}

// Screen 3: Pilot Password Recovery
@Composable
fun PilotPasswordRecoveryScreen(onNavigate: (String) -> Unit) {
    var phone by remember { mutableStateOf("+91 98112 34567") }
    var badgeNo by remember { mutableStateOf("EV-PILOT-829") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PilotCanvas)
            .padding(24.dp)
    ) {
        CockpitHeader(title = "PIN & Credential Recovery", subtitle = "Fleet operations identity verification", onBack = { onNavigate("auth_login") })
        Spacer(modifier = Modifier.height(20.dp))

        CockpitCard {
            CockpitTextField(value = phone, onValueChange = { phone = it }, placeholder = "Mobile number", label = "Registered Phone")
            Spacer(modifier = Modifier.height(12.dp))
            CockpitTextField(value = badgeNo, onValueChange = { badgeNo = it }, placeholder = "Badge ID", label = "Commercial Pilot Badge ID")
            Spacer(modifier = Modifier.height(20.dp))
            CockpitButton(text = "Verify & Issue Temporary PIN", onClick = {
                DriverDialogController.showToast("New temporary PIN sent via SMS!")
                onNavigate("auth_login")
            })
        }
    }
}

// Screen 4: Pilot Profile & Academy KYC
@Composable
fun PilotProfileScreen(onNavigate: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PilotCanvas)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        CockpitHeader(title = "Pilot Profile & Academy KYC", subtitle = "Transport Dept Verified Chauffeur", onBack = { onNavigate("duty_dashboard") })
        Spacer(modifier = Modifier.height(16.dp))

        CockpitCard {
            Text(text = "Ram Lal", color = PilotTextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(text = "Commercial EV Chauffeur • Delhi NCR Zone", color = PilotTextMuted, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(12.dp))
            val items = listOf(
                "Driving License" to "DL-0420190082910 (Transport Commercial)",
                "Pilot Badge ID" to "EV-PILOT-829 (Valid till 2028)",
                "Electric Academy" to "Certified EV Pro (98% Score)",
                "Police Verification" to "Cleared & Verified (Delhi Police)",
                "Lifetime Trips" to "2,420 Completed Rides",
                "Chauffeur Rating" to "4.95 ★ (Top 5% Fleet Tier)"
            )
            items.forEach { (k, v) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = k, color = PilotTextMuted, fontSize = 12.sp)
                    Text(text = v, color = PilotTextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        CockpitButton(text = "Open Document Vault →", onClick = { onNavigate("doc_vault") })
    }
}

// Screen 5: Document Vault & Verification
@Composable
fun DocumentVaultScreen(onNavigate: (String) -> Unit) {
    val documents = listOf(
        DriverDocumentDto("d1", "Commercial Driving License (DL)", "APPROVED", "14-Dec-2027", 390),
        DriverDocumentDto("d2", "Aadhaar Card (Govt of India)", "VERIFIED", "Permanent", 9999),
        DriverDocumentDto("d3", "Police Verification Certificate", "APPROVED", "20-Aug-2027", 270),
        DriverDocumentDto("d4", "EV Pilot Academy Certificate", "PASSED", "Permanent", 9999)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PilotCanvas)
            .padding(16.dp)
    ) {
        CockpitHeader(title = "Document Vault", subtitle = "Compliance records in Supabase", onBack = { onNavigate("pilot_profile") })
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(documents) { doc ->
                CockpitCard(cornerRadius = 14.dp) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = doc.documentType, color = PilotTextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        CockpitBadge(text = doc.status, backgroundColor = OnlineGlow, textColor = PilotOnlineGreen)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Valid Until: ${doc.expiryDate}", color = PilotTextMuted, fontSize = 11.sp)
                }
            }
        }
    }
}

// Screen 6: Document Expiry Watchdog
@Composable
fun DocumentExpiryWatchdogScreen(onNavigate: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PilotCanvas)
            .padding(16.dp)
    ) {
        CockpitHeader(title = "Document Expiry Watchdog", subtitle = "Autonomous compliance monitor", onBack = { onNavigate("pilot_profile") })
        Spacer(modifier = Modifier.height(16.dp))

        CockpitCard(borderColor = PilotOnlineGreen) {
            Text(text = "All Documents Fully Compliant", color = PilotOnlineGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Next scheduled document renewal is Commercial DL in 390 days. Zero compliance blocks on your dispatch priority.", color = PilotTextMuted, fontSize = 13.sp)
        }
        Spacer(modifier = Modifier.height(16.dp))
        CockpitButton(text = "Simulate Renewal Alert Modal", onClick = {
            DriverDialogController.show(DriverPopupType.DOC_EXPIRY)
        }, backgroundColor = PilotCard, textColor = PilotTextWhite)
    }
}

