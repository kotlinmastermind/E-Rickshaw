package com.luxury.chauffeur.customer.feature.auth

import android.app.Activity

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luxury.chauffeur.customer.core.security.TokenManager

private val DarkBg = Color(0xFF0D0D0F)
private val CardBg = Color(0xFF17171A)
private val CardBorder = Color(0xFF2C2C30)
private val GoldPrimary = Color(0xFFC9A24B)
private val GoldLight = Color(0xFFDFBF74)
private val TextWhite = Color(0xFFF5F5F7)
private val TextMuted = Color(0xFF8E8E93)
private val ErrorRed = Color(0xFFFF453A)
private val SuccessGreen = Color(0xFF34C759)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedAuthScreen(
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val tokenManager = remember { TokenManager.getInstance(context) }
    val viewModel = remember { EnterpriseAuthViewModel(tokenManager) }
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current

    // Identifier input state
    var identifierInput by remember { mutableStateOf("") }

    // Auth mode state: "OTP" or "PASSWORD"
    var activeAuthMode by remember { mutableStateOf("OTP") }
    var passwordInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    // OTP input state
    var otpInput by remember { mutableStateOf("") }

    // Sign up state
    var signUpName by remember { mutableStateOf("") }
    var signUpPhone by remember { mutableStateOf("") }
    var signUpEmail by remember { mutableStateOf("") }
    var signUpOtp by remember { mutableStateOf("") }

    LaunchedEffect(uiState) {
        if (uiState is AuthUiState.Success) {
            onAuthSuccess()
        }
    }

    Scaffold(
        containerColor = DarkBg
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Brand Logo
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(GoldLight, GoldPrimary))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Profile",
                    tint = DarkBg,
                    modifier = Modifier.size(34.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "E-Rickshaw Luxury",
                color = TextWhite,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif
            )

            Text(
                text = "Chauffeur & Commuter Services",
                color = GoldLight,
                fontSize = 12.sp,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Error banner
            if (uiState is AuthUiState.Error) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Error",
                            tint = ErrorRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = (uiState as AuthUiState.Error).message,
                            color = TextWhite,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.dismissError() },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Screen content based on State
            when (val state = uiState) {
                is AuthUiState.Idle, is AuthUiState.Loading, is AuthUiState.Error -> {
                    // STEP 1: Identification Screen
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        border = BorderStroke(1.dp, CardBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            Text(
                                text = "Sign In to E-Rickshaw Luxury",
                                color = TextWhite,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Choose OTP Passcode or Password Login to access your account.",
                                color = TextMuted,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Mode Selector Tabs
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF222226))
                                    .padding(4.dp)
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { activeAuthMode = "OTP" },
                                    color = if (activeAuthMode == "OTP") GoldPrimary else Color.Transparent
                                ) {
                                    Text(
                                        text = "📱 Mobile OTP",
                                        color = if (activeAuthMode == "OTP") DarkBg else TextMuted,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { activeAuthMode = "PASSWORD" },
                                    color = if (activeAuthMode == "PASSWORD") GoldPrimary else Color.Transparent
                                ) {
                                    Text(
                                        text = "🔑 Password",
                                        color = if (activeAuthMode == "PASSWORD") DarkBg else TextMuted,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            if (activeAuthMode == "OTP") {
                                OutlinedTextField(
                                    value = identifierInput,
                                    onValueChange = { identifierInput = it },
                                    label = { Text("Mobile Number", color = TextMuted) },
                                    placeholder = { Text("7562084184 or +91...", color = Color(0xFF48484A)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Phone,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = {
                                            focusManager.clearFocus()
                                            if (identifierInput.isNotBlank()) {
                                                viewModel.lookupAndProceed(identifierInput, activity)
                                            }
                                        }
                                    ),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = GoldPrimary,
                                        unfocusedBorderColor = CardBorder,
                                        focusedTextColor = TextWhite,
                                        unfocusedTextColor = TextWhite,
                                        cursorColor = GoldPrimary
                                    ),
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Phone,
                                            contentDescription = null,
                                            tint = GoldPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                )

                                Spacer(modifier = Modifier.height(20.dp))

                                Button(
                                    onClick = {
                                        focusManager.clearFocus()
                                        viewModel.lookupAndProceed(identifierInput, activity)
                                    },
                                    enabled = identifierInput.trim().length >= 4 && state !is AuthUiState.Loading,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = GoldPrimary,
                                        contentColor = DarkBg,
                                        disabledContainerColor = CardBorder,
                                        disabledContentColor = TextMuted
                                    )
                                ) {
                                    if (state is AuthUiState.Loading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = DarkBg,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(state.message, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    } else {
                                        Text("CONTINUE WITH OTP", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(imageVector = Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                }
                            } else {
                                // PASSWORD LOGIN MODE
                                OutlinedTextField(
                                    value = identifierInput,
                                    onValueChange = { identifierInput = it },
                                    label = { Text("Mobile or Email", color = TextMuted) },
                                    placeholder = { Text("spbadshah85@gmail.com or 7562084184", color = Color(0xFF48484A)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Email,
                                        imeAction = ImeAction.Next
                                    ),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = GoldPrimary,
                                        unfocusedBorderColor = CardBorder,
                                        focusedTextColor = TextWhite,
                                        unfocusedTextColor = TextWhite,
                                        cursorColor = GoldPrimary
                                    ),
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Email,
                                            contentDescription = null,
                                            tint = GoldPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = passwordInput,
                                    onValueChange = { passwordInput = it },
                                    label = { Text("Password", color = TextMuted) },
                                    placeholder = { Text("••••••••", color = Color(0xFF48484A)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = true,
                                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Password,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = {
                                            focusManager.clearFocus()
                                            if (identifierInput.isNotBlank() && passwordInput.isNotBlank()) {
                                                viewModel.loginWithPassword(identifierInput, passwordInput)
                                            }
                                        }
                                    ),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = GoldPrimary,
                                        unfocusedBorderColor = CardBorder,
                                        focusedTextColor = TextWhite,
                                        unfocusedTextColor = TextWhite,
                                        cursorColor = GoldPrimary
                                    ),
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = GoldPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                )

                                Spacer(modifier = Modifier.height(20.dp))

                                Button(
                                    onClick = {
                                        focusManager.clearFocus()
                                        viewModel.loginWithPassword(identifierInput, passwordInput)
                                    },
                                    enabled = identifierInput.trim().isNotEmpty() && passwordInput.trim().isNotEmpty() && state !is AuthUiState.Loading,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = GoldPrimary,
                                        contentColor = DarkBg,
                                        disabledContainerColor = CardBorder,
                                        disabledContentColor = TextMuted
                                    )
                                ) {
                                    if (state is AuthUiState.Loading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = DarkBg,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(state.message, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    } else {
                                        Text("SIGN IN WITH PASSWORD", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(imageVector = Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                is AuthUiState.OtpSent -> {
                    // Auto - fill OTP when received from server
                    LaunchedEffect(state.receivedOtp) {
                        if (!state.receivedOtp.isNullOrBlank()) {
                            otpInput = state.receivedOtp
                        }
                    }

                    // STEP 2A: OTP Verification Screen
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        border = BorderStroke(1.dp, CardBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Verify 6-Digit Passcode",
                                color = TextWhite,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "A one-time passcode has been sent to ${state.phoneMasked ?: state.identifier}.",
                                color = TextMuted,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )

                            // Instant Passcode banner if SMS carrier delivery is pending
                            if (!state.receivedOtp.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(14.dp))
                                Surface(
                                    color = Color(0xFF0F2E20),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Color(0xFF10B981)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            otpInput = state.receivedOtp
                                            viewModel.verifyOtpAndLogin(state.identifier, state.receivedOtp)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Verification Passcode (Auto-Detected)",
                                                color = Color(0xFF34D399),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = state.receivedOtp,
                                                color = Color.White,
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 4.sp
                                            )
                                        }
                                        Surface(
                                            color = Color(0xFF10B981),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "AUTO-FILL",
                                                color = Color.Black,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            OutlinedTextField(
                                value = otpInput,
                                onValueChange = {
                                    if (it.length <= 6 && it.all { ch -> ch.isDigit() }) {
                                        otpInput = it
                                        if (it.length == 6) {
                                            viewModel.verifyOtpAndLogin(state.identifier, it)
                                        }
                                    }
                                },
                                label = { Text("Enter OTP", color = TextMuted) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(
                                    fontSize = 22.sp,
                                    letterSpacing = 8.sp,
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Bold
                                ),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.NumberPassword,
                                    imeAction = ImeAction.Done
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = GoldPrimary,
                                    unfocusedBorderColor = CardBorder,
                                    focusedTextColor = TextWhite,
                                    unfocusedTextColor = TextWhite,
                                    cursorColor = GoldPrimary
                                )
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Resend timer
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (state.countdownSeconds > 0) {
                                    Text(
                                        text = "Resend in ${state.countdownSeconds}s",
                                        color = TextMuted,
                                        fontSize = 12.sp
                                    )
                                } else {
                                    TextButton(
                                        onClick = {
                                            viewModel.resendOtp(state.identifier, state.isExistingUser, activity)
                                        }
                                    ) {
                                        Text("RESEND CODE", color = GoldPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                TextButton(
                                    onClick = { viewModel.resetToIdle() }
                                ) {
                                    Text("Change Number", color = TextMuted, fontSize = 12.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    viewModel.verifyOtpAndLogin(state.identifier, otpInput, state.verificationId)
                                },
                                enabled = otpInput.length >= 4,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = GoldPrimary,
                                    contentColor = DarkBg
                                )
                            ) {
                                Text("VERIFY & ENTER", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                is AuthUiState.RedirectSignUp -> {
                    // STEP 2B: New User Registration Screen
                    LaunchedEffect(Unit) {
                        if (state.isEmail) {
                            signUpEmail = state.initialIdentifier
                        } else {
                            signUpPhone = state.initialIdentifier
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        border = BorderStroke(1.dp, CardBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(GoldPrimary.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Create VIP Account",
                                        color = TextWhite,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "New user detected. Welcome to luxury mobility.",
                                        color = GoldLight,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            OutlinedTextField(
                                value = signUpName,
                                onValueChange = { signUpName = it },
                                label = { Text("Full Name *", color = TextMuted) },
                                placeholder = { Text("e.g. Sunil Kumar", color = Color(0xFF48484A)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = GoldPrimary,
                                    unfocusedBorderColor = CardBorder,
                                    focusedTextColor = TextWhite,
                                    unfocusedTextColor = TextWhite,
                                    cursorColor = GoldPrimary
                                )
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = signUpPhone,
                                onValueChange = { signUpPhone = it },
                                label = { Text("Mobile Number *", color = TextMuted) },
                                placeholder = { Text("+91 98765 43210", color = Color(0xFF48484A)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = GoldPrimary,
                                    unfocusedBorderColor = CardBorder,
                                    focusedTextColor = TextWhite,
                                    unfocusedTextColor = TextWhite,
                                    cursorColor = GoldPrimary
                                )
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = signUpEmail,
                                onValueChange = { signUpEmail = it },
                                label = { Text("Email Address (Optional)", color = TextMuted) },
                                placeholder = { Text("sunil@example.com", color = Color(0xFF48484A)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = GoldPrimary,
                                    unfocusedBorderColor = CardBorder,
                                    focusedTextColor = TextWhite,
                                    unfocusedTextColor = TextWhite,
                                    cursorColor = GoldPrimary
                                )
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    viewModel.completeRegistration(
                                        fullName = signUpName,
                                        email = signUpEmail,
                                        phone = signUpPhone,
                                        otpCode = null
                                    )
                                },
                                enabled = signUpName.isNotBlank() && signUpPhone.isNotBlank(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = GoldPrimary,
                                    contentColor = DarkBg
                                )
                            ) {
                                Text("COMPLETE REGISTRATION", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            TextButton(
                                onClick = { viewModel.resetToIdle() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Already have an account? Sign In", color = TextMuted, fontSize = 12.sp)
                            }
                        }
                    }
                }

                is AuthUiState.Success -> {
                    CircularProgressIndicator(color = GoldPrimary)
                }
            }
        }
    }
}

