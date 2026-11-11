package com.vaisentra.mobility.rider.feature.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaisentra.mobility.core.map.AppMapDetailsCatalog
import com.vaisentra.mobility.core.map.AppMapType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapLayersBottomSheet(
    selectedMapType: String,
    mapDetailsState: Map<String, Boolean>,
    onSelectMapType: (String) -> Unit,
    onToggleDetail: (String, Boolean) -> Unit,
    onDismissRequest: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = Color(0xFF0F172A),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(42.dp)
                    .height(4.dp)
                    .background(Color(0xFF334155), CircleShape)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Map Style & Details",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Personalize vector layers & real-time telemetry",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }
                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color(0xFF1E293B), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Section 1: Map Types
            Text(
                text = "MAP TYPE",
                color = Color(0xFFEAB308),
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AppMapType.values().forEach { type ->
                    val isSelected = selectedMapType.equals(type.code, ignoreCase = true)
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelectMapType(type.code) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) Color(0xFF1E293B) else Color(0xFF151D2C),
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) Color(0xFF10B981) else Color(0xFF334155)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = type.icon,
                                fontSize = 26.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = type.displayName,
                                color = if (isSelected) Color(0xFF34D399) else Color(0xFFCBD5E1),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Section 2: Map Details
            Text(
                text = "MAP DETAILS & OVERLAYS",
                color = Color(0xFFEAB308),
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(AppMapDetailsCatalog.ALL_DETAILS) { detail ->
                    val isEnabled = mapDetailsState[detail.key] ?: detail.defaultEnabled
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF151D2C),
                        border = BorderStroke(1.dp, if (isEnabled) Color(0xFF047857) else Color(0xFF1E293B))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isEnabled) Color(0xFF065F46) else Color(0xFF1E293B)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(detail.icon, fontSize = 20.sp)
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = detail.title,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = detail.description,
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }

                            Switch(
                                checked = isEnabled,
                                onCheckedChange = { onToggleDetail(detail.key, it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF10B981),
                                    uncheckedThumbColor = Color(0xFF94A3B8),
                                    uncheckedTrackColor = Color(0xFF334155)
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

