package com.vaisentra.mobility.rider.feature.auth

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaisentra.mobility.rider.core.designsystem.MobilityColors
import com.vaisentra.mobility.rider.core.network.ApiClient
import com.vaisentra.mobility.rider.core.network.CustomerSignupRequestPayload
import com.vaisentra.mobility.rider.core.network.UnifiedLoginRequestPayload
import com.vaisentra.mobility.rider.core.security.TokenManager
import kotlinx.coroutines.launch

private val BrandGold = Color(0xFFD4AF37)


enum class AuthTab {
    SIGN_IN,
    SIGN_UP
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerAuthScreen(
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tokenManager = remember { TokenManager.getInstance(context) }

    var selectedTab by remember { mutableStateOf(AuthTab.SIGN_IN) }

    // Sign In states
    var signInIdentifier by remember { mutableStateOf("") }
    var signInPassword by remember { mutableStateOf("") }

    // Sign Up states
    var signUpName by remember { mutableStateOf("") }
    var signUpPhone by remember { mutableStateOf("") }
    var signUpEmail by remember { mutableStateOf("") }
    var signUpPassword by remember { mutableStateOf("") }
    var signUpConfirmPassword by remember { mutableStateOf("") }

    // UI state
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showPassword by remember { mutableStateOf(false) }

    fun normalizePhone(raw: String): String {
        val clean = raw.trim().replace(" ", "").replace("-", "")
        return if (clean.startsWith("+")) clean else if (clean.length == 10) "+91$clean" else clean
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Header
            Text("🚖", fontSize = 48.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "RideGo",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = BrandGold
            )
            Text(
                text = "Smart, Clean & Affordable Electric Commute",
                fontSize = 13.sp,
                color = Color.LightGray
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Tab Selector: Sign In / Sign Up
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E293B), RoundedCornerShape(12.dp))
                    .padding(4.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (selectedTab == AuthTab.SIGN_IN) BrandGold else Color.Transparent,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            selectedTab = AuthTab.SIGN_IN
                            errorMessage = null
                        }
                ) {
                    Text(
                        text = "Sign In",
                        color = if (selectedTab == AuthTab.SIGN_IN) Color.Black else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 10.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (selectedTab == AuthTab.SIGN_UP) BrandGold else Color.Transparent,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            selectedTab = AuthTab.SIGN_UP
                            errorMessage = null
                        }
                ) {
                    Text(
                        text = "Sign Up",
                        color = if (selectedTab == AuthTab.SIGN_UP) Color.Black else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 10.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Error Display
            if (!errorMessage.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFEF4444).copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, Color(0xFFEF4444)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Text(
                        text = errorMessage ?: "",
                        color = Color(0xFFFCA5A5),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            if (selectedTab == AuthTab.SIGN_IN) {
                // SIGN IN FORM (Email or Mobile + Password)
                OutlinedTextField(
                    value = signInIdentifier,
                    onValueChange = { signInIdentifier = it },
                    label = { Text("Email or Mobile Number", color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = BrandGold,
                        unfocusedBorderColor = Color.DarkGray
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = signInPassword,
                    onValueChange = { signInPassword = it },
                    label = { Text("Password", color = Color.Gray) },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.Check else Icons.Default.Lock,
                                contentDescription = "Toggle password",
                                tint = Color.Gray
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = BrandGold,
                        unfocusedBorderColor = Color.DarkGray
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (signInIdentifier.isBlank() || signInPassword.isBlank()) {
                            errorMessage = "Please enter email/mobile number and password."
                            return@Button
                        }
                        val ident = if (signInIdentifier.contains("@")) {
                            signInIdentifier.trim().lowercase()
                        } else {
                            normalizePhone(signInIdentifier)
                        }
                        val pwd = signInPassword
                        // Clear inputs immediately on submit
                        signInIdentifier = ""
                        signInPassword = ""
                        isLoading = true
                        errorMessage = null
                        scope.launch {
                            try {
                                val res = ApiClient.getService().unifiedLogin(
                                    UnifiedLoginRequestPayload(
                                        loginType = "PASSWORD",
                                        identifier = ident,
                                        password = pwd
                                    )
                                )

                                if (res.isSuccessful && res.body()?.accessToken != null) {
                                    val token = res.body()!!.accessToken
                                    val uid = res.body()?.user?.id ?: "usr_customer"
                                    tokenManager.saveAuth(token, uid, ident, "customer")
                                    Toast.makeText(context, "Sign in successful!", Toast.LENGTH_SHORT).show()
                                    onAuthSuccess()
                                } else {
                                    val err = res.errorBody()?.string() ?: "Invalid credentials. Please try again."
                                    errorMessage = if (err.contains("detail")) err else "Invalid email/mobile or password."
                                }
                            } catch (e: Exception) {
                                errorMessage = "Network error: ${e.localizedMessage ?: "Could not reach server"}"
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandGold, contentColor = Color.Black),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
                    } else {
                        Text("SIGN IN", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }

            } else {
                // SIGN UP FORM (Full Name, Mobile, Email, Password)
                OutlinedTextField(
                    value = signUpName,
                    onValueChange = { signUpName = it },
                    label = { Text("Full Name", color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = BrandGold,
                        unfocusedBorderColor = Color.DarkGray
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = signUpPhone,
                    onValueChange = { signUpPhone = it },
                    label = { Text("Mobile Number (e.g. 9876543210)", color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = BrandGold,
                        unfocusedBorderColor = Color.DarkGray
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = signUpEmail,
                    onValueChange = { signUpEmail = it },
                    label = { Text("Email Address (Optional)", color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = BrandGold,
                        unfocusedBorderColor = Color.DarkGray
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = signUpPassword,
                    onValueChange = { signUpPassword = it },
                    label = { Text("Password (min 4 characters)", color = Color.Gray) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = BrandGold,
                        unfocusedBorderColor = Color.DarkGray
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = signUpConfirmPassword,
                    onValueChange = { signUpConfirmPassword = it },
                    label = { Text("Confirm Password", color = Color.Gray) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = BrandGold,
                        unfocusedBorderColor = Color.DarkGray
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (signUpName.isBlank()) {
                            errorMessage = "Please enter your full name."
                            return@Button
                        }
                        if (signUpPhone.isBlank() && signUpEmail.isBlank()) {
                            errorMessage = "Please enter either a mobile number or an email."
                            return@Button
                        }
                        if (signUpPassword.length < 4) {
                            errorMessage = "Password must be at least 4 characters."
                            return@Button
                        }
                        if (signUpPassword != signUpConfirmPassword) {
                            errorMessage = "Passwords do not match."
                            return@Button
                        }

                        val name = signUpName.trim()
                        val phoneFormatted = if (signUpPhone.isNotBlank()) normalizePhone(signUpPhone) else null
                        val emailClean = if (signUpEmail.isNotBlank()) signUpEmail.trim().lowercase() else null
                        val pwd = signUpPassword

                        // Clear inputs immediately on submit
                        signUpName = ""
                        signUpPhone = ""
                        signUpEmail = ""
                        signUpPassword = ""
                        signUpConfirmPassword = ""

                        isLoading = true
                        errorMessage = null
                        scope.launch {
                            try {
                                val res = ApiClient.getService().signupCustomer(
                                    CustomerSignupRequestPayload(
                                        fullName = name,
                                        phoneNumber = phoneFormatted,
                                        email = emailClean,
                                        username = emailClean?.substringBefore("@") ?: ("user_" + (phoneFormatted?.takeLast(4) ?: "guest")),
                                        password = pwd
                                    )
                                )

                                if (res.isSuccessful && res.body()?.accessToken != null) {
                                    val token = res.body()!!.accessToken
                                    val uid = res.body()?.user?.id ?: "usr_customer"
                                    tokenManager.saveAuth(token, uid, phoneFormatted ?: emailClean ?: "customer", "customer")
                                    Toast.makeText(context, "Account created successfully!", Toast.LENGTH_SHORT).show()
                                    onAuthSuccess()
                                } else {
                                    val err = res.errorBody()?.string() ?: "Registration failed."
                                    errorMessage = if (err.contains("detail")) err else "Account with this mobile or email may already exist."
                                }
                            } catch (e: Exception) {
                                errorMessage = "Network error: ${e.localizedMessage ?: "Could not reach server"}"
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandGold, contentColor = Color.Black),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
                    } else {
                        Text("CREATE ACCOUNT", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
