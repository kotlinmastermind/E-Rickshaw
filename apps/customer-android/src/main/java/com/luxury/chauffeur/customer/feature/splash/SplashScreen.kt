package com.luxury.chauffeur.customer.feature.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luxury.chauffeur.customer.core.designsystem.LuxuryColors
import com.luxury.chauffeur.customer.core.designsystem.LuxuryTypography
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onNavigateNext: (isLoggedIn: Boolean) -> Unit
) {
    var startAnimation by remember { mutableStateOf(false) }
    val alphaAnim = animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "alpha"
    )
    val scaleAnim = animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.85f,
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "scale"
    )

    LaunchedEffect(Unit) {
        startAnimation = true
        delay(2200)
        // Transition to OTP or Home based on session
        onNavigateNext(false)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LuxuryColors.ObsidianBlack),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .alpha(alphaAnim.value)
                .scale(scaleAnim.value)
        ) {
            Text(
                text = "GRAND CHAUFFEUR",
                style = LuxuryTypography.HeadlineLarge.copy(
                    letterSpacing = 4.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = LuxuryColors.ChampagneGold
                )
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "UNCOMPROMISING LUXURY TRAVEL",
                style = LuxuryTypography.BodySubtle.copy(
                    letterSpacing = 3.sp,
                    fontSize = 11.sp,
                    color = LuxuryColors.PlatinumWhite.copy(alpha = 0.7f)
                )
            )
        }
    }
}
