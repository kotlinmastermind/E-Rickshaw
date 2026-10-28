package com.luxury.chauffeur.driver.feature.dispatch

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.luxury.chauffeur.driver.core.designsystem.*
import com.luxury.chauffeur.driver.core.network.DriverApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * IncomingRideModal
 *
 * Full-screen modal for incoming luxury chauffeur ride dispatch requests.
 * Features:
 * - 20-second countdown ring with real-time remaining seconds
 * - Audible chime and tactile haptic vibration while incoming
 * - Customer rating and executive tier badge
 * - Instant Accept & Reject controls
 * - Automated dismissal and non-blocking background dispatch rejection:
 *   POST /api/v1/drivers/dispatch/offers/{rideId}/reject with { "reason": "DISPATCH_TIMEOUT" }
 * - Graceful retry once on network error, falling back to server-side 22s safety expiration
 */
@Composable
fun IncomingRideModal(
    rideId: String,
    pickupAddress: String,
    destinationAddress: String,
    fare: String,
    distanceKm: String = "2.5 km",
    customerName: String = "VIP Sovereign",
    customerRating: Double = 4.95,
    totalSeconds: Int = 20,
    onAccept: () -> Unit,
    onReject: (reason: String) -> Unit,
    onDismissLocally: () -> Unit
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

    // 20-Second Countdown lifecycle: dismisses modal locally and fires non-blocking timeout reject
    LaunchedEffect(rideId) {
        while (secondsRemaining > 0) {
            delay(1000)
            secondsRemaining--
        }
        ringtone?.stop()
        // 1. Dismiss modal locally immediately
        onDismissLocally()

        // 2. Fire asynchronous non-blocking network request with retry-once resilience
        CoroutineScope(Dispatchers.IO).launch {
            try {
                DriverApiClient.getService().rejectDispatchOffer(
                    rideId = rideId,
                    payload = mapOf("reason" to "DISPATCH_TIMEOUT")
                )
            } catch (_: Exception) {
                try {
                    // Retry once on failure
                    DriverApiClient.getService().rejectDispatchOffer(
                        rideId = rideId,
                        payload = mapOf("reason" to "DISPATCH_TIMEOUT")
                    )
                } catch (_: Exception) {
                    // Backend server-side 22s safety fallback will forcefully expire and re-queue
                }
            }
        }
    }

    Dialog(
        onDismissRequest = { /* Offer must be explicitly accepted or rejected */ },
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
                // Header & Countdown
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = DriverColors.ChauffeurGold.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, DriverColors.ChauffeurGold)
                        ) {
                            Text(
                                text = "VIP DISPATCH OFFER",
                                style = DriverTypography.Subtitle.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DriverColors.ChauffeurGold
                                ),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        // Rating badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(DriverColors.SurfaceBorder)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("★", color = DriverColors.ChauffeurGold, fontSize = 12.sp)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "%.2f".format(customerRating),
                                style = DriverTypography.Subtitle.copy(
                                    color = DriverColors.TextPure,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Animated Countdown Ring
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(110.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = { secondsRemaining.toFloat() / totalSeconds.toFloat() },
                            modifier = Modifier.fillMaxSize(),
                            color = if (secondsRemaining <= 5) DriverColors.AlertDanger else DriverColors.ChauffeurGold,
                            trackColor = DriverColors.CockpitDark,
                            strokeWidth = 6.dp
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$secondsRemaining",
                                style = DriverTypography.MetricValue.copy(
                                    color = if (secondsRemaining <= 5) DriverColors.AlertDanger else DriverColors.TextPure,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 32.sp
                                )
                            )
                            Text(
                                text = "SEC",
                                style = DriverTypography.Body.copy(
                                    fontSize = 10.sp,
                                    color = DriverColors.TextMuted
                                )
                            )
                        }
                    }
                }

                // Ride & Passenger Details Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DriverColors.SurfaceCard),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, DriverColors.SurfaceBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "ESTIMATED EARNINGS",
                                    style = DriverTypography.Body.copy(
                                        color = DriverColors.TextMuted,
                                        fontSize = 11.sp
                                    )
                                )
                                Text(
                                    text = fare,
                                    style = DriverTypography.MetricValue.copy(
                                        color = DriverColors.ChauffeurGold,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 24.sp
                                    )
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "PICKUP DISTANCE",
                                    style = DriverTypography.Body.copy(
                                        color = DriverColors.TextMuted,
                                        fontSize = 11.sp
                                    )
                                )
                                Text(
                                    text = distanceKm,
                                    style = DriverTypography.Subtitle.copy(
                                        color = DriverColors.TextPure,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 16.sp
                                    )
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = DriverColors.SurfaceBorder
                        )

                        // Pickup Address
                        Row(verticalAlignment = Alignment.Top) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(DriverColors.ChauffeurGold)
                                    .align(Alignment.CenterVertically)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "PICKUP",
                                    style = DriverTypography.Body.copy(
                                        color = DriverColors.ChauffeurGold,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    text = pickupAddress,
                                    style = DriverTypography.Body.copy(
                                        color = DriverColors.TextPure,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // Destination Address
                        Row(verticalAlignment = Alignment.Top) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(DriverColors.ChauffeurGold)
                                    .align(Alignment.CenterVertically)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "DESTINATION",
                                    style = DriverTypography.Body.copy(
                                        color = DriverColors.ChauffeurGold,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    text = destinationAddress,
                                    style = DriverTypography.Body.copy(
                                        color = DriverColors.TextPure,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }
                    }
                }

                // Action Controls: Reject & Accept
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            isAccepting = true
                            ringtone?.stop()
                            onAccept()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        enabled = !isAccepting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DriverColors.ChauffeurGold,
                            contentColor = DriverColors.MidnightBlack
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (isAccepting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = DriverColors.MidnightBlack,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "ACCEPT CHAUFFEUR RIDE",
                                style = DriverTypography.Subtitle.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = DriverColors.MidnightBlack
                                )
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            ringtone?.stop()
                            onReject("UNAVAILABLE")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = DriverColors.TextMuted
                        ),
                        border = BorderStroke(1.dp, DriverColors.SurfaceBorder),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "DECLINE OFFER",
                            style = DriverTypography.Body.copy(
                                color = DriverColors.TextMuted,
                                fontSize = 13.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0D11)
@Composable
fun IncomingRideModalPreview() {
    IncomingRideModal(
        rideId = "RIDE-7788",
        pickupAddress = "Taj Mahal Palace, Colaba, Mumbai",
        destinationAddress = "Chhatrapati Shivaji Maharaj Terminus",
        fare = "₹1,450",
        distanceKm = "3.2 km",
        customerName = "Royal Sovereign",
        customerRating = 4.96,
        totalSeconds = 20,
        onAccept = {},
        onReject = {},
        onDismissLocally = {}
    )
}

