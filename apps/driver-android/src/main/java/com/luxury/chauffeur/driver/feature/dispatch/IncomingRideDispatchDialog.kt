package com.luxury.chauffeur.driver.feature.dispatch

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.luxury.chauffeur.driver.core.designsystem.*
import kotlinx.coroutines.delay

/**
 * Full-screen incoming ride dispatch alert dialog.
 * Features:
 * - Audible buzzer/ringtone and vibration pattern while ringing
 * - Customer rating badge (e.g. ★ 4.9 VIP)
 * - Pickup, Drop-off, estimated fare and distance
 * - 15-30s animated [CountdownRing]
 * - Instant Accept & Reject with reason
 * - Automated rejection when timer hits zero
 */
@Composable
fun IncomingRideDispatchDialog(
    rideId: String,
    pickupAddress: String,
    destinationAddress: String,
    fare: String,
    distanceKm: String = "2.4 km",
    customerName: String = "VIP Commuter",
    customerRating: Double = 4.92,
    totalSeconds: Int = 20,
    onAccept: () -> Unit,
    onReject: (reason: String) -> Unit,
    onTimeout: () -> Unit
) {
    val context = LocalContext.current
    var secondsRemaining by remember { mutableIntStateOf(totalSeconds) }
    var ringtone by remember { mutableStateOf<Ringtone?>(null) }
    var isAccepting by remember { mutableStateOf(false) }

    // Start audible alarm & haptic vibration while incoming
    DisposableEffect(Unit) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        try {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val r = RingtoneManager.getRingtone(context, ringtoneUri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                r?.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }
            r?.play()
            ringtone = r
        } catch (_: Exception) {}

        // Vibration pattern: wait 0ms, vibrate 500ms, sleep 300ms, vibrate 500ms
        try {
            val pattern = longArrayOf(0, 500, 300, 500, 300, 500)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, -1)
            }
        } catch (_: Exception) {}

        onDispose {
            try {
                ringtone?.stop()
                vibrator?.cancel()
            } catch (_: Exception) {}
        }
    }

    // Countdown timer
    LaunchedEffect(rideId) {
        while (secondsRemaining > 0) {
            delay(1000)
            secondsRemaining--
        }
        ringtone?.stop()
        onTimeout()
    }

    Dialog(
        onDismissRequest = { /* Modal must be explicitly accepted or rejected */ },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = DriverColors.MidnightBlack,
            border = BorderStroke(2.dp, DriverColors.ChauffeurGold)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header & Countdown Ring
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = DriverColors.ChauffeurGold.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, DriverColors.ChauffeurGold)
                        ) {
                            Text(
                                text = "⚡ VIP DISPATCH OFFER",
                                color = DriverColors.ChauffeurGold,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        Text(
                            text = "Ride #$rideId",
                            style = DriverTypography.Body.copy(color = DriverColors.TextMuted, fontSize = 12.sp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Animated Circular Countdown Ring
                    CountdownRing(
                        remainingSeconds = secondsRemaining,
                        totalSeconds = totalSeconds,
                        size = 80.dp,
                        strokeWidth = 6.dp
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Auto-rejects in $secondsRemaining seconds",
                        color = if (secondsRemaining <= 5) DriverColors.AlertDanger else DriverColors.TextMuted,
                        fontSize = 11.sp,
                        fontWeight = if (secondsRemaining <= 5) FontWeight.Bold else FontWeight.Normal
                    )
                }

                // Customer & Trip Spec Card
                LuxCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = DriverColors.ChauffeurGold.copy(alpha = 0.6f)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        // Customer Profile & Rating
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    modifier = Modifier.size(40.dp),
                                    shape = CircleShape,
                                    color = DriverColors.CockpitDark,
                                    border = BorderStroke(1.dp, DriverColors.ChauffeurGold)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("👤", fontSize = 18.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = customerName,
                                        style = DriverTypography.Subtitle.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "VIP Guest",
                                        style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.TextMuted)
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = DriverColors.CockpitDark,
                                border = BorderStroke(1.dp, DriverColors.ChauffeurGold)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("★", color = DriverColors.ChauffeurGold, fontSize = 12.sp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "%.2f".format(customerRating),
                                        color = DriverColors.TextPure,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = DriverColors.SurfaceBorder, thickness = 0.8.dp)

                        // Pickup Address
                        Row(verticalAlignment = Alignment.Top) {
                            Text("📍", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "PICKUP LOCATION",
                                    style = DriverTypography.Body.copy(fontSize = 10.sp, color = DriverColors.TextMuted, fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = pickupAddress,
                                    style = DriverTypography.Body.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
                                    maxLines = 2
                                )
                            }
                        }

                        // Dropoff Address
                        Row(verticalAlignment = Alignment.Top) {
                            Text("🏁", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "DROP-OFF DESTINATION",
                                    style = DriverTypography.Body.copy(fontSize = 10.sp, color = DriverColors.TextMuted, fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = destinationAddress,
                                    style = DriverTypography.Body.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
                                    maxLines = 2
                                )
                            }
                        }

                        HorizontalDivider(color = DriverColors.SurfaceBorder, thickness = 0.8.dp)

                        // Estimated Fare & Distance
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "ESTIMATED FARE",
                                    style = DriverTypography.Body.copy(fontSize = 10.sp, color = DriverColors.TextMuted)
                                )
                                Text(
                                    text = fare,
                                    style = DriverTypography.MetricValue.copy(fontSize = 26.sp, color = DriverColors.ChauffeurGold)
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "PICKUP DISTANCE",
                                    style = DriverTypography.Body.copy(fontSize = 10.sp, color = DriverColors.TextMuted)
                                )
                                Text(
                                    text = distanceKm,
                                    style = DriverTypography.Subtitle.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }

                // Action Buttons: Reject / Accept
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    SecondaryButton(
                        text = "REJECT",
                        onClick = {
                            ringtone?.stop()
                            onReject("PILOT_DECLINED")
                        },
                        modifier = Modifier.weight(1f)
                    )

                    PrimaryButton(
                        text = "ACCEPT RIDE",
                        isLoading = isAccepting,
                        onClick = {
                            isAccepting = true
                            ringtone?.stop()
                            onAccept()
                        },
                        modifier = Modifier.weight(1.3f)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun IncomingRideDispatchDialogPreview() {
    IncomingRideDispatchDialog(
        rideId = "RIDE-9981",
        pickupAddress = "Terminal 3 Arrival VIP Lounge, New Delhi Airport",
        destinationAddress = "The Imperial Hotel, Janpath, Connaught Place",
        fare = "₹450",
        distanceKm = "1.8 km pickup",
        customerName = "Col. Vikramaditya",
        customerRating = 4.95,
        totalSeconds = 25,
        onAccept = {},
        onReject = {},
        onTimeout = {}
    )
}

