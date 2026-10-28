package com.luxury.chauffeur.driver.core.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────
// PrimaryButton — Gold accent call-to-action
// ─────────────────────────────────────────────

/**
 * Premium gold primary action button with optional loading state.
 *
 * @param text Button label text.
 * @param onClick Callback when pressed.
 * @param modifier Optional layout modifier.
 * @param isLoading If true, shows a spinner instead of text and disables interaction.
 * @param enabled Whether the button is interactive.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    enabled: Boolean = true
) {
    val haptic = LocalHapticFeedback.current
    Button(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = DriverColors.ChauffeurGold,
            contentColor = DriverColors.MidnightBlack,
            disabledContainerColor = DriverColors.ChauffeurGold.copy(alpha = 0.4f),
            disabledContentColor = DriverColors.MidnightBlack.copy(alpha = 0.5f)
        ),
        enabled = enabled && !isLoading,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = DriverColors.MidnightBlack,
                strokeWidth = 2.5.dp
            )
        } else {
            Text(
                text = text,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}

/**
 * Secondary outlined button for less prominent actions.
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val haptic = LocalHapticFeedback.current
    OutlinedButton(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.5.dp, DriverColors.ChauffeurGold),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = DriverColors.ChauffeurGold
        ),
        enabled = enabled
    ) {
        Text(text = text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

// ─────────────────────────────────────────────
// LuxCard — Premium card container
// ─────────────────────────────────────────────

/**
 * Premium card composable with soft gold border, dark surface, and rounded corners.
 *
 * @param modifier Optional layout modifier.
 * @param borderColor Border color; defaults to the standard surface border.
 * @param content Card content.
 */
@Composable
fun LuxCard(
    modifier: Modifier = Modifier,
    borderColor: Color = DriverColors.SurfaceBorder,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = DriverColors.SurfaceCard,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                ) else Modifier
            )
    ) {
        Box(modifier = Modifier.padding(LuxurySpacing.xl)) {
            content()
        }
    }
}

// ─────────────────────────────────────────────
// StatusChip — Compact status indicator
// ─────────────────────────────────────────────

/**
 * Compact rounded-pill status indicator with color-coded background.
 *
 * @param label Status text (e.g. "APPROVED", "PENDING", "REJECTED").
 * @param color Chip accent color; background is 20% opacity of this color.
 */
@Composable
fun StatusChip(
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.2f),
        modifier = modifier
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/**
 * Returns the appropriate [Color] for a KYC / document verification status string.
 */
fun statusColor(status: String): Color = when (status.uppercase()) {
    "APPROVED", "VERIFIED" -> DriverColors.StatusOnline
    "PENDING", "UNDER_REVIEW" -> DriverColors.StatusBusy
    "REJECTED", "FAILED" -> DriverColors.AlertDanger
    else -> DriverColors.StatusOffline
}

// ─────────────────────────────────────────────
// EmptyState — Illustration + message
// ─────────────────────────────────────────────

/**
 * Empty-state placeholder shown when a list has no items.
 *
 * @param icon Emoji or icon text to display.
 * @param title Primary message.
 * @param subtitle Secondary explanatory text.
 * @param action Optional CTA button.
 */
@Composable
fun EmptyState(
    icon: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = icon, fontSize = 48.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = DriverTypography.Title.copy(fontSize = 18.sp),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = DriverTypography.Body.copy(color = DriverColors.TextMuted, fontSize = 13.sp),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(20.dp))
            PrimaryButton(
                text = actionLabel,
                onClick = onAction,
                modifier = Modifier.padding(horizontal = 48.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────
// LoadingShimmer — Skeleton placeholder
// ─────────────────────────────────────────────

/**
 * Animated shimmer placeholder for loading states.
 * Shows a gradient sweep animation across a rounded rectangle.
 *
 * @param width Width of the shimmer block (Modifier.fillMaxWidth by default via modifier).
 * @param height Height of the shimmer block.
 */
@Composable
fun LoadingShimmer(
    modifier: Modifier = Modifier,
    height: Dp = 16.dp
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            DriverColors.SurfaceCard,
            DriverColors.SurfaceBorder.copy(alpha = 0.6f),
            DriverColors.SurfaceCard
        ),
        start = Offset(translateAnim - 300f, 0f),
        end = Offset(translateAnim, 0f)
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(8.dp))
            .background(shimmerBrush)
    )
}

