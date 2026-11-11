package com.vaisentra.mobility.rider.feature.demo

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaisentra.mobility.core.designsystem.ElectricNeoTokens
import com.vaisentra.mobility.core.designsystem.NeoCard
import com.vaisentra.mobility.core.designsystem.NeoStatPill
import com.vaisentra.mobility.core.designsystem.NeoTextField

@Composable
fun CustomerDemoInspectorOverlay(
    activeScreenId: Int?,
    onSelectScreen: (Int?) -> Unit,
    content: @Composable () -> Unit
) {
    var isDrawerOpen by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf("Screens") } // "Screens" vs "Popups"
    var activePopupId by remember { mutableStateOf<Int?>(null) }
    var selectedCategory by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var inspectorToast by remember { mutableStateOf<String?>(null) }

    val categories = remember {
        listOf("All", "Onboarding", "Radar & Booking", "Preferences", "In-Trip HUD", "Wallet & Pay", "Safety & SOS", "Post-Trip", "Support", "Loyalty", "Account")
    }

    val filteredScreens = remember(selectedCategory, searchQuery) {
        CustomerScreenCatalog.SCREENS.filter { screen ->
            val matchesCat = selectedCategory == "All" || screen.category == selectedCategory
            val matchesQuery = searchQuery.isBlank() ||
                screen.title.contains(searchQuery, ignoreCase = true) ||
                screen.id.toString() == searchQuery.trim() ||
                screen.category.contains(searchQuery, ignoreCase = true)
            matchesCat && matchesQuery
        }
    }

    val filteredPopups = remember(searchQuery) {
        CustomerPopupCatalog.POPUPS.filter { popup ->
            searchQuery.isBlank() ||
                popup.title.contains(searchQuery, ignoreCase = true) ||
                popup.id.toString() == searchQuery.trim() ||
                popup.alertLevel.contains(searchQuery, ignoreCase = true)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Main Screen or Active Demo Preview Screen
        if (activeScreenId != null) {
            Column(modifier = Modifier.fillMaxSize().background(ElectricNeoTokens.CyberVoid)) {
                // Top Exit Banner
                Surface(
                    color = ElectricNeoTokens.CyberSurfaceElevated,
                    modifier = Modifier.fillMaxWidth().border(1.dp, ElectricNeoTokens.ElectricCyanBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(ElectricNeoTokens.ElectricCyanGlow),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("⚡", fontSize = 14.sp)
                            }
                            Column {
                                Text(
                                    text = "RIDER DEMO • SCREEN #${activeScreenId}",
                                    color = ElectricNeoTokens.ElectricCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = CustomerScreenCatalog.SCREENS.find { it.id == activeScreenId }?.title ?: "Demo",
                                    color = ElectricNeoTokens.TextWhite,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(ElectricNeoTokens.RadiusPill))
                                    .background(ElectricNeoTokens.CyberSurfaceHighlight)
                                    .border(1.dp, ElectricNeoTokens.ElectricCyanBorder, RoundedCornerShape(ElectricNeoTokens.RadiusPill))
                                    .clickable { isDrawerOpen = true }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("Catalog (50+15)", color = ElectricNeoTokens.ElectricCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(ElectricNeoTokens.RadiusPill))
                                    .background(ElectricNeoTokens.CyberCrimsonGlow)
                                    .border(1.dp, ElectricNeoTokens.CyberCrimsonBorder, RoundedCornerShape(ElectricNeoTokens.RadiusPill))
                                    .clickable { onSelectScreen(null) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("✕ Exit", color = ElectricNeoTokens.CyberCrimson, fontSize = 11.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }

                // Render Active Preview Composable
                Box(modifier = Modifier.weight(1f)) {
                    CustomerScreenRenderer(screenId = activeScreenId)
                }
            }
        } else {
            // Real Production App Flow
            content()

            // Floating Neo Pill to open Demo Inspector (Top-Right)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 16.dp, end = 16.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(ElectricNeoTokens.RadiusPill))
                        .background(ElectricNeoTokens.CyberSurfaceElevated)
                        .border(1.5.dp, ElectricNeoTokens.ElectricCyan, RoundedCornerShape(ElectricNeoTokens.RadiusPill))
                        .clickable { isDrawerOpen = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("⚡", fontSize = 14.sp)
                    Text(
                        text = "50 RIDER + 15 POPUPS",
                        color = ElectricNeoTokens.ElectricCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        // Active Interactive Popup Modal Overlay (If triggered)
        if (activePopupId != null) {
            CustomerPopupInteractiveModal(
                popupId = activePopupId!!,
                onDismiss = { activePopupId = null },
                onActionCompleted = { feedback ->
                    inspectorToast = feedback
                }
            )
        }

        // Toast feedback for popup actions
        if (inspectorToast != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 24.dp, start = 20.dp, end = 20.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Surface(
                    color = ElectricNeoTokens.NeonEmeraldGlow,
                    modifier = Modifier
                        .clip(RoundedCornerShape(ElectricNeoTokens.RadiusMedium))
                        .border(1.5.dp, ElectricNeoTokens.NeonEmerald, RoundedCornerShape(ElectricNeoTokens.RadiusMedium))
                        .clickable { inspectorToast = null }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(text = "✓ ${inspectorToast}", color = ElectricNeoTokens.NeonEmerald, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Modal Drawer Sheet for selecting from 50 Screens or 15 Popups
        if (isDrawerOpen) {
            Surface(
                color = ElectricNeoTokens.CyberVoid.copy(alpha = 0.96f),
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Drawer Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "VAISENTRA RIDERGO STUDIO",
                                color = ElectricNeoTokens.ElectricCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "50 Screens & 15 Pop-ups",
                                color = ElectricNeoTokens.TextWhite,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(ElectricNeoTokens.CyberSurfaceHighlight)
                                .clickable { isDrawerOpen = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✕", color = ElectricNeoTokens.TextWhite, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Tab Selector: Screens (50) vs Popups (15)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(ElectricNeoTokens.RadiusPill))
                            .background(ElectricNeoTokens.CyberSurfaceElevated)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(ElectricNeoTokens.RadiusPill))
                                .background(if (activeTab == "Screens") ElectricNeoTokens.ElectricCyan else Color.Transparent)
                                .clickable { activeTab = "Screens" }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "📱 50 Screens",
                                color = if (activeTab == "Screens") Color.Black else ElectricNeoTokens.TextWhite,
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(ElectricNeoTokens.RadiusPill))
                                .background(if (activeTab == "Popups") ElectricNeoTokens.SolarAmber else Color.Transparent)
                                .clickable { activeTab = "Popups" }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "💬 15 Pop-ups",
                                color = if (activeTab == "Popups") Color.Black else ElectricNeoTokens.TextWhite,
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Search Filter
                    NeoTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = if (activeTab == "Screens") "🔍 Search 50 screens..." else "🔍 Search 15 popups..."
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (activeTab == "Screens") {
                        // Category Filter Chips
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(categories) { cat ->
                                val isSelected = selectedCategory == cat
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(ElectricNeoTokens.RadiusPill))
                                        .background(if (isSelected) ElectricNeoTokens.ElectricCyan else ElectricNeoTokens.CyberSurface)
                                        .border(1.dp, if (isSelected) ElectricNeoTokens.ElectricCyan else ElectricNeoTokens.ElectricCyanBorder, RoundedCornerShape(ElectricNeoTokens.RadiusPill))
                                        .clickable { selectedCategory = cat }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = cat,
                                        color = if (isSelected) Color.Black else ElectricNeoTokens.TextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // 50 Screens List
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredScreens) { screen ->
                                val isCurrent = activeScreenId == screen.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(ElectricNeoTokens.RadiusSmall))
                                        .background(if (isCurrent) ElectricNeoTokens.CyberSurfaceHighlight else ElectricNeoTokens.CyberSurfaceCard)
                                        .border(1.dp, if (isCurrent) ElectricNeoTokens.NeonEmerald else ElectricNeoTokens.ElectricCyanBorder.copy(alpha = 0.3f), RoundedCornerShape(ElectricNeoTokens.RadiusSmall))
                                        .clickable {
                                            onSelectScreen(screen.id)
                                            isDrawerOpen = false
                                        }
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(ElectricNeoTokens.CyberSurfaceElevated),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(screen.icon, fontSize = 16.sp)
                                        }
                                        Column {
                                            Text(
                                                text = "${screen.id}. ${screen.title}",
                                                color = ElectricNeoTokens.TextWhite,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = screen.category,
                                                color = ElectricNeoTokens.ElectricCyan,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }

                                    Text(
                                        text = "Open ➔",
                                        color = if (isCurrent) ElectricNeoTokens.NeonEmerald else ElectricNeoTokens.TextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    } else {
                        // 15 Popups List
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredPopups) { popup ->
                                val accent = when (popup.alertLevel) {
                                    "CRITICAL" -> ElectricNeoTokens.CyberCrimson
                                    "WARNING" -> ElectricNeoTokens.SolarAmber
                                    "SUCCESS" -> ElectricNeoTokens.NeonEmerald
                                    else -> ElectricNeoTokens.ElectricCyan
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(ElectricNeoTokens.RadiusSmall))
                                        .background(ElectricNeoTokens.CyberSurfaceCard)
                                        .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(ElectricNeoTokens.RadiusSmall))
                                        .clickable {
                                            activePopupId = popup.id
                                            isDrawerOpen = false
                                        }
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(accent.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(popup.icon, fontSize = 16.sp)
                                        }
                                        Column {
                                            Text(
                                                text = "${popup.id}. ${popup.title}",
                                                color = ElectricNeoTokens.TextWhite,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "${popup.alertLevel} • ${popup.triggerContext}",
                                                color = accent,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }

                                    Text(
                                        text = "Trigger ⚡",
                                        color = accent,
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
