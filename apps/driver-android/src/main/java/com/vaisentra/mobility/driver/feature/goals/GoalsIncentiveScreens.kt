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
import com.vaisentra.mobility.driver.theme.*

/**
 * Screen 36: Daily Targets & Incentive Bonus (10 rides in morning peak for ₹300)
 */
@Composable
fun DailyTargetsScreen(
    onNavigateBack: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    var completedRides by remember { mutableIntStateOf(7) }
    val targetRides = 10
    val progress = completedRides.toFloat() / targetRides

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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            CockpitBadge("MORNING PEAK SURGE QUEST", AmberWarning)
                            Spacer(Modifier.height(6.dp))
                            Text("Complete 10 rides (08:00 - 12:00)", color = CockpitTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("Reward: ₹300 Extra Bonus Credit", color = EmeraldPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
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
                        Text("${targetRides - completedRides} rides left to unlock ₹300", color = ElectricCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            item {
                Text("UPCOMING MISSIONS TODAY", color = CockpitTextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            item {
                CockpitCard {
                    CockpitBadge("EVENING RUSH SLAM", ElectricCyan)
                    Spacer(Modifier.height(6.dp))
                    Text("Complete 8 rides between 17:00 - 21:00", color = CockpitTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Bonus: ₹250 wallet credit", color = EmeraldPrimary, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Starts in 1h 45m", color = CockpitTextMuted, fontSize = 11.sp)
                }
            }

            item {
                CockpitCard {
                    CockpitBadge("CLEAN EV CHAMPION", EmeraldPrimary)
                    Spacer(Modifier.height(6.dp))
                    Text("Zero battery-depletion cancelations for 24h", color = CockpitTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Bonus: Free battery swap token worth ₹120", color = EmeraldPrimary, fontSize = 12.sp)
                }
            }
        }
    }
}

/**
 * Screen 37: Weekly Goals & Progress (weekly ride milestones & multiplier tiers)
 */
@Composable
fun WeeklyGoalsScreen(
    onNavigateBack: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    val weeklyRides = 58
    val tier1 = 50
    val tier2 = 75
    val tier3 = 100

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Weekly Tier Goals",
            subtitle = "Cumulative milestone bonuses & commission cashback",
            onBack = onNavigateBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                CockpitCard(glow = true) {
                    Text("WEEKLY PROGRESS (MON - SUN)", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("$weeklyRides Trips Completed", color = ElectricCyan, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Tier 1 Unlocked (+₹800). Next Tier 2 in 17 trips (+₹1,500 total)", color = EmeraldPrimary, fontSize = 13.sp)

                    Spacer(Modifier.height(14.dp))
                    LinearProgressIndicator(
                        progress = { weeklyRides.toFloat() / tier2 },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = ElectricCyan,
                        trackColor = CockpitSurfaceHighlight
                    )
                }
            }

            item {
                Text("WEEKLY REWARD TIERS", color = CockpitTextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            item {
                CockpitCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Bronze Tier (50 Trips)", color = CockpitTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("₹800 Cash Bonus", color = EmeraldPrimary, fontSize = 12.sp)
                        }
                        CockpitBadge("UNLOCKED", EmeraldPrimary)
                    }
                }
            }

            item {
                CockpitCard(glow = true) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Silver Tier (75 Trips)", color = CockpitTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("₹1,500 Cash Bonus + 5% Fee Discount", color = ElectricCyan, fontSize = 12.sp)
                        }
                        CockpitBadge("IN PROGRESS (17 left)", ElectricCyan)
                    }
                }
            }

            item {
                CockpitCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Gold Tier (100 Trips)", color = CockpitTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("₹2,500 Cash Bonus + 100% Free Swap Passes", color = AmberWarning, fontSize = 12.sp)
                        }
                        CockpitBadge("LOCKED", CockpitTextMuted)
                    }
                }
            }
        }
    }
}

/**
 * Screen 38: Streak Missions & Badges (zero-cancellation streak badges)
 */
@Composable
fun StreakMissionsScreen(
    onNavigateBack: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    val badges = listOf(
        PilotBadgeItem("Zero Cancels", "14 days without pilot cancelation", Icons.Default.Shield, EmeraldPrimary, true),
        PilotBadgeItem("Early Bird", "50+ morning feeder rides before 09:00", Icons.Default.WbSunny, AmberWarning, true),
        PilotBadgeItem("Metro Feeder Pro", "500+ metro station pickups completed", Icons.Default.Subway, ElectricCyan, true),
        PilotBadgeItem("Eco Century", "1,000 kg CO2 emissions offset", Icons.Default.Eco, EmeraldPrimary, true),
        PilotBadgeItem("5-Star Master", "Maintain 4.95+ rating over 200 rides", Icons.Default.Star, AmberWarning, false)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Streak Missions & Badges",
            subtitle = "Excellence honours, streak multipliers & pilot bragging rights",
            onBack = onNavigateBack
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                CockpitCard(glow = true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("18-Day Zero Cancellation Streak!", color = CockpitTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Active streak multiplier: +5% on all base fares", color = EmeraldPrimary, fontSize = 12.sp)
                        }
                    }
                }
            }

            items(badges) { badge ->
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
                                    .background(badge.tint.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(badge.icon, contentDescription = null, tint = badge.tint)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(badge.title, color = CockpitTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(badge.desc, color = CockpitTextSecondary, fontSize = 11.sp)
                            }
                        }
                        CockpitBadge(if (badge.earned) "EARNED" else "IN PROGRESS", if (badge.earned) badge.tint else CockpitTextMuted)
                    }
                }
            }
        }
    }
}

/**
 * Screen 43: Performance & Safety Scorecard (rating 4.95★, acceptance 96.5%)
 */
@Composable
fun PerformanceScorecardScreen(
    onNavigateBack: () -> Unit = {},
    onShowToast: (String) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBackground)
            .statusBarsPadding()
    ) {
        CockpitHeader(
            title = "Pilot Scorecard",
            subtitle = "Quality metrics, safety audits & performance rating",
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
                            Text("OVERALL QUALITY SCORE", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text("98.4 / 100", color = EmeraldPrimary, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                            Text("Tier: ELITE DIAMOND PILOT", color = ElectricCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Icon(Icons.Default.Stars, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(48.dp))
                    }
                }
            }

            item {
                CockpitCard {
                    Text("CORE PERFORMANCE BENCHMARKS", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    ScoreRow("Customer Satisfaction Rating", "4.95 ★", EmeraldPrimary)
                    ScoreRow("Dispatch Acceptance Rate", "96.5%", EmeraldPrimary)
                    ScoreRow("Driver Cancellation Rate", "0.4%", EmeraldPrimary)
                    ScoreRow("On-Time Pickup Rate", "99.1%", EmeraldPrimary)
                    ScoreRow("Speed Limit Adherence", "100.0%", EmeraldPrimary)
                    ScoreRow("Vehicle Inspection Compliance", "100.0%", EmeraldPrimary)
                }
            }

            item {
                CockpitCard {
                    Text("COMMUTER FEEDBACK HIGHLIGHTS", color = CockpitTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("“Ramesh Ji drove very smoothly and vehicle was very clean with proper hand sanitizer.”", color = CockpitTextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("“Polite behavior and quick route through metro traffic.”", color = CockpitTextSecondary, fontSize = 12.sp)
                }
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

private data class PilotBadgeItem(
    val title: String,
    val desc: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tint: Color,
    val earned: Boolean
)

