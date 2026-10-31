package com.luxury.chauffeur.driver.feature.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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

private val DarkBg = Color(0xFF090A0F)
private val CardBg = Color(0xFF13151F)
private val CardBorder = Color(0xFF282C42)
private val GoldPrimary = Color(0xFFD4AF37)
private val GoldLight = Color(0xFFE8D08D)
private val TextWhite = Color(0xFFF0F1F5)
private val TextMuted = Color(0xFF8C90A4)
private val SuccessGreen = Color(0xFF22C55E)

object DriverPermissionManager {
    private const val PREF_NAME = "driver_permissions_pref"
    private const val KEY_ONBOARDED = "driver_permissions_onboarded"

    fun isOnboarded(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ONBOARDED, false) && hasCorePermissions(context)
    }

    fun setOnboarded(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ONBOARDED, true).apply()
    }

    fun hasCorePermissions(context: Context): Boolean {
        val location = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return location
    }

    fun getRequiredPermissions(): Array<String> {
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

data class DriverPermissionItem(
    val title: String,
    val purpose: String,
    val icon: ImageVector,
    val isRequired: Boolean = true
)

@Composable
fun DriverPermissionOnboardingScreen(
    onPermissionsCompleted: () -> Unit
) {
    val context = LocalContext.current
    var refreshKey by remember { mutableIntStateOf(0) }

    fun check(p: String): Boolean = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    val locationGranted = check(Manifest.permission.ACCESS_FINE_LOCATION) || check(Manifest.permission.ACCESS_COARSE_LOCATION)
    val cameraGranted = check(Manifest.permission.CAMERA)
    val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) check(Manifest.permission.POST_NOTIFICATIONS) else true
    val storageGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) check(Manifest.permission.READ_MEDIA_IMAGES) else check(Manifest.permission.READ_EXTERNAL_STORAGE)

    val allMandatory = locationGranted && cameraGranted && notifGranted

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refreshKey++
    }

    Scaffold(containerColor = DarkBg) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(GoldLight, GoldPrimary))),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "🛺", fontSize = 32.sp)
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Pilot Cockpit Permissions",
                color = TextWhite,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "To receive nearby luxury ride dispatches, navigate pickup routes, and manage KYC documents, grant the essential pilot permissions below.",
                color = TextMuted,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            val items = listOf(
                DriverPermissionItem(
                    title = "Continuous Pilot Location",
                    purpose = "Used to match you with nearby customers and broadcast live trip progress on passenger maps.",
                    icon = Icons.Default.LocationOn,
                    isRequired = true
                ),
                DriverPermissionItem(
                    title = "Dispatch & Fare Notifications",
                    purpose = "Required to alert you instantly when a new passenger ride offer is assigned.",
                    icon = Icons.Default.Notifications,
                    isRequired = true
                ),
                DriverPermissionItem(
                    title = "Camera for Inspection & KYC",
                    purpose = "Required to photograph your driving license, vehicle fitness, and passenger luggage checks.",
                    icon = Icons.Default.ThumbUp,
                    isRequired = true
                ),
                DriverPermissionItem(
                    title = "Document Storage",
                    purpose = "Required to upload commercial permits, RC book scans, and insurance certificates.",
                    icon = Icons.Default.Star,
                    isRequired = false
                )
            )

            items.forEach { item ->
                val isGranted = when (item.title) {
                    "Continuous Pilot Location" -> locationGranted
                    "Dispatch & Fare Notifications" -> notifGranted
                    "Camera for Inspection & KYC" -> cameraGranted
                    else -> storageGranted
                }

                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    border = BorderStroke(1.dp, if (isGranted) SuccessGreen.copy(alpha = 0.5f) else CardBorder)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
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
                                Text(text = item.title, color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                if (item.isRequired) {
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
                            Text(text = item.purpose, color = TextMuted, fontSize = 12.sp, lineHeight = 16.sp)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        if (isGranted) {
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Granted", tint = SuccessGreen, modifier = Modifier.size(22.dp))
                        } else {
                            Text(text = "!", color = GoldPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (!allMandatory) {
                Button(
                    onClick = { launcher.launch(DriverPermissionManager.getRequiredPermissions()) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = DarkBg)
                ) {
                    Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "GRANT PILOT PERMISSIONS", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Icon(imageVector = Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp), tint = TextMuted)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "OPEN APP SETTINGS", fontSize = 12.sp, color = TextMuted)
                }
            } else {
                Button(
                    onClick = {
                        DriverPermissionManager.setOnboarded(context)
                        onPermissionsCompleted()
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = Color.White)
                ) {
                    Icon(imageVector = Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "CONTINUE TO PILOT SIGN IN", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

