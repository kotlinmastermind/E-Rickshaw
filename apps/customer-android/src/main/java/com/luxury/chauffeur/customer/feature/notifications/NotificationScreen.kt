package com.luxury.chauffeur.customer.feature.notifications

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.luxury.chauffeur.customer.core.designsystem.*

data class LuxuryNotificationItem(val title: String, val body: String, val timeAgo: String)

@Composable
fun NotificationScreen(
    onNavigateBack: () -> Unit
) {
    val notifications = remember {
        listOf(
            LuxuryNotificationItem("Chauffeur Allocated", "Rajeshwar Varma (Mercedes-Benz S-Class) has accepted your VIP booking.", "10m ago"),
            LuxuryNotificationItem("Passcode Verification", "Your trip authorization code is 849201. Share only upon entering vehicle.", "12m ago"),
            LuxuryNotificationItem("Welcome to Grand Chauffeur", "Your distinguished guest account is verified and ready for reservations.", "1d ago")
        )
    }

    Scaffold(
        containerColor = LuxuryColors.ObsidianBlack,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "NOTIFICATIONS",
                    style = LuxuryTypography.HeadlineMedium.copy(color = LuxuryColors.ChampagneGold)
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(notifications) { notif ->
                    LuxuryCard {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(notif.title, style = LuxuryTypography.BodyRegular.copy(fontWeight = FontWeight.Bold))
                            Text(notif.timeAgo, style = LuxuryTypography.BodySubtle)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(notif.body, style = LuxuryTypography.BodySubtle)
                    }
                }
            }

            LuxuryButton(
                text = "BACK TO JOURNEY",
                isSecondary = true,
                onClick = onNavigateBack,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }
    }
}