/**
 * Shimmer loading card that mimics a content card while data loads.
 * Shows 3 lines of shimmer placeholders inside a card shape.
 */
@Composable
fun ShimmerCard(
    modifier: Modifier = Modifier
) {
    LuxCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LoadingShimmer(height = 14.dp, modifier = Modifier.fillMaxWidth(0.6f))
            LoadingShimmer(height = 10.dp, modifier = Modifier.fillMaxWidth(0.9f))
            LoadingShimmer(height = 10.dp, modifier = Modifier.fillMaxWidth(0.75f))
        }
    }
}

// ─────────────────────────────────────────────
// ErrorState — Network / API error display
// ─────────────────────────────────────────────

/**
 * Error state display with retry action.
 */
@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = DriverColors.AlertDanger.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, DriverColors.AlertDanger.copy(alpha = 0.4f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "⚠️",
                fontSize = 28.sp
            )
            Text(
                text = message,
                color = DriverColors.AlertDanger,
                style = DriverTypography.Body.copy(fontSize = 13.sp),
                textAlign = TextAlign.Center
            )
            if (onRetry != null) {
                SecondaryButton(
                    text = "RETRY",
                    onClick = onRetry,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
// CountdownRing — Circular countdown animation
// ─────────────────────────────────────────────

/**
 * Animated circular countdown ring with remaining seconds displayed in center.
 * Used on the incoming ride request card.
 *
 * @param remainingSeconds Current seconds remaining.
 * @param totalSeconds Total countdown duration for the sweep calculation.
 * @param size Diameter of the ring.
 */
@Composable
fun CountdownRing(
    remainingSeconds: Int,
    totalSeconds: Int,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    strokeWidth: Dp = 4.dp,
    ringColor: Color = DriverColors.ChauffeurGold,
    trackColor: Color = DriverColors.SurfaceBorder,
    urgentColor: Color = DriverColors.AlertDanger
) {
    val fraction = if (totalSeconds > 0) remainingSeconds.toFloat() / totalSeconds else 0f
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "countdownFraction"
    )
    val isUrgent = remainingSeconds <= 5
    val activeColor by animateColorAsState(
        targetValue = if (isUrgent) urgentColor else ringColor,
        animationSpec = tween(300),
        label = "countdownColor"
    )

    // Pulse animation when urgent
    val pulseAlpha = if (isUrgent) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0.4f,
            animationSpec = infiniteRepeatable(
                animation = tween(500),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseAlpha"
        )
        alpha
    } else {
        1f
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .alpha(pulseAlpha)
            .drawBehind {
                val sweepAngle = animatedFraction * 360f
                val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
                // Track
                drawArc(
                    color = trackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = stroke
                )
                // Active arc
                drawArc(
                    color = activeColor,
                    startAngle = -90f,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = stroke
                )
            }
    ) {
        Text(
            text = "${remainingSeconds}s",
            color = activeColor,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}

// ─────────────────────────────────────────────
// AnimatedOnlineToggle — Large premium toggle
// ─────────────────────────────────────────────

/**
 * Large, animated online/offline toggle with haptic feedback.
 * Displays a capsule-shaped track with a sliding thumb and label.
 *
 * @param isOnline Current online state.
 * @param onToggle Callback when toggle is tapped.
 */
@Composable
fun AnimatedOnlineToggle(
    isOnline: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val thumbOffset by animateFloatAsState(
        targetValue = if (isOnline) 1f else 0f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "toggleThumb"
    )
    val trackColor by animateColorAsState(
        targetValue = if (isOnline) DriverColors.StatusOnline else DriverColors.SurfaceBorder,
        animationSpec = tween(350),
        label = "toggleTrack"
    )
    val thumbColor by animateColorAsState(
        targetValue = if (isOnline) Color.White else DriverColors.TextMuted,
        animationSpec = tween(350),
        label = "toggleThumbColor"
    )

    val trackWidth = 160.dp
    val trackHeight = 48.dp
    val thumbDiameter = 40.dp

    Surface(
        shape = RoundedCornerShape(trackHeight / 2),
        color = trackColor.copy(alpha = 0.25f),
        border = BorderStroke(2.dp, trackColor),
        modifier = modifier
            .width(trackWidth)
            .height(trackHeight)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggle(!isOnline)
            }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.CenterStart
        ) {
            // Label text
            Text(
                text = if (isOnline) "ONLINE" else "OFFLINE",
                color = if (isOnline) DriverColors.StatusOnline else DriverColors.TextMuted,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                modifier = Modifier
                    .align(if (isOnline) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 16.dp)
            )

            // Sliding thumb
            val thumbPadding = 4.dp
            val maxTravel = trackWidth - thumbDiameter - (thumbPadding * 2)
            Box(
                modifier = Modifier
                    .padding(start = thumbPadding + (maxTravel * thumbOffset))
                    .size(thumbDiameter)
                    .clip(CircleShape)
                    .background(thumbColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isOnline) "●" else "○",
                    color = if (isOnline) DriverColors.StatusOnline else DriverColors.SurfaceBorder,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
// Previews
// ─────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF090A0F)
@Composable
private fun PrimaryButtonPreview() {
    LuxuryDriverTheme(darkTheme = true) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton(text = "ACCEPT TRIP", onClick = {})
            PrimaryButton(text = "LOADING...", onClick = {}, isLoading = true)
            SecondaryButton(text = "DECLINE", onClick = {})
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF090A0F)
@Composable
private fun StatusChipPreview() {
    LuxuryDriverTheme(darkTheme = true) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip(label = "APPROVED", color = DriverColors.StatusOnline)
            StatusChip(label = "PENDING", color = DriverColors.StatusBusy)
            StatusChip(label = "REJECTED", color = DriverColors.AlertDanger)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF090A0F)
@Composable
private fun LuxCardPreview() {
    LuxuryDriverTheme(darkTheme = true) {
        LuxCard(modifier = Modifier.padding(16.dp)) {
            Column {
                Text("TODAY'S EARNINGS", style = DriverTypography.Body.copy(color = DriverColors.TextMuted, fontSize = 11.sp))
                Spacer(modifier = Modifier.height(4.dp))
                Text("₹2,450", style = DriverTypography.MetricValue)
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF090A0F)
@Composable
private fun EmptyStatePreview() {
    LuxuryDriverTheme(darkTheme = true) {
        EmptyState(
            icon = "🛺",
            title = "No Rides Yet",
            subtitle = "Go online to start receiving ride requests in your area.",
            actionLabel = "GO ONLINE",
            onAction = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF090A0F)
@Composable
private fun LoadingShimmerPreview() {
    LuxuryDriverTheme(darkTheme = true) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ShimmerCard()
            ShimmerCard()
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF090A0F)
@Composable
private fun ErrorStatePreview() {
    LuxuryDriverTheme(darkTheme = true) {
        ErrorState(
            message = "Unable to load earnings. Please check your connection and try again.",
            modifier = Modifier.padding(16.dp),
            onRetry = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF090A0F)
@Composable
private fun CountdownRingPreview() {
    LuxuryDriverTheme(darkTheme = true) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CountdownRing(remainingSeconds = 12, totalSeconds = 15)
            CountdownRing(remainingSeconds = 4, totalSeconds = 15)
            CountdownRing(remainingSeconds = 1, totalSeconds = 15)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF090A0F)
@Composable
private fun AnimatedOnlineTogglePreview() {
    LuxuryDriverTheme(darkTheme = true) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AnimatedOnlineToggle(isOnline = true, onToggle = {})
            AnimatedOnlineToggle(isOnline = false, onToggle = {})
        }
    }
}
