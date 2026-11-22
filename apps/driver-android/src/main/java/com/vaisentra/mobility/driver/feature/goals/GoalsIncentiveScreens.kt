package com.vaisentra.mobility.driver.feature.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaisentra.mobility.driver.components.*
import com.vaisentra.mobility.driver.data.*
import com.vaisentra.mobility.driver.theme.*
import kotlinx.coroutines.launch

/**
 * Screen 36: Daily Targets & Incentive Bonus
 */
@Composable
fun DailyTargetsScreen(
    onNavigateBack: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    val dailyTarget by DriverRepository.dailyTarget.collectAsState()

    LaunchedEffect(Unit) {
        DriverRepository.fetchDailyTargets()
    }

    val completedRides = dailyTarget.completedRides
    val targetRides = dailyTarget.targetRides.coerceAtLeast(1)
    val progress = (completedRides.toFloat() / targetRides.toFloat()).coerceIn(0f, 1f)
    val remaining = (targetRides - completedRides).coerceAtLeast(0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Daily Targets & Bonus",
            subtitle = "Peak hour ride missions & guaranteed rewards",
            onBack = onNavigateBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                CockpitCard(glow = true) {
                    Column {
                        CockpitBadge(if (dailyTarget.isAchieved) "TARGET ACHIEVED" else "DAILY QUEST", AmberWarning)
                        Spacer(Modifier.height(6.dp))
                        Text("Complete $targetRides rides today", color = CockpitTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Reward: ₹${"%.0f".format(dailyTarget.rewardRupees)} Extra Bonus Credit", color = EmeraldPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(14.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = EmeraldPrimary,
                        trackColor = CockpitSurfaceHighlight
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("$completedRides / $targetRides completed", color = CockpitTextSecondary, fontSize = 12.sp)
                        Text("$remaining rides left to unlock ₹${"%.0f".format(dailyTarget.rewardRupees)}", color = ElectricCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Screen 37: Weekly Goals & Progress (GET /api/v1/drivers/financials/goals/weekly)
 */
@Composable
fun WeeklyGoalsScreen(
    onNavigateBack: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    val weeklyGoal by DriverRepository.weeklyGoal.collectAsState()
    LaunchedEffect(Unit) { DriverRepository.fetchWeeklyGoal() }

    val targetTrips = weeklyGoal.targetTrips.coerceAtLeast(1)
    val progressFraction = (weeklyGoal.progressPercentage.toFloat() / 100f).coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Weekly Tier Goals",
            subtitle = "Cumulative milestone bonuses & weekly pace",
            onBack = onNavigateBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                CockpitCard(glow = true) {
                    Text("WEEKLY PROGRESS", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("${weeklyGoal.currentTrips} / $targetTrips Trips", color = ElectricCyan, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "Weekly Earnings: ₹${"%.2f".format(weeklyGoal.currentEarningsRupees)} / ₹${"%.0f".format(weeklyGoal.targetEarningsRupees)}",
                        color = EmeraldPrimary,
                        fontSize = 13.sp
                    )
                    if (weeklyGoal.paceMessage.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(weeklyGoal.paceMessage, color = CockpitTextSecondary, fontSize = 12.sp)
                    }

                    Spacer(Modifier.height(14.dp))
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = ElectricCyan,
                        trackColor = CockpitSurfaceHighlight
                    )
                }
            }
        }
    }
}

/**
 * Screen 38: Streak Missions & Badges (GET /api/v1/drivers/financials/incentives)
 */
@Composable
fun StreakMissionsScreen(
    onNavigateBack: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    val milestones by DriverRepository.incentiveMilestones.collectAsState()

    LaunchedEffect(Unit) {
        DriverRepository.fetchIncentiveMilestones()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Streak Missions & Badges",
            subtitle = "Live incentive quests & bonus tiers",
            onBack = onNavigateBack
        )

        if (milestones.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                Text("No active incentive quests right now.", color = CockpitTextMuted, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(milestones) { quest ->
                    val tint = if (quest.isUnlocked) EmeraldPrimary else AmberWarning
                    CockpitCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(tint.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = tint)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(quest.programName, color = CockpitTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(
                                        "${quest.currentTrips}/${quest.targetTrips} trips • Reward ₹${"%.0f".format(quest.rewardRupees)}",
                                        color = CockpitTextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            CockpitBadge(if (quest.isUnlocked) "UNLOCKED" else quest.tierName, tint)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Screen 43: Performance & Safety Scorecard
 */
@Composable
fun PerformanceScorecardScreen(
    onNavigateBack: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    val scorecard by DriverRepository.scorecard.collectAsState()
    val ratings by DriverRepository.driverRatings.collectAsState()
    val appeals by DriverRepository.ratingAppeals.collectAsState()
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }

    var selectedRatingForAppeal by remember { mutableStateOf<DriverRatingItemDto?>(null) }
    var appealReason by remember { mutableStateOf("") }
    var isSubmittingAppeal by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        DriverRepository.fetchPerformanceScorecard()
        DriverRepository.fetchSafetyOverviewAndRatings()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Pilot Scorecard",
            subtitle = "Live Supabase quality metrics & rating appeals",
            onBack = onNavigateBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                CockpitCard(glow = true) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("CUSTOMER RATING", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            if (scorecard.rating > 0.0) {
                                Text("${"%.2f".format(scorecard.rating)} ★", color = EmeraldPrimary, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                                Text("${scorecard.ratingCount} Verified Reviews in Supabase", color = CockpitTextMuted, fontSize = 11.sp)
                            } else {
                                Text("New Pilot ★", color = EmeraldPrimary, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                                Text("0 reviews in Supabase (Clean Record)", color = CockpitTextMuted, fontSize = 11.sp)
                            }
                            Spacer(Modifier.height(4.dp))
                            if (scorecard.cleanlinessScore > 0.0) {
                                Text("Cleanliness: ${"%.2f".format(scorecard.cleanlinessScore)} / 5.0", color = ElectricCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Text("Cleanliness: Not rated yet", color = ElectricCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Icon(Icons.Default.Stars, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(48.dp))
                    }
                }
            }

            item {
                CockpitCard {
                    Text("CORE PERFORMANCE BENCHMARKS (SUPABASE LIVE)", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    val ratingDisplay = if (scorecard.rating > 0.0) "${"%.2f".format(scorecard.rating)} ★" else "No reviews yet"
                    ScoreRow("Customer Satisfaction Rating", ratingDisplay, EmeraldPrimary)
                    
                    val acceptanceDisplay = if (scorecard.totalOffers > 0) {
                        "${"%.1f".format(scorecard.acceptanceRate)}% (${scorecard.acceptedOffers}/${scorecard.totalOffers} accepted)"
                    } else {
                        "100.0% (0 missed offers)"
                    }
                    ScoreRow("Dispatch Acceptance Rate", acceptanceDisplay, EmeraldPrimary)
                    
                    val cancelDisplay = if (scorecard.totalTrips > 0) "${"%.1f".format(scorecard.cancellationRate)}%" else "0.0% (0 cancellations)"
                    ScoreRow("Driver Cancellation Rate", cancelDisplay, EmeraldPrimary)
                    
                    ScoreRow(
                        "Safety Incidents",
                        if (scorecard.safetyIncidentCount == 0) "0 (Zero Incidents ✓)" else "${scorecard.safetyIncidentCount} Incidents",
                        if (scorecard.safetyIncidentCount == 0) EmeraldPrimary else CrimsonAlert
                    )
                    
                    val cleanDisplay = if (scorecard.cleanlinessScore > 0.0) "${"%.2f".format(scorecard.cleanlinessScore)}" else "Not rated yet"
                    ScoreRow("Cabin Cleanliness Score", cleanDisplay, EmeraldPrimary)
                    
                    ScoreRow("Total Completed Trips", "${scorecard.totalTrips} Trips", CockpitTextPrimary)
                }
            }

            // Section: Active Rating Appeals if any
            if (appeals.isNotEmpty()) {
                item {
                    CockpitCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("ACTIVE RATING DISPUTES & APPEALS", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            CockpitBadge("${appeals.size} FILED", AmberWarning)
                        }
                        Spacer(Modifier.height(8.dp))
                        appeals.forEach { a ->
                            val status = a["status"] as? String ?: "PENDING"
                            val statusColor = when (status.uppercase()) {
                                "APPROVED", "ACCEPTED" -> EmeraldPrimary
                                "REJECTED" -> CrimsonAlert
                                else -> AmberWarning
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Appeal ID: ${(a["rating_id"] as? String ?: "").take(8)}...", color = CockpitTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text("Status: $status", color = statusColor, fontSize = 11.sp)
                                }
                                CockpitBadge(status.uppercase(), statusColor)
                            }
                            Divider(color = CockpitBorder.copy(alpha = 0.5f), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
            }

            // Section: Recent Ratings & Reviews with Appeal action
            item {
                CockpitCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("RECENT CUSTOMER RATINGS", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("${ratings.size} Reviews", color = CockpitTextSecondary, fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(8.dp))

                    if (ratings.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                            Text("All recent rides have 5.0★ clean ratings. No negative feedback.", color = CockpitTextMuted, fontSize = 12.sp)
                        }
                    } else {
                        ratings.take(10).forEach { r ->
                            val isLowRating = r.rating < 4.0f
                            val isAppealed = appeals.any { (it["rating_id"] as? String) == r.id }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .background(CockpitBackground.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    .padding(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("${r.rating.toInt()} ★", color = if (isLowRating) AmberWarning else EmeraldPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.width(8.dp))
                                        Text(r.createdAt?.take(10) ?: "Trip Review", color = CockpitTextMuted, fontSize = 11.sp)
                                    }
                                    if (isAppealed) {
                                        CockpitBadge("APPEALED", AmberWarning)
                                    } else if (isLowRating) {
                                        OutlinedButton(
                                            onClick = {
                                                selectedRatingForAppeal = r
                                                appealReason = ""
                                            },
                                            modifier = Modifier.height(28.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                            shape = RoundedCornerShape(6.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, AmberWarning),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberWarning)
                                        ) {
                                            Text("Dispute / Appeal", color = AmberWarning, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        CockpitBadge("VERIFIED", EmeraldPrimary)
                                    }
                                }

                                if (!r.feedback.isNullOrBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text("\"${r.feedback}\"", color = CockpitTextPrimary, fontSize = 12.sp)
                                }
                                if (r.cleanliness != null) {
                                    Text("Cleanliness: ${r.cleanliness} ★", color = ElectricCyan, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }

            item {
                CockpitButton(
                    text = if (isRefreshing) "Syncing with Supabase..." else "↻ Refresh Live Database Metrics",
                    enabled = !isRefreshing,
                    onClick = {
                        isRefreshing = true
                        scope.launch {
                            val res = DriverRepository.fetchPerformanceScorecard()
                            DriverRepository.fetchSafetyOverviewAndRatings()
                            isRefreshing = false
                            if (res.isSuccess) {
                                onShowToast("Scorecard metrics synced with live Supabase database!")
                            } else {
                                onShowToast("Sync failed: ${res.exceptionOrNull()?.message}")
                            }
                        }
                    },
                    backgroundColor = EmeraldPrimary,
                    textColor = CockpitBackground
                )
            }
        }
    }

    // Modal Dialog: Dispute / Appeal Rating
    val ratingToAppeal = selectedRatingForAppeal
    if (ratingToAppeal != null) {
        AlertDialog(
            onDismissRequest = { if (!isSubmittingAppeal) selectedRatingForAppeal = null },
            title = {
                Column {
                    Text("Dispute & Appeal Rating", color = CockpitTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Chauffeur Arbitration Board Review", color = CockpitTextMuted, fontSize = 11.sp)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Rating: ${ratingToAppeal.rating.toInt()} ★ (${ratingToAppeal.createdAt?.take(10) ?: "Trip"})", color = AmberWarning, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))

                    Text("SELECT COMMON DISPUTE REASON:", color = CockpitTextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    val quickTags = listOf("Unfair traffic delay", "Passenger refused toll", "False complaint", "Route dispute")
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        quickTags.take(2).forEach { tag ->
                            OutlinedButton(
                                onClick = { appealReason = tag },
                                modifier = Modifier.weight(1f).height(30.dp),
                                contentPadding = PaddingValues(2.dp),
                                shape = RoundedCornerShape(6.dp),
                                border = androidx.compose.foundation.BorderStroke(0.8.dp, if (appealReason == tag) ElectricCyan else CockpitBorder)
                            ) {
                                Text(tag, fontSize = 9.sp, color = if (appealReason == tag) ElectricCyan else CockpitTextSecondary, maxLines = 1)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        quickTags.drop(2).forEach { tag ->
                            OutlinedButton(
                                onClick = { appealReason = tag },
                                modifier = Modifier.weight(1f).height(30.dp),
                                contentPadding = PaddingValues(2.dp),
                                shape = RoundedCornerShape(6.dp),
                                border = androidx.compose.foundation.BorderStroke(0.8.dp, if (appealReason == tag) ElectricCyan else CockpitBorder)
                            ) {
                                Text(tag, fontSize = 9.sp, color = if (appealReason == tag) ElectricCyan else CockpitTextSecondary, maxLines = 1)
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    CockpitTextField(
                        value = appealReason,
                        onValueChange = { appealReason = it },
                        placeholder = "State specific justification (minimum 20 characters)...",
                        label = "Appeal Justification",
                        modifier = Modifier.height(90.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${appealReason.length} / 20 characters minimum",
                        color = if (appealReason.length >= 20) EmeraldPrimary else CockpitTextMuted,
                        fontSize = 10.sp
                    )
                }
            },
            confirmButton = {
                CockpitButton(
                    text = if (isSubmittingAppeal) "Submitting..." else "Submit Formal Appeal",
                    enabled = !isSubmittingAppeal && appealReason.length >= 20,
                    onClick = {
                        isSubmittingAppeal = true
                        scope.launch {
                            val res = DriverRepository.appealDriverRating(ratingToAppeal.id, appealReason)
                            isSubmittingAppeal = false
                            if (res.isSuccess) {
                                onShowToast("Appeal submitted successfully for telemetric review!")
                                selectedRatingForAppeal = null
                            } else {
                                onShowToast("Failed: ${res.exceptionOrNull()?.message}")
                            }
                        }
                    },
                    backgroundColor = EmeraldPrimary,
                    textColor = CockpitBackground,
                    modifier = Modifier.height(40.dp)
                )
            },
            dismissButton = {
                TextButton(
                    onClick = { selectedRatingForAppeal = null },
                    enabled = !isSubmittingAppeal
                ) {
                    Text("Cancel", color = CockpitTextMuted)
                }
            },
            containerColor = CockpitCardBackground
        )
    }
}

@Composable
private fun ScoreRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = CockpitTextSecondary, fontSize = 12.sp)
        Text(value, color = valueColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}