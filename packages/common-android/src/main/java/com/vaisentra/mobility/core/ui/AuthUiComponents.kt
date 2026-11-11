package com.vaisentra.mobility.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaisentra.mobility.core.designsystem.SharedDesignTokens
import kotlinx.coroutines.delay

/**
 * Normalizes user-entered mobile phone number into E.164 canonical format (+91...).
 * DRY helper shared across Customer and Driver apps.
 */
fun normalizePhoneNumber(input: String): String {
    val clean = input.trim().replace(" ", "").replace("-", "")
    if (clean.isBlank()) return ""
    val digitsOnly = clean.filter { it.isDigit() }
    return when {
        clean.startsWith("+") -> clean
        digitsOnly.length == 10 -> "+91$digitsOnly"
        digitsOnly.length == 12 && digitsOnly.startsWith("91") -> "+$digitsOnly"
        digitsOnly.length > 10 && digitsOnly.startsWith("91") -> "+$digitsOnly"
        digitsOnly.length > 10 -> "+91${digitsOnly.take(10)}"
        else -> "+91$digitsOnly"
    }
}

/**
 * Validates whether the given string is a valid 10-digit Indian mobile number (or +91...).
 */
fun isValidIndianMobile(input: String): Boolean {
    val normalized = normalizePhoneNumber(input)
    val digits = normalized.filter { it.isDigit() }
    return (digits.length == 12 && digits.startsWith("91")) || digits.length == 10
}

/**
 * Shared 6-digit PIN / OTP input box rendering with individual digit cells.
 */
@Composable
fun SixDigitPinInput(
    pin: String,
    onPinChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    accentColor: Color = SharedDesignTokens.BrandGold,
    cellBackgroundColor: Color = Color(0xFF1E293B)
) {
    BasicTextField(
        value = pin,
        onValueChange = { newValue ->
            val filtered = newValue.filter { it.isDigit() }.take(6)
            onPinChange(filtered)
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = modifier.fillMaxWidth(),
        decorationBox = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until 6) {
                    val digit = pin.getOrNull(i)?.toString() ?: ""
                    val isFocused = pin.length == i
                    val cellBorderColor = when {
                        isError -> SharedDesignTokens.Red
                        isFocused -> accentColor
                        digit.isNotEmpty() -> accentColor.copy(alpha = 0.6f)
                        else -> Color(0xFF334155)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(0.85f)
                            .padding(horizontal = 3.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(cellBackgroundColor)
                            .border(1.5.dp, cellBorderColor, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = digit,
                            color = if (isError) SharedDesignTokens.Red else Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    )
}

/**
 * Shared Resend OTP cooldown timer and presentation component.
 */
@Composable
fun ResendOtpCooldown(
    cooldownSeconds: Int = 30,
    onResendClick: () -> Unit,
    accentColor: Color = SharedDesignTokens.BrandGold,
    modifier: Modifier = Modifier
) {
    var secondsRemaining by remember { mutableIntStateOf(cooldownSeconds) }
    var isTimerActive by remember { mutableStateOf(true) }

    LaunchedEffect(isTimerActive, secondsRemaining) {
        if (isTimerActive && secondsRemaining > 0) {
            delay(1000L)
            secondsRemaining -= 1
        } else if (secondsRemaining <= 0) {
            isTimerActive = false
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isTimerActive && secondsRemaining > 0) {
            Text(
                text = "Resend code in ${secondsRemaining}s",
                color = Color(0xFF94A3B8),
                fontSize = 13.sp
            )
        } else {
            androidx.compose.material3.TextButton(
                onClick = {
                    secondsRemaining = cooldownSeconds
                    isTimerActive = true
                    onResendClick()
                }
            ) {
                Text(
                    text = "Resend Verification Code",
                    color = accentColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
