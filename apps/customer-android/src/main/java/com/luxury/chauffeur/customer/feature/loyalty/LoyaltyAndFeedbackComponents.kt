package com.luxury.chauffeur.customer.feature.loyalty

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.luxury.chauffeur.customer.core.designsystem.*
import com.luxury.chauffeur.customer.core.network.*
import kotlinx.coroutines.launch

// =========================================================================
// Feature 151-157, 174: Post-Trip Rating, Multi-Category Scores, Private Feedback,
// Feedback Tags, Tipping & 2-Hour Adjustment Window, Customer CSAT/NPS Survey
// =========================================================================

@Composable
fun PostTripRatingAndTippingDialog(
    rideId: String,
    onDismiss: () -> Unit,
    onCompleted: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Rating Category Scores (Features 151, 153, 154)
    var overallRating by remember { mutableDoubleStateOf(5.0) }
    var cleanlinessRating by remember { mutableDoubleStateOf(5.0) }
    var comfortRating by remember { mutableDoubleStateOf(5.0) }
    var serviceQualityRating by remember { mutableDoubleStateOf(5.0) }
    var navigationRating by remember { mutableDoubleStateOf(5.0) }

    // Feedback Tags & Comments (Features 152, 155)
    val availableTags = listOf(
        "Smooth Ride", "Punctual Chauffeur", "Spotless Vehicle",
        "Discreet & Courteous", "Silky Suspension", "Pleasant Aroma",
        "Expert Route", "Refreshing Climate", "Executive Demeanor"
    )
    val selectedTags = remember { mutableStateListOf<String>() }
    var publicComment by remember { mutableStateOf("") }
    var privateFeedback by remember { mutableStateOf("") }

    // Chauffeur Tipping (Features 156, 157)
    val tipPresets = listOf(0, 100, 200, 500, 1000)
    var selectedTipRupees by remember { mutableIntStateOf(200) }
    var customTipInput by remember { mutableStateOf("") }
    var isCustomTip by remember { mutableStateOf(false) }
    var tipSubmittedStatus by remember { mutableStateOf<String?>(null) }
    var tipAdjustmentWindowOpen by remember { mutableStateOf(false) }

    // CSAT & NPS Satisfaction Survey (Feature 174)
    var showSurveySection by remember { mutableStateOf(false) }
    var csatScore by remember { mutableIntStateOf(5) }
    var npsScore by remember { mutableIntStateOf(10) }
    var surveyFeedback by remember { mutableStateOf("") }
    var surveySubmitted by remember { mutableStateOf(false) }

    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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
                            text = "CHAUFFEUR SERVICE REVIEW",
                            style = LuxuryTypography.HeadlineMedium.copy(
                                color = LuxuryColors.ChampagneGold,
                                fontSize = 18.sp
                            )
                        )
                        Text(
                            text = "Ride #${rideId.take(12)}",
                            style = LuxuryTypography.BodySubtle
                        )
                    }
                    GoldBadge("EXECUTIVE")
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (errorMessage != null) {
                    Surface(
                        color = LuxuryColors.CrimsonAlert.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                        border = BorderStroke(1.dp, LuxuryColors.CrimsonAlert),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Text(
                            text = errorMessage!!,
                            color = LuxuryColors.CrimsonAlert,
                            style = LuxuryTypography.BodySubtle,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                // 1. Overall Chauffeur Experience (Feature 151)
                LuxuryCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "OVERALL CHAUFFEUR RATING",
                        style = LuxuryTypography.TitleGold.copy(fontSize = 13.sp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    RatingStarRow(
                        rating = overallRating.toInt(),
                        onRatingSelected = { overallRating = it.toDouble() }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 2. Specific Category Scores (Features 153, 154)
                LuxuryCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "DETAILED SERVICE SCORES",
                        style = LuxuryTypography.TitleGold.copy(fontSize = 13.sp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    CategoryScoreItem(
                        title = "Vehicle Cleanliness & Presentation",
                        subtitle = "Interior fragrance, seating condition & sanitation",
                        score = cleanlinessRating.toInt(),
                        onScoreChanged = { cleanlinessRating = it.toDouble() }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    CategoryScoreItem(
                        title = "Driving Comfort & Smoothness",
                        subtitle = "Braking, acceleration & cornering finesse",
                        score = comfortRating.toInt(),
                        onScoreChanged = { comfortRating = it.toDouble() }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    CategoryScoreItem(
                        title = "Chauffeur Service Etiquette",
                        subtitle = "Door assistance, discretion & courtesy",
                        score = serviceQualityRating.toInt(),
                        onScoreChanged = { serviceQualityRating = it.toDouble() }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    CategoryScoreItem(
                        title = "Route Navigation & Efficiency",
                        subtitle = "Traffic circumvention and smooth route execution",
                        score = navigationRating.toInt(),
                        onScoreChanged = { navigationRating = it.toDouble() }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 3. Feedback Tags (Feature 155)
                Text(
                    text = "SELECT COMPLIMENTS & TAGS",
                    style = LuxuryTypography.TitleGold.copy(fontSize = 13.sp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                availableTags.chunked(3).forEach { rowTags ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rowTags.forEach { tag ->
                            val isSelected = selectedTags.contains(tag)
                            Surface(
                                shape = RoundedCornerShape(LuxuryDimensions.RadiusPill),
                                color = if (isSelected) LuxuryColors.ChampagneGold.copy(alpha = 0.25f) else LuxuryColors.CardSurface,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) LuxuryColors.ChampagneGold else LuxuryColors.CardBorder
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        if (isSelected) selectedTags.remove(tag) else selectedTags.add(tag)
                                    }
                            ) {
                                Text(
                                    text = (if (isSelected) "✓ " else "+ ") + tag,
                                    style = LuxuryTypography.BodySubtle.copy(
                                        color = if (isSelected) LuxuryColors.ChampagneGold else LuxuryColors.PlatinumWhite,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        fontSize = 10.sp
                                    ),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Public Comment
                OutlinedTextField(
                    value = publicComment,
                    onValueChange = { publicComment = it },
                    label = { Text("Public Review Note", color = LuxuryColors.SubtleGray) },
                    placeholder = { Text("Share your impressions of this journey...", color = LuxuryColors.SubtleGray) },
                    textStyle = LuxuryTypography.BodyRegular,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LuxuryColors.ChampagneGold,
                        unfocusedBorderColor = LuxuryColors.CardBorder,
                        focusedTextColor = LuxuryColors.PlatinumWhite,
                        unfocusedTextColor = LuxuryColors.PlatinumWhite
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Private Service Feedback (Feature 152)
                OutlinedTextField(
                    value = privateFeedback,
                    onValueChange = { privateFeedback = it },
                    label = { Text("Private Feedback (Concierge Fleet Ops Only)", color = LuxuryColors.SubtleGray) },
                    placeholder = { Text("Discreet notes sent strictly to management, hidden from chauffeur...", color = LuxuryColors.SubtleGray) },
                    textStyle = LuxuryTypography.BodyRegular,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LuxuryColors.GoldMuted,
                        unfocusedBorderColor = LuxuryColors.CardBorder,
                        focusedTextColor = LuxuryColors.PlatinumWhite,
                        unfocusedTextColor = LuxuryColors.PlatinumWhite
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 4. Chauffeur Tip (Features 156, 157)
                LuxuryCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "CHAUFFEUR GRATUITY (TIP)",
                                style = LuxuryTypography.TitleGold.copy(fontSize = 13.sp)
                            )
                            Text(
                                text = "100% of tips go directly to your chauffeur",
                                style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                            color = LuxuryColors.ChampagneGold.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, LuxuryColors.ChampagneGold)
                        ) {
                            Text(
                                text = "2h Edit Window",
                                style = LuxuryTypography.TitleGold.copy(fontSize = 10.sp),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        tipPresets.forEach { amount ->
                            val isSelected = !isCustomTip && selectedTipRupees == amount
                            Surface(
                                shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                                color = if (isSelected) LuxuryColors.ChampagneGold else LuxuryColors.CardSurface,
                                border = BorderStroke(1.dp, if (isSelected) LuxuryColors.ChampagneGold else LuxuryColors.CardBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        isCustomTip = false
                                        selectedTipRupees = amount
                                    }
                            ) {
                                Text(
                                    text = if (amount == 0) "No Tip" else "₹$amount",
                                    color = if (isSelected) LuxuryColors.ObsidianBlack else LuxuryColors.PlatinumWhite,
                                    style = LuxuryTypography.TitleGold.copy(
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = customTipInput,
                            onValueChange = {
                                customTipInput = it
                                val parsed = it.toIntOrNull()
                                if (parsed != null && parsed > 0) {
                                    isCustomTip = true
                                    selectedTipRupees = parsed
                                }
                            },
                            placeholder = { Text("Custom Amount (₹)", color = LuxuryColors.SubtleGray) },
                            textStyle = LuxuryTypography.BodyRegular,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = LuxuryColors.ChampagneGold,
                                unfocusedBorderColor = LuxuryColors.CardBorder,
                                focusedTextColor = LuxuryColors.PlatinumWhite,
                                unfocusedTextColor = LuxuryColors.PlatinumWhite
                            ),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    if (tipSubmittedStatus != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = tipSubmittedStatus!!,
                            color = LuxuryColors.LuxuryEmerald,
                            style = LuxuryTypography.BodySubtle.copy(fontSize = 12.sp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 5. Customer Satisfaction Survey (Feature 174)
                Surface(
                    shape = RoundedCornerShape(LuxuryDimensions.RadiusMedium),
                    color = LuxuryColors.CardSurface,
                    border = BorderStroke(1.dp, LuxuryColors.CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showSurveySection = !showSurveySection },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "30-SECOND SATISFACTION SURVEY",
                                    style = LuxuryTypography.TitleGold.copy(fontSize = 12.sp)
                                )
                                Text(
                                    text = "Help Grand Chauffeur elevate service quality",
                                    style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp)
                                )
                            }
                            Text(
                                text = if (showSurveySection) "▲" else "▼",
                                color = LuxuryColors.ChampagneGold
                            )
                        }

                        if (showSurveySection) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "How satisfied are you with Grand Chauffeur overall? (CSAT: $csatScore/5)",
                                style = LuxuryTypography.BodySubtle
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            RatingStarRow(rating = csatScore, onRatingSelected = { csatScore = it })

                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "How likely are you to recommend us to peers? (NPS: $npsScore/10)",
                                style = LuxuryTypography.BodySubtle
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                (1..10).forEach { scoreVal ->
                                    val isSelected = npsScore == scoreVal
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) LuxuryColors.ChampagneGold else LuxuryColors.ObsidianBlack
                                            )
                                            .clickable { npsScore = scoreVal },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = scoreVal.toString(),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) LuxuryColors.ObsidianBlack else LuxuryColors.PlatinumWhite
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = surveyFeedback,
                                onValueChange = { surveyFeedback = it },
                                placeholder = { Text("What did you enjoy most about today's journey?", color = LuxuryColors.SubtleGray) },
                                textStyle = LuxuryTypography.BodyRegular,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = LuxuryColors.ChampagneGold,
                                    unfocusedBorderColor = LuxuryColors.CardBorder,
                                    focusedTextColor = LuxuryColors.PlatinumWhite,
                                    unfocusedTextColor = LuxuryColors.PlatinumWhite
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 2
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Submit Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LuxuryButton(
                        text = "Skip",
                        onClick = onDismiss,
                        isSecondary = true,
                        modifier = Modifier.weight(1f)
                    )

                    LuxuryButton(
                        text = if (isSubmitting) "Submitting..." else "Submit Review",
                        onClick = {
                            isSubmitting = true
                            errorMessage = null
                            scope.launch {
                                try {
                                    // 1. Submit rating
                                    val ratingPayload = SubmitDriverRatingRequest(
                                        overallScore = overallRating,
                                        cleanlinessScore = cleanlinessRating,
                                        comfortScore = comfortRating,
                                        serviceQualityScore = serviceQualityRating,
                                        navigationScore = navigationRating,
                                        feedbackTags = selectedTags.toList(),
                                        privateFeedback = privateFeedback.ifBlank { null },
                                        publicComment = publicComment.ifBlank { null }
                                    )
                                    ApiClient.getService().submitComprehensiveDriverRating(rideId, ratingPayload)

                                    // 2. Submit tip if chosen
                                    if (selectedTipRupees > 0) {
                                        ApiClient.getService().submitRideTip(
                                            rideId,
                                            SubmitTipRequest(tipCents = selectedTipRupees * 100)
                                        )
                                    }

                                    // 3. Submit survey if opened
                                    if (showSurveySection) {
                                        ApiClient.getService().submitCustomerSurvey(
                                            CustomerSurveyRequest(
                                                rideId = rideId,
                                                csatScore = csatScore,
                                                npsScore = npsScore,
                                                feedbackText = surveyFeedback.ifBlank { null }
                                            )
                                        )
                                    }

                                    Toast.makeText(context, "Review & Gratuity submitted successfully", Toast.LENGTH_SHORT).show()
                                    onCompleted()
                                } catch (e: Exception) {
                                    errorMessage = "Error submitting feedback: ${e.localizedMessage}"
                                } finally {
                                    isSubmitting = false
                                }
                            }
                        },
                        isLoading = isSubmitting,
                        modifier = Modifier.weight(2f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryScoreItem(
    title: String,
    subtitle: String,
    score: Int,
    onScoreChanged: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = LuxuryTypography.BodyRegular.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium))
            Text(text = subtitle, style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp))
        }
        RatingStarRow(rating = score, onRatingSelected = onScoreChanged, starSize = 18.dp)
    }
}

@Composable
private fun RatingStarRow(
    rating: Int,
    onRatingSelected: (Int) -> Unit,
    starSize: androidx.compose.ui.unit.Dp = 26.dp
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        (1..5).forEach { star ->
            val isFilled = star <= rating
            Text(
                text = if (isFilled) "★" else "☆",
                fontSize = starSize.value.sp,
                color = if (isFilled) LuxuryColors.ChampagneGold else LuxuryColors.SubtleGray,
                modifier = Modifier.clickable { onRatingSelected(star) }
            )
        }
    }
}

// =========================================================================
// Features 158-162, 166, 167: Royal Loyalty Hub, Tier Benefits, Points Ledger,
// Points Redemption, Milestones & Badges, Ride Streak Tracker & Renewal Reminders
// =========================================================================

@Composable
fun RoyalLoyaltyHubDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeTab by remember { mutableStateOf<String>("OVERVIEW") } // OVERVIEW, LEDGER, BADGES
    var loyaltySummary by remember { mutableStateOf<LoyaltySummaryResponse?>(null) }
    var ledgerList by remember { mutableStateOf<List<LoyaltyLedgerItemDto>>(emptyList()) }
    var milestoneBadges by remember { mutableStateOf<List<MilestoneBadgeDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRedeeming by remember { mutableStateOf(false) }
    var actionMessage by remember { mutableStateOf<String?>(null) }

    val fetchLoyaltyData = {
        scope.launch {
            isLoading = true
            try {
                val sumRes = ApiClient.getService().getLoyaltySummary()
                if (sumRes.isSuccessful) loyaltySummary = sumRes.body()

                val ledRes = ApiClient.getService().getLoyaltyLedger()
                if (ledRes.isSuccessful) ledgerList = ledRes.body() ?: emptyList()

                val badRes = ApiClient.getService().getCustomerMilestones()
                if (badRes.isSuccessful) milestoneBadges = badRes.body() ?: emptyList()
            } catch (_: Exception) {} finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        fetchLoyaltyData()
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
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ROYAL LOYALTY SANCTUARY",
                            style = LuxuryTypography.HeadlineMedium.copy(
                                color = LuxuryColors.ChampagneGold,
                                fontSize = 18.sp
                            )
                        )
                        Text(
                            text = "Tiered privileges & sovereign ledger",
                            style = LuxuryTypography.BodySubtle
                        )
                    }
                    Text(
                        text = "✕",
                        style = LuxuryTypography.TitleGold.copy(fontSize = 18.sp),
                        modifier = Modifier.clickable { onDismiss() }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Navigation Tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("OVERVIEW", "LEDGER", "BADGES").forEach { tab ->
                        val isSelected = activeTab == tab
                        Surface(
                            shape = RoundedCornerShape(LuxuryDimensions.RadiusPill),
                            color = if (isSelected) LuxuryColors.ChampagneGold else LuxuryColors.CardSurface,
                            border = BorderStroke(1.dp, if (isSelected) LuxuryColors.ChampagneGold else LuxuryColors.CardBorder),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { activeTab = tab }
                        ) {
                            Text(
                                text = tab,
                                color = if (isSelected) LuxuryColors.ObsidianBlack else LuxuryColors.PlatinumWhite,
                                style = LuxuryTypography.TitleGold.copy(
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                modifier = Modifier.padding(vertical = 8.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = LuxuryColors.ChampagneGold)
                    }
                } else {
                    if (actionMessage != null) {
                        Surface(
                            color = LuxuryColors.LuxuryEmerald.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                            border = BorderStroke(1.dp, LuxuryColors.LuxuryEmerald),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                        ) {
                            Text(
                                text = actionMessage!!,
                                color = LuxuryColors.LuxuryEmerald,
                                style = LuxuryTypography.BodySubtle,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    when (activeTab) {
                        "OVERVIEW" -> {
                            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                // Tier & Balance Card (Features 158, 160)
                                val tier = loyaltySummary?.membershipTier ?: "SILVER"
                                val tierColor = when (tier) {
                                    "BLACK_SOVEREIGN" -> Color(0xFFC084FC)
                                    "PLATINUM" -> Color(0xFFE2E8F0)
                                    "GOLD" -> LuxuryColors.ChampagneGold
                                    else -> Color(0xFF94A3B8)
                                }

                                Surface(
                                    shape = RoundedCornerShape(LuxuryDimensions.RadiusMedium),
                                    color = LuxuryColors.CardSurface,
                                    border = BorderStroke(1.5.dp, tierColor),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = "$tier SOVEREIGN",
                                                    style = LuxuryTypography.TitleGold.copy(
                                                        color = tierColor,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 16.sp
                                                    )
                                                )
                                                Text(
                                                    text = "Grand Chauffeur Elite Status",
                                                    style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp)
                                                )
                                            }
                                            GoldBadge("ACTIVE")
                                        }

                                        Spacer(modifier = Modifier.height(16.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Text("AVAILABLE POINTS", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))
                                                Text(
                                                    text = "${loyaltySummary?.pointsBalance ?: 0} pts",
                                                    style = LuxuryTypography.HeadlineLarge.copy(color = LuxuryColors.ChampagneGold)
                                                )
                                                Text(
                                                    text = "Value: ₹${loyaltySummary?.pointsBalance ?: 0}",
                                                    style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp)
                                                )
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text("LIFETIME EARNED", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))
                                                Text(
                                                    text = "${loyaltySummary?.lifetimePoints ?: 0} pts",
                                                    style = LuxuryTypography.HeadlineMedium.copy(color = LuxuryColors.PlatinumWhite)
                                                )
                                            }
                                        }

                                        // Renewal Reminder (Feature 161)
                                        if (loyaltySummary?.renewalReminder != null) {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Surface(
                                                color = LuxuryColors.ChampagneGold.copy(alpha = 0.12f),
                                                shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                                                border = BorderStroke(1.dp, LuxuryColors.ChampagneGold)
                                            ) {
                                                Text(
                                                    text = loyaltySummary!!.renewalReminder!!.message,
                                                    style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.ChampagneGold),
                                                    modifier = Modifier.padding(8.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Ride Streak Rewards (Feature 167)
                                val streak = loyaltySummary?.rideStreakCount ?: 0
                                LuxuryCard(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("ROYAL RIDE STREAK", style = LuxuryTypography.TitleGold.copy(fontSize = 13.sp))
                                            Text(
                                                text = "Complete 3 rides within 7 days for a 200 pts bonus",
                                                style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp)
                                            )
                                        }
                                        GoldBadge("$streak/3 RIDES")
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))
                                    LinearProgressIndicator(
                                        progress = { (streak % 3) / 3f },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(LuxuryDimensions.RadiusPill)),
                                        color = LuxuryColors.ChampagneGold,
                                        trackColor = LuxuryColors.DeepGraphite
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Redeem Points for Ride Credit (Feature 162)
                                LuxuryCard(modifier = Modifier.fillMaxWidth()) {
                                    Text("REDEEM POINTS TO WALLET CREDIT", style = LuxuryTypography.TitleGold.copy(fontSize = 13.sp))
                                    Text(
                                        text = "1 Point = ₹1.00 instant executive wallet balance",
                                        style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp)
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf(200, 500, 1000).forEach { pts ->
                                            val canRedeem = (loyaltySummary?.pointsBalance ?: 0) >= pts
                                            Button(
                                                onClick = {
                                                    isRedeeming = true
                                                    scope.launch {
                                                        try {
                                                            val res = ApiClient.getService().redeemLoyaltyPoints(
                                                                RedeemLoyaltyPointsRequest(pointsToRedeem = pts)
                                                            )
                                                            if (res.isSuccessful) {
                                                                actionMessage = "Successfully redeemed $pts points for ₹$pts wallet credit!"
                                                                fetchLoyaltyData()
                                                            } else {
                                                                actionMessage = "Unable to redeem points at this time"
                                                            }
                                                        } catch (e: Exception) {
                                                            actionMessage = "Error: ${e.localizedMessage}"
                                                        } finally {
                                                            isRedeeming = false
                                                        }
                                                    }
                                                },
                                                enabled = canRedeem && !isRedeeming,
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = LuxuryColors.ChampagneGold,
                                                    contentColor = LuxuryColors.ObsidianBlack,
                                                    disabledContainerColor = LuxuryColors.CardSurface,
                                                    disabledContentColor = LuxuryColors.SubtleGray
                                                ),
                                                shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(
                                                    text = "₹$pts\n($pts pts)",
                                                    fontSize = 11.sp,
                                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Tier Benefits List (Feature 160)
                                Text("YOUR TIER PRIVILEGES", style = LuxuryTypography.TitleGold.copy(fontSize = 13.sp))
                                Spacer(modifier = Modifier.height(8.dp))

                                (loyaltySummary?.tierBenefits ?: listOf(
                                    "Priority chauffeur allocation during peak hours",
                                    "Complimentary waiting window of 15 minutes",
                                    "Access to Maybach & Phantom suite reserves",
                                    "Dedicated fleet concierge hotline"
                                )).forEach { benefit ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("◆ ", color = LuxuryColors.ChampagneGold, fontSize = 12.sp)
                                        Text(benefit, style = LuxuryTypography.BodyRegular.copy(fontSize = 13.sp))
                                    }
                                }
                            }
                        }

                        "LEDGER" -> {
                            // Loyalty Points Ledger (Features 158, 159)
                            if (ledgerList.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No points transactions recorded yet.", style = LuxuryTypography.BodySubtle)
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(ledgerList) { entry ->
                                        LuxuryCard(modifier = Modifier.fillMaxWidth()) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = entry.description,
                                                        style = LuxuryTypography.BodyRegular.copy(fontWeight = FontWeight.Medium)
                                                    )
                                                    Text(
                                                        text = "${entry.transactionType} • ${entry.createdAt.take(10)}",
                                                        style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp)
                                                    )
                                                }
                                                Column(horizontalAlignment = Alignment.End) {
                                                    val isPositive = entry.pointsChange >= 0
                                                    Text(
                                                        text = if (isPositive) "+${entry.pointsChange} pts" else "${entry.pointsChange} pts",
                                                        color = if (isPositive) LuxuryColors.LuxuryEmerald else LuxuryColors.CrimsonAlert,
                                                        style = LuxuryTypography.TitleGold.copy(fontWeight = FontWeight.Bold)
                                                    )
                                                    Text(
                                                        text = "Bal: ${entry.balanceAfter}",
                                                        style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        "BADGES" -> {
                            // Customer Milestones & Badges (Feature 166)
                            if (milestoneBadges.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Complete rides to earn distinguished milestones.", style = LuxuryTypography.BodySubtle)
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(milestoneBadges) { badge ->
                                        LuxuryCard(modifier = Modifier.fillMaxWidth()) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(46.dp)
                                                        .clip(CircleShape)
                                                        .background(LuxuryColors.ChampagneGold.copy(alpha = 0.2f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("👑", fontSize = 20.sp)
                                                }
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            text = badge.badgeName,
                                                            style = LuxuryTypography.TitleGold.copy(fontWeight = FontWeight.Bold)
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        GoldBadge(badge.badgeTier)
                                                    }
                                                    Text(
                                                        text = badge.description,
                                                        style = LuxuryTypography.BodySubtle.copy(fontSize = 12.sp)
                                                    )
                                                    Text(
                                                        text = "Unlocked: ${badge.unlockedAt.take(10)}",
                                                        style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp, color = LuxuryColors.GoldMuted)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// Features 163, 164: Referral Sanctuary, Progress Dashboard, Reward Eligibility
// =========================================================================

@Composable
fun ReferralSanctuaryDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var dashboard by remember { mutableStateOf<ReferralDashboardResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showEligibilityModal by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val res = ApiClient.getService().getReferralDashboard()
            if (res.isSuccessful) dashboard = res.body()
        } catch (_: Exception) {} finally {
            isLoading = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f)
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
                            text = "REFERRAL SANCTUARY",
                            style = LuxuryTypography.HeadlineMedium.copy(
                                color = LuxuryColors.ChampagneGold,
                                fontSize = 18.sp
                            )
                        )
                        Text(
                            text = "Gift prestige, earn sovereign credits",
                            style = LuxuryTypography.BodySubtle
                        )
                    }
                    Text(
                        text = "✕",
                        style = LuxuryTypography.TitleGold.copy(fontSize = 18.sp),
                        modifier = Modifier.clickable { onDismiss() }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = LuxuryColors.ChampagneGold)
                    }
                } else {
                    val code = dashboard?.referralCode ?: "CHAUFFEUR-VIP"

                    // Referral Code Box
                    Surface(
                        shape = RoundedCornerShape(LuxuryDimensions.RadiusMedium),
                        color = LuxuryColors.CardSurface,
                        border = BorderStroke(1.5.dp, LuxuryColors.ChampagneGold),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("YOUR EXCLUSIVE INVITATION CODE", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = code,
                                style = LuxuryTypography.HeadlineLarge.copy(
                                    color = LuxuryColors.ChampagneGold,
                                    letterSpacing = 4.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Referral Code", code))
                                        Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = LuxuryColors.CardSurface,
                                        contentColor = LuxuryColors.ChampagneGold
                                    ),
                                    border = BorderStroke(1.dp, LuxuryColors.ChampagneGold),
                                    shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Copy Code", fontSize = 12.sp)
                                }

                                Button(
                                    onClick = {
                                        val shareText = "Experience the pinnacle of luxury travel with Grand Chauffeur. Use my invitation code $code for ₹200 on your inaugural journey: https://luxurychauffeur.com/invite/$code"
                                        val sendIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_TEXT, shareText)
                                            type = "text/plain"
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, "Invite distinguished guest"))
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = LuxuryColors.ChampagneGold,
                                        contentColor = LuxuryColors.ObsidianBlack
                                    ),
                                    shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Share Invite", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Dashboard KPI Metrics (Feature 163)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricCard(
                            title = "GUESTS INVITED",
                            value = "${dashboard?.totalReferrals ?: 0}",
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            title = "COMPLETED",
                            value = "${dashboard?.successfulReferrals ?: 0}",
                            modifier = Modifier.weight(1f)
                        )
                        val earnedRupees = (dashboard?.totalBonusEarnedCents ?: 0) / 100.0
                        MetricCard(
                            title = "BONUS EARNED",
                            value = "₹${earnedRupees.toInt()}",
                            modifier = Modifier.weight(1.2f)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Reward Eligibility Details (Feature 164)
                    LuxuryCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { showEligibilityModal = !showEligibilityModal }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("REWARD ELIGIBILITY & TERMS", style = LuxuryTypography.TitleGold.copy(fontSize = 13.sp))
                                Text(
                                    text = "₹500 bonus for each completed referral journey",
                                    style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp)
                                )
                            }
                            Text(if (showEligibilityModal) "▲" else "▼", color = LuxuryColors.ChampagneGold)
                        }

                        if (showEligibilityModal) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = dashboard?.eligibilityTerms ?: "• Invitee must register a verified phone number.\n• Invitee receives ₹200 wallet credit immediately.\n• Referrer receives ₹500 credit upon completion of invitee's first trip.\n• Fraud prevention safeguards apply.",
                                style = LuxuryTypography.BodySubtle.copy(lineHeight = 18.sp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Recent Referrals List
                    Text("RECENT GUEST INVITATIONS", style = LuxuryTypography.TitleGold.copy(fontSize = 13.sp))
                    Spacer(modifier = Modifier.height(8.dp))

                    val recents = dashboard?.recentReferrals ?: emptyList()
                    if (recents.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                            color = LuxuryColors.CardSurface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "No invitations yet. Share your code to start earning credits.",
                                style = LuxuryTypography.BodySubtle,
                                modifier = Modifier.padding(16.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        recents.forEach { item ->
                            LuxuryCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(text = item.referredName, style = LuxuryTypography.BodyRegular.copy(fontWeight = FontWeight.Medium))
                                        Text(text = "Invited ${item.createdAt.take(10)}", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        GoldBadge(item.rewardStatus)
                                        if (item.rewardAmountRupees > 0) {
                                            Text(
                                                text = "+₹${item.rewardAmountRupees.toInt()}",
                                                color = LuxuryColors.LuxuryEmerald,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
        color = LuxuryColors.CardSurface,
        border = BorderStroke(1.dp, LuxuryColors.CardBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, style = LuxuryTypography.BodySubtle.copy(fontSize = 9.sp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, style = LuxuryTypography.TitleGold.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp))
        }
    }
}

// =========================================================================
// Features 168-170: Exclusive Offers Feed, Birthday / Anniversary Celebrations,
// Notification Frequency Controls
// =========================================================================

@Composable
fun ExclusiveOffersFeedDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var offersData by remember { mutableStateOf<OffersResponse?>(null) }
    var birthdayInput by remember { mutableStateOf("") }
    var anniversaryInput by remember { mutableStateOf("") }
    var selectedFrequency by remember { mutableStateOf("WEEKLY") }
    var isLoading by remember { mutableStateOf(true) }
    var isSavingPreferences by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val res = ApiClient.getService().getPersonalizedOffers()
            if (res.isSuccessful && res.body() != null) {
                val data = res.body()!!
                offersData = data
                birthdayInput = data.birthdayDate ?: ""
                anniversaryInput = data.anniversaryDate ?: ""
                selectedFrequency = data.notificationFrequency
            }
        } catch (_: Exception) {} finally {
            isLoading = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f)
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
                            text = "EXCLUSIVE OFFERS & PERKS",
                            style = LuxuryTypography.HeadlineMedium.copy(
                                color = LuxuryColors.ChampagneGold,
                                fontSize = 18.sp
                            )
                        )
                        Text(
                            text = "Curated luxury promotions and celebration gifts",
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

                if (statusMsg != null) {
                    Surface(
                        color = LuxuryColors.LuxuryEmerald.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                        border = BorderStroke(1.dp, LuxuryColors.LuxuryEmerald),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        Text(
                            text = statusMsg!!,
                            color = LuxuryColors.LuxuryEmerald,
                            style = LuxuryTypography.BodySubtle,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                // 1. Personalized Offer Feed (Feature 169)
                Text("YOUR PERSONALIZED OFFERS", style = LuxuryTypography.TitleGold.copy(fontSize = 13.sp))
                Spacer(modifier = Modifier.height(8.dp))

                val offers = offersData?.offers ?: emptyList()
                if (offers.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                        color = LuxuryColors.CardSurface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No active promotions at this moment.",
                            style = LuxuryTypography.BodySubtle,
                            modifier = Modifier.padding(16.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    offers.forEach { offer ->
                        Surface(
                            shape = RoundedCornerShape(LuxuryDimensions.RadiusMedium),
                            color = LuxuryColors.CardSurface,
                            border = BorderStroke(1.dp, LuxuryColors.ChampagneGold.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = offer.title,
                                        style = LuxuryTypography.TitleGold.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    )
                                    GoldBadge(offer.offerType)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = offer.description, style = LuxuryTypography.BodySubtle)

                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "CODE: ${offer.promoCode}",
                                        style = LuxuryTypography.OtpDigits.copy(fontSize = 14.sp, letterSpacing = 2.sp)
                                    )
                                    Button(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("Promo Code", offer.promoCode))
                                            Toast.makeText(context, "Promo code copied", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = LuxuryColors.ChampagneGold,
                                            contentColor = LuxuryColors.ObsidianBlack
                                        ),
                                        shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall)
                                    ) {
                                        Text("Copy Code", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 2. Birthday & Anniversary Celebrations (Feature 168)
                LuxuryCard(modifier = Modifier.fillMaxWidth()) {
                    Text("CELEBRATION MILESTONES", style = LuxuryTypography.TitleGold.copy(fontSize = 13.sp))
                    Text(
                        text = "Receive ₹500 complimentary ride credit on your special days",
                        style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = birthdayInput,
                        onValueChange = { birthdayInput = it },
                        label = { Text("Birthday Date (YYYY-MM-DD)", color = LuxuryColors.SubtleGray) },
                        placeholder = { Text("e.g. 1990-08-15", color = LuxuryColors.SubtleGray) },
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
                        value = anniversaryInput,
                        onValueChange = { anniversaryInput = it },
                        label = { Text("Anniversary Date (YYYY-MM-DD)", color = LuxuryColors.SubtleGray) },
                        placeholder = { Text("e.g. 2020-12-05", color = LuxuryColors.SubtleGray) },
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
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 3. Notification Frequency Control (Feature 170)
                LuxuryCard(modifier = Modifier.fillMaxWidth()) {
                    Text("OFFER NOTIFICATION CADENCE", style = LuxuryTypography.TitleGold.copy(fontSize = 13.sp))
                    Text(
                        text = "Customize how frequently executive promotions are dispatched",
                        style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    listOf(
                        "DAILY" to "Daily Executive Alerts",
                        "WEEKLY" to "Weekly Curated Digest",
                        "MONTHLY" to "Monthly Sovereign Highlights",
                        "SILENT" to "Silent (In-App Feed Only)"
                    ).forEach { (freqKey, freqLabel) ->
                        val isSelected = selectedFrequency == freqKey
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedFrequency = freqKey }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedFrequency = freqKey },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = LuxuryColors.ChampagneGold,
                                    unselectedColor = LuxuryColors.CardBorder
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = freqLabel, style = LuxuryTypography.BodyRegular)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Save Preferences Button
                LuxuryButton(
                    text = if (isSavingPreferences) "Saving..." else "Save Celebration & Frequency Settings",
                    onClick = {
                        isSavingPreferences = true
                        scope.launch {
                            try {
                                val res = ApiClient.getService().updateOfferPreferences(
                                    OfferPreferencesRequest(
                                        frequency = selectedFrequency,
                                        birthdayDate = birthdayInput.ifBlank { null },
                                        anniversaryDate = anniversaryInput.ifBlank { null }
                                    )
                                )
                                if (res.isSuccessful) {
                                    statusMsg = "Celebration dates and notification cadence updated!"
                                    Toast.makeText(context, "Preferences saved", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                statusMsg = "Error saving preferences: ${e.localizedMessage}"
                            } finally {
                                isSavingPreferences = false
                            }
                        }
                    },
                    isLoading = isSavingPreferences
                )
            }
        }
    }
}

// =========================================================================
// Features 171, 172, 173: Ride History Filters, Repeat Past Trip,
// Download Monthly Ride Statement
// =========================================================================

@Composable
fun PastJournalsAndStatementsDialog(
    onDismiss: () -> Unit,
    onTripRepeated: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var statusFilter by remember { mutableStateOf<String?>("COMPLETED") } // ALL, COMPLETED, CANCELLED
    var filteredRides by remember { mutableStateOf<List<FilteredRideDto>>(emptyList()) }
    var monthlyStatement by remember { mutableStateOf<MonthlyStatementResponse?>(null) }
    var selectedMonth by remember { mutableIntStateOf(10) }
    var selectedYear by remember { mutableIntStateOf(2026) }

    var isLoadingRides by remember { mutableStateOf(true) }
    var isRepeatingRide by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val loadRides = {
        scope.launch {
            isLoadingRides = true
            try {
                val res = ApiClient.getService().getFilteredRideHistory(
                    status = if (statusFilter == "ALL") null else statusFilter,
                    limit = 20
                )
                if (res.isSuccessful) filteredRides = res.body() ?: emptyList()

                val stmtRes = ApiClient.getService().getMonthlyRideStatement(year = selectedYear, month = selectedMonth)
                if (stmtRes.isSuccessful) monthlyStatement = stmtRes.body()
            } catch (_: Exception) {} finally {
                isLoadingRides = false
            }
        }
    }

    LaunchedEffect(statusFilter, selectedMonth, selectedYear) {
        loadRides()
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
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "JOURNEY JOURNALS & STATEMENTS",
                            style = LuxuryTypography.HeadlineMedium.copy(
                                color = LuxuryColors.ChampagneGold,
                                fontSize = 18.sp
                            )
                        )
                        Text(
                            text = "Historical manifests, repeat routing & statements",
                            style = LuxuryTypography.BodySubtle
                        )
                    }
                    Text(
                        text = "✕",
                        style = LuxuryTypography.TitleGold.copy(fontSize = 18.sp),
                        modifier = Modifier.clickable { onDismiss() }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Monthly Statement Summary Card (Feature 173)
                if (monthlyStatement != null) {
                    Surface(
                        shape = RoundedCornerShape(LuxuryDimensions.RadiusMedium),
                        color = LuxuryColors.CardSurface,
                        border = BorderStroke(1.dp, LuxuryColors.ChampagneGold),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "MONTHLY STATEMENT • ${monthlyStatement!!.statementPeriod}",
                                    style = LuxuryTypography.TitleGold.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                )
                                Button(
                                    onClick = {
                                        val cleanBase = ApiClient.baseUrl.removeSuffix("/")
                                        val fullUrl = if (monthlyStatement!!.csvDownloadUrl.startsWith("http")) monthlyStatement!!.csvDownloadUrl else cleanBase + monthlyStatement!!.csvDownloadUrl
                                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl))
                                        context.startActivity(browserIntent)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = LuxuryColors.ChampagneGold,
                                        contentColor = LuxuryColors.ObsidianBlack
                                    ),
                                    shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("Download CSV", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("RIDES", style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp))
                                    Text("${monthlyStatement!!.totalRides}", style = LuxuryTypography.HeadlineMedium.copy(fontSize = 16.sp))
                                }
                                Column {
                                    Text("TOTAL SPEND", style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp))
                                    Text("₹${monthlyStatement!!.totalSpendRupees.toInt()}", style = LuxuryTypography.HeadlineMedium.copy(fontSize = 16.sp, color = LuxuryColors.ChampagneGold))
                                }
                                Column {
                                    Text("TIPS GIVEN", style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp))
                                    Text("₹${monthlyStatement!!.totalTipsRupees.toInt()}", style = LuxuryTypography.HeadlineMedium.copy(fontSize = 16.sp))
                                }
                                Column {
                                    Text("DISTANCE", style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp))
                                    Text("%.1f km".format(monthlyStatement!!.totalDistanceKm), style = LuxuryTypography.HeadlineMedium.copy(fontSize = 16.sp))
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Filter Chips (Feature 171)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("ALL", "COMPLETED", "CANCELLED").forEach { filterOpt ->
                        val isSelected = statusFilter == filterOpt || (filterOpt == "ALL" && statusFilter == null)
                        Surface(
                            shape = RoundedCornerShape(LuxuryDimensions.RadiusPill),
                            color = if (isSelected) LuxuryColors.ChampagneGold else LuxuryColors.CardSurface,
                            border = BorderStroke(1.dp, if (isSelected) LuxuryColors.ChampagneGold else LuxuryColors.CardBorder),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    statusFilter = if (filterOpt == "ALL") null else filterOpt
                                }
                        ) {
                            Text(
                                text = filterOpt,
                                color = if (isSelected) LuxuryColors.ObsidianBlack else LuxuryColors.PlatinumWhite,
                                style = LuxuryTypography.TitleGold.copy(
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                modifier = Modifier.padding(vertical = 6.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

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

                // Ride List with Repeat Feature (Feature 172)
                if (isLoadingRides) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = LuxuryColors.ChampagneGold)
                    }
                } else if (filteredRides.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No matching journeys found.", style = LuxuryTypography.BodySubtle)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredRides) { ride ->
                            LuxuryCard(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "RIDE #${ride.rideId.take(10)}",
                                        style = LuxuryTypography.TitleGold.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    )
                                    GoldBadge(ride.status)
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Pickup: ${ride.pickupAddress}",
                                    style = LuxuryTypography.BodyRegular.copy(fontSize = 12.sp)
                                )
                                Text(
                                    text = "Dest: ${ride.destinationAddress}",
                                    style = LuxuryTypography.BodyRegular.copy(fontSize = 12.sp, color = LuxuryColors.ChampagneGold)
                                )

                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Fare: ₹${ride.fareRupees.toInt()}${if (ride.tipCents > 0) " (+₹${ride.tipCents / 100} tip)" else ""}",
                                            style = LuxuryTypography.BodyRegular.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        Text(
                                            text = "${ride.createdAt.take(10)} • +${ride.pointsEarned} pts",
                                            style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp)
                                        )
                                    }

                                    // Repeat Trip Button (Feature 172)
                                    Button(
                                        onClick = {
                                            isRepeatingRide = true
                                            scope.launch {
                                                try {
                                                    val res = ApiClient.getService().repeatPastTrip(ride.rideId)
                                                    if (res.isSuccessful && res.body() != null) {
                                                        val rep = res.body()!!
                                                        statusMessage = "Trip itinerary repeated! Ride #${rep.rideId.take(8)} scheduled."
                                                        Toast.makeText(context, "Repeat trip confirmed!", Toast.LENGTH_SHORT).show()
                                                        onTripRepeated(rep.rideId)
                                                    }
                                                } catch (e: Exception) {
                                                    statusMessage = "Error repeating trip: ${e.localizedMessage}"
                                                } finally {
                                                    isRepeatingRide = false
                                                }
                                            }
                                        },
                                        enabled = !isRepeatingRide,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = LuxuryColors.ChampagneGold,
                                            contentColor = LuxuryColors.ObsidianBlack
                                        ),
                                        shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text("Repeat Trip", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
