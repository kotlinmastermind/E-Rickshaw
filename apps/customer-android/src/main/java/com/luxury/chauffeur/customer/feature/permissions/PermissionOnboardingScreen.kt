package com.luxury.chauffeur.customer.feature.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

private val DarkBg = Color(0xFF0D0D0F)
private val CardBg = Color(0xFF17171A)
private val CardBorder = Color(0xFF2C2C30)
private val GoldPrimary = Color(0xFFC9A24B)
private val GoldLight = Color(0xFFDFBF74)
private val TextWhite = Color(0xFFF5F5F7)
private val TextMuted = Color(0xFF8E8E93)
private val SuccessGreen = Color(0xFF34C759)

data class PermissionItem(
    val title: String,
    val purpose: String,
    val icon: ImageVector,
    val permissions: List<String>,
    val isMandatory: Boolean = true
)

object PermissionManager {
    private const val PREF_NAME = "luxury_permissions_pref"
    private const val KEY_PERMISSIONS_ONBOARDED = "permissions_onboarded"

    fun isPermissionOnboardingCompleted(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_PERMISSIONS_ONBOARDED, false) && hasMandatoryPermissions(context)
    }

    fun setPermissionOnboardingCompleted(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_PERMISSIONS_ONBOARDED, true).apply()
    }

    fun hasMandatoryPermissions(context: Context): Boolean {
        val hasLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return hasLocation
    }

    fun getRequiredPermissionsList(): Array<String> {
        val list = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
            list.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            list.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        return list.toTypedArray()
    }
}

@Composable
fun PermissionOnboardingScreen(
    onPermissionsCompleted: () -> Unit
) {
    val context = LocalContext.current
    var permissionTriggerKey by remember { mutableIntStateOf(0) }

    fun checkGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    val locationGranted = checkGranted(Manifest.permission.ACCESS_FINE_LOCATION) ||
            checkGranted(Manifest.permission.ACCESS_COARSE_LOCATION)
    val cameraGranted = checkGranted(Manifest.permission.CAMERA)
    val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        checkGranted(Manifest.permission.POST_NOTIFICATIONS)
    } else true
    val mediaGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        checkGranted(Manifest.permission.READ_MEDIA_IMAGES)
    } else {
        checkGranted(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    val allMandatoryGranted = locationGranted && cameraGranted && notificationGranted

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionTriggerKey++
    }

    val permissionCards = remember(permissionTriggerKey) {
        listOf(
            PermissionItem(
                title = "Precise Geolocation",
                purpose = "Required to accurately detect your pickup address, navigate routes in real time, and locate nearby E-Rickshaw pilots.",
                icon = Icons.Default.LocationOn,
                permissions = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                isMandatory = true
            ),
            PermissionItem(
                title = "Camera Access",
                purpose = "Required for profile photos, scanning pilot QR check-in codes, and support ticket image attachments.",
                icon = Icons.Default.ThumbUp,
                permissions = listOf(Manifest.permission.CAMERA),
                isMandatory = true
            ),
            PermissionItem(
                title = "Live Ride Notifications",
                purpose = "Required to receive live trip updates, pilot arrival alerts, OTP passcodes, and emergency security notifications.",
                icon = Icons.Default.Notifications,
                permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    listOf(Manifest.permission.POST_NOTIFICATIONS)
                } else emptyList(),
                isMandatory = true
            ),
            PermissionItem(
                title = "Media & Document Storage",
                purpose = "Required to save travel tax invoices, receipt PDFs, and attach trip dispute documents.",
                icon = Icons.Default.Star,
                permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    listOf(Manifest.permission.READ_MEDIA_IMAGES)
                } else listOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                isMandatory = false
            )
        )
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

            // Brand Header
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(GoldLight, GoldPrimary))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Security",
                    tint = DarkBg,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Welcome to E-Rickshaw Luxury",
                color = TextWhite,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Before you begin your journey, please grant the essential device permissions below to ensure a seamless, secure, and live-tracked ride experience.",
                color = TextMuted,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Permission Cards List
            permissionCards.forEach { item ->
                val isGranted = when (item.title) {
                    "Precise Geolocation" -> locationGranted
                    "Camera Access" -> cameraGranted
                    "Live Ride Notifications" -> notificationGranted
                    else -> mediaGranted
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    border = BorderStroke(1.dp, if (isGranted) SuccessGreen.copy(alpha = 0.5f) else CardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isGranted) SuccessGreen.copy(alpha = 0.15f) else GoldPrimary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.title,
                                tint = if (isGranted) SuccessGreen else GoldPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = item.title,
                                    color = TextWhite,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (item.isMandatory) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "• REQUIRED",
                                        color = if (isGranted) SuccessGreen else GoldLight,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = item.purpose,
                                color = TextMuted,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        if (isGranted) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Granted",
                                tint = SuccessGreen,
                                modifier = Modifier.size(22.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(CardBorder),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "!",
                                    color = GoldPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            if (!allMandatoryGranted) {
                Button(
                    onClick = {
                        launcher.launch(PermissionManager.getRequiredPermissionsList())
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoldPrimary,
                        contentColor = DarkBg
                    )
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "GRANT PERMISSIONS",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 0.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                        context.startActivity(intent)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Icon(imageVector = Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp), tint = TextMuted)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "OPEN APP SETTINGS",
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
            } else {
                Button(
                    onClick = {
                        PermissionManager.setPermissionOnboardingCompleted(context)
                        onPermissionsCompleted()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SuccessGreen,
                        contentColor = Color.White
                    )
                ) {
                    Icon(imageVector = Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "CONTINUE TO LOGIN",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Your privacy is guaranteed. Location data is only shared with assigned pilots during active transit.",
                color = Color(0xFF636366),
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

