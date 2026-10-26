package com.luxury.chauffeur.driver.feature.documents

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luxury.chauffeur.driver.core.designsystem.*

data class DocumentItem(val type: String, val name: String, val status: String, val filename: String)

@Composable
fun DocumentUploadScreen(
    onNavigateBack: () -> Unit
) {
    var documents by remember {
        mutableStateOf(
            listOf(
                DocumentItem("DRIVING_LICENSE", "Commercial Chauffeur Driving License", "VERIFIED", "license_verified.pdf"),
                DocumentItem("VEHICLE_REGISTRATION", "Luxury Vehicle Registration (RC)", "VERIFIED", "rc_mh01_0001.pdf"),
                DocumentItem("INSURANCE", "Comprehensive Commercial Fleet Insurance", "PENDING", "insurance_policy.pdf"),
                DocumentItem("IDENTITY_PROOF", "Aadhaar / Passport VIP Identification", "VERIFIED", "identity_doc.pdf")
            )
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
                Text("DOCUMENT COMPLIANCE", style = DriverTypography.Title.copy(fontSize = 18.sp, color = DriverColors.ChauffeurGold))
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
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(documents) { doc ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = DriverColors.SurfaceCard,
                        border = BorderStroke(1.dp, DriverColors.SurfaceBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(doc.name, style = DriverTypography.Body)
                                Text(doc.filename, style = DriverTypography.Body.copy(fontSize = 11.sp, color = DriverColors.TextMuted))
                            }
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = if (doc.status == "VERIFIED") DriverColors.StatusOnline.copy(alpha = 0.2f) else DriverColors.StatusBusy.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = doc.status,
                                    color = if (doc.status == "VERIFIED") DriverColors.StatusOnline else DriverColors.StatusBusy,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
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
