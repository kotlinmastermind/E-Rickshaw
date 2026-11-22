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
import com.vaisentra.mobility.driver.data.DriverRepository
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
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        DriverRepository.fetchPerformanceScorecard()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Pilot Scorecard",
            subtitle = "Live Supabase quality metrics & performance benchmarks",
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

            item {
                CockpitButton(
                    text = if (isRefreshing) "Syncing with Supabase..." else "↻ Refresh Live Database Metrics",
                    enabled = !isRefreshing,
                    onClick = {
                        isRefreshing = true
                        scope.launch {
                            val res = DriverRepository.fetchPerformanceScorecard()
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