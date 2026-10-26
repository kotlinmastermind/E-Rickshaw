package com.luxury.chauffeur.driver.feature.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luxury.chauffeur.driver.core.designsystem.*

@Composable
fun DriverProfileScreen(
    onNavigateBack: () -> Unit
) {
    Scaffold(
        containerColor = DriverColors.MidnightBlack,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Text("CHAUFFEUR CREDENTIALS", style = DriverTypography.Title.copy(fontSize = 18.sp, color = DriverColors.ChauffeurGold))
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
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = DriverColors.SurfaceCard,
                    border = BorderStroke(1.dp, DriverColors.SurfaceBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Rajeshwar Varma", style = DriverTypography.Title)
                        Text("Master Chauffeur • Verified Partner", style = DriverTypography.Subtitle.copy(fontSize = 13.sp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Commercial License: DL-MH01-2020-009988", style = DriverTypography.Body)
                        Text("Primary Contact: +91 98765 00001", style = DriverTypography.Body)
                        Text("Rating Average: ★ 4.98 (342 reviews)", style = DriverTypography.Body)
                        Text("Total Escorts Completed: 340 Trips", style = DriverTypography.Body)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = DriverColors.CockpitDark,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("ASSIGNED LUXURY VEHICLE", style = DriverTypography.Subtitle)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Mercedes-Benz E-Class E350d (2025)", style = DriverTypography.Body)
                        Text("Plate: MH01-LX-0001 • Obsidian Black", style = DriverTypography.Body.copy(color = DriverColors.TextMuted))
                        Text("Amenities: Bottled Evian, Wi-Fi 6, Reclining Nappa Leather", style = DriverTypography.Body.copy(color = DriverColors.TextMuted, fontSize = 12.sp))
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
