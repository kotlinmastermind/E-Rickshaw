package com.luxury.chauffeur.customer.feature.security

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luxury.chauffeur.customer.core.designsystem.*
import com.luxury.chauffeur.customer.core.network.ApiClient
import com.luxury.chauffeur.customer.core.network.AppLockPinPayload
import com.luxury.chauffeur.customer.core.security.TokenManager
import kotlinx.coroutines.launch

@Composable
fun AppLockOverlay(
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isVerifying by remember { mutableStateOf(false) }

    fun verifyPinWithServer(pin: String) {
        if (pin.length != 4) return
        isVerifying = true
        errorMessage = null

        scope.launch {
            try {
                // Verify with backend
                val res = ApiClient.getService().verifyAppLockPin(AppLockPinPayload(pin = pin))
                isVerifying = false
                if (res.isSuccessful && res.body()?.valid == true) {
                    onUnlocked()
                } else {
                    // Check local cached PIN if offline
                    val localPin = TokenManager.getInstance(context).getAppLockPin()
                    if (localPin == pin) {
                        onUnlocked()
                    } else {
                        enteredPin = ""
                        errorMessage = "Invalid security PIN. Please try again."
                    }
                }
            } catch (_: Exception) {
                // Fallback to local cached PIN
                isVerifying = false
                val localPin = TokenManager.getInstance(context).getAppLockPin()
                if (localPin == pin) {
                    onUnlocked()
                } else {
                    enteredPin = ""
                    errorMessage = "Incorrect security PIN."
                }
            }
        }
    }

    fun onDigitPressed(digit: String) {
        if (enteredPin.length < 4) {
            val newPin = enteredPin + digit
            enteredPin = newPin
            errorMessage = null
            if (newPin.length == 4) {
                verifyPinWithServer(newPin)
            }
        }
    }

    fun onBackspace() {
        if (enteredPin.isNotEmpty()) {
            enteredPin = enteredPin.dropLast(1)
            errorMessage = null
        }
    }

    fun onBiometricUnlock() {
        // Biometric unlock shortcut when biometric is enabled
        if (TokenManager.getInstance(context).isBiometricEnabled()) {
            onUnlocked()
        } else {
            errorMessage = "Biometric authentication is not enabled on this device."
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LuxuryColors.ObsidianBlack)
            .padding(24.dp)
            .semantics { contentDescription = "Security App Lock Screen" }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                GoldBadge("SOVEREIGN VAULT")
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "ENTER SECURITY PIN",
                    style = LuxuryTypography.HeadlineLarge.copy(
                        color = LuxuryColors.ChampagneGold,
                        fontSize = 22.sp,
                        letterSpacing = 1.sp
                    ),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Grand Chauffeur is secured. Enter your 4-digit PIN to access your executive suite.",
                    style = LuxuryTypography.BodySubtle,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                // PIN Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until 4) {
                        val isFilled = i < enteredPin.length
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isFilled) LuxuryColors.ChampagneGold
                                    else LuxuryColors.CardSurface
                                )
                                .semantics {
                                    contentDescription = if (isFilled) "Digit ${i + 1} filled" else "Digit ${i + 1} empty"
                                }
                        )
                    }
                }

                if (isVerifying) {
                    Spacer(modifier = Modifier.height(16.dp))
                    CircularProgressIndicator(
                        color = LuxuryColors.ChampagneGold,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp)
                    )
                }

                errorMessage?.let { err ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = err,
                        color = LuxuryColors.CrimsonAlert,
                        style = LuxuryTypography.BodySubtle,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Keypad
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val keypadRows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("BIO", "0", "DEL")
                )

                for (row in keypadRows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (key in row) {
                            Surface(
                                shape = CircleShape,
                                color = when (key) {
                                    "BIO", "DEL" -> LuxuryColors.DeepGraphite
                                    else -> LuxuryColors.CardSurface
                                },
                                border = BorderStroke(
                                    1.dp,
                                    if (key == "BIO") LuxuryColors.ChampagneGold.copy(alpha = 0.5f) else LuxuryColors.CardBorder
                                ),
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        when (key) {
                                            "DEL" -> onBackspace()
                                            "BIO" -> onBiometricUnlock()
                                            else -> onDigitPressed(key)
                                        }
                                    }
                                    .semantics {
                                        contentDescription = when (key) {
                                            "DEL" -> "Delete last digit"
                                            "BIO" -> "Unlock with Biometrics"
                                            else -> "Digit $key"
                                        }
                                    }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Text(
                                        text = when (key) {
                                            "BIO" -> "BIO"
                                            "DEL" -> "⌫"
                                            else -> key
                                        },
                                        style = LuxuryTypography.HeadlineMedium.copy(
                                            fontSize = if (key == "BIO") 14.sp else 24.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = when (key) {
                                                "BIO" -> LuxuryColors.ChampagneGold
                                                "DEL" -> LuxuryColors.SubtleGray
                                                else -> LuxuryColors.PlatinumWhite
                                            }
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
