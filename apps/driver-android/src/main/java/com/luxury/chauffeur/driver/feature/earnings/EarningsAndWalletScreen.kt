package com.luxury.chauffeur.driver.feature.earnings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luxury.chauffeur.driver.core.designsystem.*

data class PayoutTransaction(val title: String, val date: String, val amount: String)

@Composable
fun EarningsAndWalletScreen(
    onNavigateBack: () -> Unit
) {
    val history = remember {
        listOf(
            PayoutTransaction("Colaba to T2 Private Terminal", "Today, 14:20", "+₹1,280"),
            PayoutTransaction("BKC Enclave to Nariman Point", "Yesterday, 19:40", "+₹1,920"),
            PayoutTransaction("Weekly Direct Bank Settlement", "21 Oct 2026", "Settled to HDFC Bank")
        )
    }

    Scaffold(
        containerColor = DriverColors.MidnightBlack,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Text("EARNINGS & WALLET", style = DriverTypography.Title.copy(fontSize = 18.sp, color = DriverColors.ChauffeurGold))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                // Wallet Balance Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = DriverColors.SurfaceCard,
                    border = BorderStroke(1.dp, DriverColors.ChauffeurGold),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("AVAILABLE CHAUFFEUR BALANCE", style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.TextMuted))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("₹25,400.00", style = DriverTypography.MetricValue.copy(fontSize = 32.sp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Next automated settlement: Monday 09:00 AM", style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.StatusOnline))
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                Text("RECENT SETTLEMENTS & TRIPS", style = DriverTypography.Subtitle)
                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
                ) {
                    items(history) { item ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = DriverColors.CockpitDark,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(item.title, style = DriverTypography.Body)
                                    Text(item.date, style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.TextMuted))
                                }
                                Text(item.amount, style = DriverTypography.Subtitle)
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onNavigateBack,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DriverColors.ChauffeurGold, contentColor = DriverColors.MidnightBlack),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("BACK TO COCKPIT")
            }
        }
    }
}
