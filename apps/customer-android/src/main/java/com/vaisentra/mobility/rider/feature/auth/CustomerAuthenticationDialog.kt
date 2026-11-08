package com.vaisentra.mobility.rider.feature.auth

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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vaisentra.mobility.rider.core.designsystem.*
import com.vaisentra.mobility.rider.core.network.*
import com.vaisentra.mobility.rider.core.security.TokenManager
import kotlinx.coroutines.launch

@Composable
fun CustomerAuthenticationDialog(
    onDismiss: () -> Unit,
    onAuthenticationSuccess: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var authTab by remember { mutableStateOf("SIGNIN") } // SIGNUP, SIGNIN, FORGOT_PASSWORD

    // Form states
    var fullName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

        var isSubmitting by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(LuxuryDimensions.RadiusLarge)),
            color = LuxuryColors.ObsidianBlack,
            border = BorderStroke(1.5.dp, LuxuryColors.ChampagneGold)
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
                            text = "AUTHENTICATION SANCTUARY",
                            style = LuxuryTypography.HeadlineMedium.copy(
                                color = LuxuryColors.ChampagneGold,
                                fontSize = 18.sp
                            )
                        )
                        Text(
                            text = "Customer Account Portal",
                            style = LuxuryTypography.BodySubtle
                        )
                    }
                    Text(
                        text = "✕",
                        style = LuxuryTypography.TitleGold.copy(fontSize = 18.sp),
                        modifier = Modifier.clickable { onDismiss() }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Navigation Tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("SIGNIN" to "Sign In", "SIGNUP" to "Sign Up", "FORGOT_PASSWORD" to "Forgot Pass").forEach { (key, label) ->
                        val isSelected = authTab == key
                        Surface(
                            shape = RoundedCornerShape(LuxuryDimensions.RadiusPill),
                            color = if (isSelected) LuxuryColors.ChampagneGold else LuxuryColors.CardSurface,
                            border = BorderStroke(1.dp, if (isSelected) LuxuryColors.ChampagneGold else LuxuryColors.CardBorder),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    authTab = key
                                    errorMessage = null
                                    statusMessage = null
                                }
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) LuxuryColors.ObsidianBlack else LuxuryColors.PlatinumWhite,
                                style = LuxuryTypography.TitleGold.copy(
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                modifier = Modifier.padding(vertical = 8.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (errorMessage != null) {
                    Surface(
                        color = LuxuryColors.CrimsonAlert.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                        border = BorderStroke(1.dp, LuxuryColors.CrimsonAlert),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        Text(
                            text = errorMessage!!,
                            color = LuxuryColors.CrimsonAlert,
                            style = LuxuryTypography.BodySubtle,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                if (statusMessage != null) {
                    Surface(
                        color = LuxuryColors.LuxuryEmerald.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                        border = BorderStroke(1.dp, LuxuryColors.LuxuryEmerald),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        Text(
                            text = statusMessage!!,
                            color = LuxuryColors.LuxuryEmerald,
                            style = LuxuryTypography.BodySubtle,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                when (authTab) {
                    "SIGNIN" -> {
                        LuxuryCard(modifier = Modifier.fillMaxWidth()) {
                            Text("SIGN IN TO YOUR ACCOUNT", style = LuxuryTypography.TitleGold.copy(fontSize = 13.sp))
                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = phoneNumber,
                                onValueChange = { phoneNumber = it },
                                label = { Text("Username, Email ID, or Mobile Phone", color = LuxuryColors.SubtleGray) },
                                textStyle = LuxuryTypography.BodyRegular,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = LuxuryColors.ChampagneGold,
                                    unfocusedBorderColor = LuxuryColors.CardBorder,
                                    focusedTextColor = LuxuryColors.PlatinumWhite,
                                    unfocusedTextColor = LuxuryColors.PlatinumWhite
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("Password", color = LuxuryColors.SubtleGray) },
                                visualTransformation = PasswordVisualTransformation(),
                                textStyle = LuxuryTypography.BodyRegular,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = LuxuryColors.ChampagneGold,
                                    unfocusedBorderColor = LuxuryColors.CardBorder,
                                    focusedTextColor = LuxuryColors.PlatinumWhite,
                                    unfocusedTextColor = LuxuryColors.PlatinumWhite
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            LuxuryButton(
                                text = if (isSubmitting) "Authenticating..." else "Sign In",
                                onClick = {
                                    val clean = phoneNumber.trim().replace(" ", "").replace("-", "")
                                    if (clean.isBlank()) {
                                        errorMessage = "Please enter your username, email or mobile phone."
                                        return@LuxuryButton
                                    }
                                    if (password.isBlank()) {
                                        errorMessage = "Please enter your password."
                                        return@LuxuryButton
                                    }
                                    val formatted = normalizePhoneNumber(clean)
                                    isSubmitting = true
                                    errorMessage = null
                                    scope.launch {
                                        try {
                                            val res = ApiClient.getService().unifiedLogin(
                                                UnifiedLoginRequestPayload(
                                                    loginType = "PASSWORD",
                                                    identifier = formatted,
                                                    password = password
                                                )
                                            )
                                            if (res.isSuccessful && res.body() != null) {
                                                val tokenResp = res.body()!!
                                                TokenManager.getInstance(context).saveAuth(
                                                    token = tokenResp.accessToken,
                                                    userId = tokenResp.user.id,
                                                    phone = tokenResp.user.phoneNumber,
                                                    role = tokenResp.user.role,
                                                    email = tokenResp.user.email,
                                                    fullName = tokenResp.user.fullName
                                                )
                                                Toast.makeText(context, "Sign In Successful!", Toast.LENGTH_SHORT).show()
                                                onAuthenticationSuccess(tokenResp.accessToken)
                                                onDismiss()
                                            } else {
                                                val err = res.errorBody()?.string() ?: ""
                                                errorMessage = if (err.contains("detail")) err else "Sign In failed: Invalid credentials."
                                            }
                                        } catch (e: Exception) {
                                            errorMessage = "Sign In Error: ${e.localizedMessage}"
                                        } finally {
                                            isSubmitting = false
                                        }
                                    }
                                },
                                isLoading = isSubmitting
                            )
                        }
                    }

                    "SIGNUP" -> {
                        LuxuryCard(modifier = Modifier.fillMaxWidth()) {
                            Text("CREATE NEW CUSTOMER ACCOUNT", style = LuxuryTypography.TitleGold.copy(fontSize = 13.sp))
                            Spacer(modifier = Modifier.height(8.dp))

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
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            OutlinedTextField(
                                value = phoneNumber,
                                onValueChange = { phoneNumber = it },
                                label = { Text("Mobile Phone Number (+91...)", color = LuxuryColors.SubtleGray) },
                                textStyle = LuxuryTypography.BodyRegular,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = LuxuryColors.ChampagneGold,
                                    unfocusedBorderColor = LuxuryColors.CardBorder,
                                    focusedTextColor = LuxuryColors.PlatinumWhite,
                                    unfocusedTextColor = LuxuryColors.PlatinumWhite
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it },
                                label = { Text("Email Address", color = LuxuryColors.SubtleGray) },
                                textStyle = LuxuryTypography.BodyRegular,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = LuxuryColors.ChampagneGold,
                                    unfocusedBorderColor = LuxuryColors.CardBorder,
                                    focusedTextColor = LuxuryColors.PlatinumWhite,
                                    unfocusedTextColor = LuxuryColors.PlatinumWhite
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            OutlinedTextField(
                                value = username,
                                onValueChange = { username = it },
                                label = { Text("Unique Username", color = LuxuryColors.SubtleGray) },
                                textStyle = LuxuryTypography.BodyRegular,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = LuxuryColors.ChampagneGold,
                                    unfocusedBorderColor = LuxuryColors.CardBorder,
                                    focusedTextColor = LuxuryColors.PlatinumWhite,
                                    unfocusedTextColor = LuxuryColors.PlatinumWhite
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("Password (min 6 chars)", color = LuxuryColors.SubtleGray) },
                                visualTransformation = PasswordVisualTransformation(),
                                textStyle = LuxuryTypography.BodyRegular,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = LuxuryColors.ChampagneGold,
                                    unfocusedBorderColor = LuxuryColors.CardBorder,
                                    focusedTextColor = LuxuryColors.PlatinumWhite,
                                    unfocusedTextColor = LuxuryColors.PlatinumWhite
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            LuxuryButton(
                                text = if (isSubmitting) "Creating Account..." else "Submit Sign Up",
                                onClick = {
                                    if (fullName.isBlank() || phoneNumber.isBlank() || password.isBlank()) {
                                        errorMessage = "Full name, mobile number, and password are required."
                                        return@LuxuryButton
                                    }
                                    val cleanPhone = normalizePhoneNumber(phoneNumber)
                                    isSubmitting = true
                                    errorMessage = null
                                    scope.launch {
                                        try {
                                            val res = ApiClient.getService().signupCustomer(
                                                CustomerSignupRequestPayload(
                                                    phoneNumber = cleanPhone,
                                                    email = email.trim().ifBlank { null },
                                                    fullName = fullName.trim(),
                                                    password = password,
                                                    username = username.trim().ifBlank { null }
                                                )
                                            )
                                            if (res.isSuccessful && res.body() != null) {
                                                val tokenResp = res.body()!!
                                                TokenManager.getInstance(context).saveAuth(
                                                    token = tokenResp.accessToken,
                                                    userId = tokenResp.user.id,
                                                    phone = tokenResp.user.phoneNumber,
                                                    role = tokenResp.user.role,
                                                    email = tokenResp.user.email,
                                                    fullName = tokenResp.user.fullName
                                                )
                                                Toast.makeText(context, "Account Registered Successfully!", Toast.LENGTH_SHORT).show()
                                                onAuthenticationSuccess(tokenResp.accessToken)
                                                onDismiss()
                                            } else {
                                                val err = res.errorBody()?.string() ?: ""
                                                errorMessage = if (err.contains("detail")) err else "Registration Failed. Check if email/username already taken."
                                            }
                                        } catch (e: Exception) {
                                            errorMessage = "Registration Error: ${e.localizedMessage}"
                                        } finally {
                                            isSubmitting = false
                                        }
                                    }
                                },
                                isLoading = isSubmitting
                            )
                        }
                    }

                    "FORGOT_PASSWORD" -> {
                        LuxuryCard(modifier = Modifier.fillMaxWidth()) {
                            Text("ACCOUNT RECOVERY & SUPPORT", style = LuxuryTypography.TitleGold.copy(fontSize = 13.sp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Automated SMS OTP password reset has been decommissioned. For credential assistance and account access, please reach out to our platform administration support team:",
                                style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.PlatinumWhite, fontSize = 12.sp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                "Email: support@vaisentra.com\nGrievance: privacy@erickshaw.in",
                                style = LuxuryTypography.TitleGold.copy(fontSize = 13.sp, color = LuxuryColors.ChampagneGold)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            LuxuryButton(
                                text = "Back to Sign In",
                                onClick = {
                                    authTab = "SIGNIN"
                                    errorMessage = null
                                    statusMessage = null
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
