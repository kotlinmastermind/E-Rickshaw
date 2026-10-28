package com.luxury.chauffeur.driver.feature.enterprise

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.luxury.chauffeur.driver.core.designsystem.*
import com.luxury.chauffeur.driver.core.network.DriverApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DriverEnterpriseAddonsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var liveAddons by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var verifiedItems by remember { mutableStateOf(setOf("ADDON_INTERCITY_TOLL_FASTAG", "ADDON_ARMORED_ESCORT")) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isLoading = true
        scope.launch(Dispatchers.IO) {
            val res = runCatching { DriverApiClient.getService().getChauffeurAddons() }.getOrNull()
            withContext(Dispatchers.Main) {
                if (res?.isSuccessful == true && !res.body().isNullOrEmpty()) {
                    liveAddons = res.body()!!
                }
                isLoading = false
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(16.dp)),
            color = DriverColors.MidnightBlack,
            border = BorderStroke(1.5.dp, DriverColors.ChauffeurGold)
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
                            text = "INTERCITY & SPECIALTY ADD-ONS",
                            style = DriverTypography.Title.copy(
                                color = DriverColors.ChauffeurGold,
                                fontSize = 16.sp
                            )
                        )
                        Text(
                            text = "Verification for Add-ons, Hourly & Intercity (492-494)",
                            style = DriverTypography.Body.copy(color = DriverColors.TextMuted, fontSize = 12.sp)
                        )
                    }
                    Text(
                        text = "✕",
                        style = DriverTypography.Subtitle.copy(fontSize = 18.sp),
                        modifier = Modifier.clickable { onDismiss() }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Specialty Add-ons Checklist
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DriverColors.SurfaceCard,
                    border = BorderStroke(1.dp, DriverColors.SurfaceBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("SPECIALTY VOYAGE ADD-ONS (Features 492-494)", style = DriverTypography.Subtitle.copy(fontSize = 13.sp))
                        Spacer(modifier = Modifier.height(8.dp))

                        val displayList = if (liveAddons.isNotEmpty()) {
                            liveAddons.map {
                                (it["addon_code"] as? String ?: "ADDON") to (it["title"] as? String ?: "Specialty Service")
                            }
                        } else {
                            listOf(
                                "ADDON_ARMORED_ESCORT" to "Armed Security Detail Accompaniment (Active)",
                                "ADDON_CHAMPAGNE_CRU" to "Chilled Dom Pérignon Champagne (Loaded)",
                                "ADDON_INTERCITY_TOLL_FASTAG" to "Expressway Automatic FastTag (Prepaid)",
                                "ADDON_HOURLY_CHAUFFEUR" to "4-Hour Standing Chauffeur Duty (Allocated)"
                            )
                        }

                        displayList.forEach { (addonCode, addonTitle) ->
                            val isVerified = verifiedItems.contains(addonCode)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        verifiedItems = if (isVerified) {
                                            verifiedItems - addonCode
                                        } else {
                                            verifiedItems + addonCode
                                        }
                                    }
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = addonTitle,
                                        style = DriverTypography.Body.copy(
                                            fontSize = 12.sp,
                                            fontWeight = if (isVerified) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isVerified) DriverColors.TextPure else DriverColors.TextMuted
                                        )
                                    )
                                    Text(text = addonCode, style = DriverTypography.Body.copy(fontSize = 10.sp, color = DriverColors.TextMuted))
                                }
                                if (isVerified) {
                                    GoldBadge("VERIFIED ✓")
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = DriverColors.MidnightBlack,
                                        border = BorderStroke(1.dp, DriverColors.SurfaceBorder)
                                    ) {
                                        Text(
                                            text = "PENDING",
                                            fontSize = 9.sp,
                                            color = DriverColors.TextMuted,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        Toast.makeText(context, "${verifiedItems.size} specialty add-ons verified for departure", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DriverColors.ChauffeurGold,
                        contentColor = DriverColors.MidnightBlack
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Confirm Specialty Readiness (${verifiedItems.size} Verified)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}
