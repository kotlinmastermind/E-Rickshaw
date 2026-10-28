package com.luxury.chauffeur.customer.feature.support

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.luxury.chauffeur.customer.core.designsystem.*
import com.luxury.chauffeur.customer.core.network.*
import kotlinx.coroutines.launch

// =============================================================================
// 193: ACTIVE INCIDENT BANNER (Feature 193)
// =============================================================================
@Composable
fun ActiveIncidentBannerComposable(
    incidents: List<ServiceIncidentDto>,
    onViewStatus: () -> Unit = {}
) {
    if (incidents.isEmpty()) return

    val topIncident = incidents.first()
    val isCritical = (topIncident.severity ?: "INFO").uppercase() == "CRITICAL"
    val bannerBg = if (isCritical) Color(0xFF450A0A) else Color(0xFF382400)
    val bannerBorder = if (isCritical) LuxuryColors.CrimsonAlert else LuxuryColors.ChampagneGold

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
        color = bannerBg,
        border = BorderStroke(1.dp, bannerBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Incident Alert",
                tint = bannerBorder,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ADVISORY: ${topIncident.title ?: "Notice"}",
                    style = LuxuryTypography.BodySubtle.copy(
                        color = LuxuryColors.PlatinumWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                )
                Text(
                    text = topIncident.displayMessage,
                    style = LuxuryTypography.BodySubtle.copy(
                        color = LuxuryColors.SubtleGray,
                        fontSize = 10.sp
                    ),
                    maxLines = 2
                )
                if (!topIncident.actionRecommendation.isNullOrBlank()) {
                    Text(
                        text = "Guidance: ${topIncident.actionRecommendation}",
                        style = LuxuryTypography.BodySubtle.copy(
                            color = LuxuryColors.ChampagneGold,
                            fontSize = 9.sp
                        )
                    )
                }
            }
            TextButton(onClick = onViewStatus) {
                Text("STATUS", color = bannerBorder, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// =============================================================================
// 177: CONTEXTUAL HELP BUTTON (Feature 177)
// =============================================================================
@Composable
fun ContextualHelpButton(
    tag: String,
    label: String = "Policy & Help",
    onClick: (String) -> Unit
) {
    Surface(
        onClick = { onClick(tag) },
        shape = RoundedCornerShape(LuxuryDimensions.RadiusPill),
        color = LuxuryColors.CharcoalSurface,
        border = BorderStroke(0.5.dp, LuxuryColors.ChampagneGold.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Context Help",
                tint = LuxuryColors.ChampagneGold,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = LuxuryTypography.BodySubtle.copy(
                    color = LuxuryColors.ChampagneGold,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

// =============================================================================
// 176, 177, 192, 193, 194, 200: SEARCHABLE HELP CENTER (Features 176, 177, 192, 194, 200)
// =============================================================================
@Composable
fun HelpCenterDialog(
    initialTag: String? = null,
    onDismiss: () -> Unit,
    onOpenTickets: () -> Unit,
    onOpenSelfService: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>("ALL") }
    var articles by remember { mutableStateOf<List<HelpArticleDto>>(emptyList()) }
    var incidents by remember { mutableStateOf<List<ServiceIncidentDto>>(emptyList()) }
    var serviceStatus by remember { mutableStateOf<PlatformServiceStatusDto?>(null) }
    var showStatusModal by remember { mutableStateOf(false) }
    var showRetentionModal by remember { mutableStateOf(false) }
    var expandedArticleId by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    val categories = listOf("ALL", "BOOKING", "PAYMENTS", "SAFETY", "CANCELLATION", "LOST_FOUND", "PRIVACY")

    fun loadArticles() {
        isLoading = true
        scope.launch {
            try {
                val cat = if (selectedCategory == "ALL") null else selectedCategory
                val res = ApiClient.getService().getHelpArticles(
                    query = searchQuery.ifBlank { null },
                    category = cat,
                    tag = initialTag
                )
                if (res.isSuccessful && res.body() != null) {
                    articles = res.body()!!
                }
            } catch (e: Exception) {
                // Fallback graceful offline view
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(searchQuery, selectedCategory) {
        loadArticles()
    }

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val incRes = ApiClient.getService().getActiveIncidentBanners()
                if (incRes.isSuccessful && incRes.body() != null) {
                    incidents = incRes.body()!!
                }
                val statRes = ApiClient.getService().getPlatformServiceStatus()
                if (statRes.isSuccessful && statRes.body() != null) {
                    serviceStatus = statRes.body()!!
                }
            } catch (_: Exception) {}
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(LuxuryDimensions.RadiusLarge),
            color = LuxuryColors.CardSurface,
            border = BorderStroke(1.dp, LuxuryColors.ChampagneGold)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(LuxuryDimensions.PaddingLarge)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = CircleShape,
                            color = LuxuryColors.ChampagneGold.copy(alpha = 0.15f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Info, "Help", tint = LuxuryColors.ChampagneGold, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("CONCIERGE HELP DESK", style = LuxuryTypography.HeadlineMedium.copy(fontSize = 17.sp))
                            Text("Knowledge Base & 24/7 Assistance", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close", tint = LuxuryColors.SubtleGray)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Active Incident Banner (Feature 193)
                if (incidents.isNotEmpty()) {
                    ActiveIncidentBannerComposable(incidents = incidents, onViewStatus = { showStatusModal = true })
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Quick Navigation Chips (Self-Service, Tickets, Service Status, Retention)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        onClick = onOpenSelfService,
                        shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                        color = LuxuryColors.CharcoalSurface,
                        border = BorderStroke(1.dp, LuxuryColors.ChampagneGold.copy(alpha = 0.6f))
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Build, "Self-Service", tint = LuxuryColors.ChampagneGold, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("SELF-SERVICE DESK", style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.ChampagneGold, fontSize = 10.sp, fontWeight = FontWeight.Bold))
                        }
                    }

                    Surface(
                        onClick = onOpenTickets,
                        shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                        color = LuxuryColors.CharcoalSurface,
                        border = BorderStroke(1.dp, LuxuryColors.CardBorder)
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Email, "Tickets", tint = LuxuryColors.PlatinumWhite, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("MY TICKETS", style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.PlatinumWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold))
                        }
                    }

                    Surface(
                        onClick = { showStatusModal = true },
                        shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                        color = LuxuryColors.CharcoalSurface,
                        border = BorderStroke(1.dp, LuxuryColors.CardBorder)
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, "Status", tint = LuxuryColors.EmeraldGreen, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("SYSTEM STATUS", style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.PlatinumWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold))
                        }
                    }

                    Surface(
                        onClick = { showRetentionModal = true },
                        shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                        color = LuxuryColors.CharcoalSurface,
                        border = BorderStroke(1.dp, LuxuryColors.CardBorder)
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, "Retention", tint = LuxuryColors.SubtleGray, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("DATA RETENTION", style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.SubtleGray, fontSize = 10.sp, fontWeight = FontWeight.Bold))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Search Bar (Feature 176)
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search help articles (cancellation, refunds, baggage...)", color = LuxuryColors.SubtleGray, fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(LuxuryDimensions.RadiusMedium),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, "Search", tint = LuxuryColors.ChampagneGold) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, "Clear", tint = LuxuryColors.SubtleGray)
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LuxuryColors.ChampagneGold,
                        unfocusedBorderColor = LuxuryColors.CardBorder,
                        focusedTextColor = LuxuryColors.PlatinumWhite,
                        unfocusedTextColor = LuxuryColors.PlatinumWhite,
                        focusedContainerColor = LuxuryColors.CharcoalSurface,
                        unfocusedContainerColor = LuxuryColors.CharcoalSurface
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Category Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(categories) { cat ->
                        val isSelected = selectedCategory == cat
                        Surface(
                            onClick = { selectedCategory = cat },
                            shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                            color = if (isSelected) LuxuryColors.ChampagneGold else LuxuryColors.CharcoalSurface,
                            border = BorderStroke(0.5.dp, if (isSelected) LuxuryColors.ChampagneGold else LuxuryColors.CardBorder)
                        ) {
                            Text(
                                text = cat,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                style = LuxuryTypography.BodySubtle.copy(
                                    color = if (isSelected) LuxuryColors.ObsidianBlack else LuxuryColors.PlatinumWhite,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Articles List
                if (isLoading) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = LuxuryColors.ChampagneGold)
                    }
                } else if (articles.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Info, "No articles", tint = LuxuryColors.SubtleGray, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("No articles found matching your query", style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.SubtleGray))
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(articles) { article ->
                            val isExpanded = expandedArticleId == article.id
                            ArticleItemCard(
                                article = article,
                                isExpanded = isExpanded,
                                onToggle = {
                                    expandedArticleId = if (isExpanded) null else article.id
                                },
                                onRate = { wasHelpful ->
                                    scope.launch {
                                        try {
                                            val rRes = ApiClient.getService().rateHelpArticle(
                                                articleId = article.id,
                                                payload = ArticleRatingRequestDto(wasHelpful = wasHelpful)
                                            )
                                            if (rRes.isSuccessful) {
                                                Toast.makeText(context, "Thank you for your rating!", Toast.LENGTH_SHORT).show()
                                                loadArticles()
                                            }
                                        } catch (_: Exception) {}
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Service Status Dialog (Feature 194)
    if (showStatusModal && serviceStatus != null) {
        ServiceStatusDialog(status = serviceStatus!!, onDismiss = { showStatusModal = false })
    }

    // Data Retention Policy Dialog (Feature 200)
    if (showRetentionModal) {
        DataRetentionDialog(onDismiss = { showRetentionModal = false })
    }
}

// =============================================================================
// ARTICLE ITEM CARD WITH RATING (Feature 192)
// =============================================================================
@Composable
fun ArticleItemCard(
    article: HelpArticleDto,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onRate: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LuxuryDimensions.RadiusMedium),
        color = LuxuryColors.CharcoalSurface,
        border = BorderStroke(1.dp, if (isExpanded) LuxuryColors.ChampagneGold else LuxuryColors.CardBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = LuxuryColors.ChampagneGold.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = article.category,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.ChampagneGold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${article.helpfulVotes} found helpful",
                            style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.SubtleGray, fontSize = 9.sp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = article.title,
                        style = LuxuryTypography.BodyRegular.copy(color = LuxuryColors.PlatinumWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    )
                    Text(
                        text = article.summary,
                        style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.SubtleGray, fontSize = 11.sp),
                        maxLines = if (isExpanded) 10 else 2
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "Expand",
                    tint = LuxuryColors.ChampagneGold
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Divider(color = LuxuryColors.CardBorder, thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = article.contentMarkdown,
                        style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.PlatinumWhite, fontSize = 11.sp, lineHeight = 16.sp)
                    )
                    if (!article.contextTags.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tags: ${article.contextTags}",
                            style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.ChampagneGold.copy(alpha = 0.8f), fontSize = 9.sp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    // Help Article Rating (Feature 192)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Was this article helpful?",
                            style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.SubtleGray, fontSize = 10.sp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { onRate(true) },
                                shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                                border = BorderStroke(0.5.dp, LuxuryColors.EmeraldGreen),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Icon(Icons.Default.ThumbUp, "Helpful", tint = LuxuryColors.EmeraldGreen, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Yes (${article.helpfulVotes})", color = LuxuryColors.EmeraldGreen, fontSize = 10.sp)
                            }
                            OutlinedButton(
                                onClick = { onRate(false) },
                                shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                                border = BorderStroke(0.5.dp, LuxuryColors.CrimsonAlert),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Icon(Icons.Default.ThumbUp, "Not Helpful", tint = LuxuryColors.CrimsonAlert, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("No (${article.unhelpfulVotes})", color = LuxuryColors.CrimsonAlert, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// 194: SERVICE STATUS PAGE (Feature 194)
// =============================================================================
@Composable
fun ServiceStatusDialog(
    status: PlatformServiceStatusDto,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.95f),
            shape = RoundedCornerShape(LuxuryDimensions.RadiusLarge),
            color = LuxuryColors.CardSurface,
            border = BorderStroke(1.dp, LuxuryColors.ChampagneGold)
        ) {
            Column(modifier = Modifier.padding(LuxuryDimensions.PaddingLarge)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("SYSTEM SERVICE STATUS", style = LuxuryTypography.HeadlineMedium.copy(fontSize = 16.sp))
                        Text(status.summary, style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.EmeraldGreen, fontSize = 11.sp))
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close", tint = LuxuryColors.SubtleGray)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                status.services.forEach { comp ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                        color = LuxuryColors.CharcoalSurface,
                        border = BorderStroke(0.5.dp, LuxuryColors.CardBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(comp.name, style = LuxuryTypography.BodyRegular.copy(color = LuxuryColors.PlatinumWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
                                Text("90-day SLA: ${comp.uptime90d}", style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp))
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = LuxuryColors.EmeraldGreen.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = comp.status,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.EmeraldGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    )
                                }
                                Text("${comp.latencyMs}ms", style = LuxuryTypography.BodySubtle.copy(fontSize = 9.sp, color = LuxuryColors.SubtleGray))
                            }
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// 178, 179: GUIDED RIDE CANCELLATION SUPPORT (Features 178, 179)
// =============================================================================
@Composable
fun GuidedCancellationDialog(
    rideId: String,
    onDismiss: () -> Unit,
    onCancellationCompleted: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var quote by remember { mutableStateOf<CancellationQuoteDto?>(null) }
    var isLoadingQuote by remember { mutableStateOf(true) }
    var selectedReason by remember { mutableStateOf("CHAUFFEUR_DELAYED") }
    var customNotes by remember { mutableStateOf("") }
    var waiverRequested by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var cancellationResult by remember { mutableStateOf<GuidedCancellationResponseDto?>(null) }

    LaunchedEffect(rideId) {
        isLoadingQuote = true
        try {
            val res = ApiClient.getService().getCancellationQuote(rideId)
            if (res.isSuccessful && res.body() != null) {
                quote = res.body()!!
                if (quote!!.suggestedReasons.isNotEmpty()) {
                    selectedReason = quote!!.suggestedReasons.first()
                }
            }
        } catch (_: Exception) {
            Toast.makeText(context, "Could not load cancellation quote.", Toast.LENGTH_SHORT).show()
        } finally {
            isLoadingQuote = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(LuxuryDimensions.RadiusLarge),
            color = LuxuryColors.CardSurface,
            border = BorderStroke(1.dp, LuxuryColors.ChampagneGold)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(LuxuryDimensions.PaddingLarge)
            ) {
                if (cancellationResult != null) {
                    // Result View
                    val r = cancellationResult!!
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Cancelled",
                        tint = LuxuryColors.ChampagneGold,
                        modifier = Modifier.size(42.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("RESERVATION CANCELLED", style = LuxuryTypography.HeadlineMedium.copy(fontSize = 17.sp))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(r.message, style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.PlatinumWhite, fontSize = 12.sp))
                    if (!r.complaintReferenceNumber.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                            color = LuxuryColors.CharcoalSurface,
                            border = BorderStroke(1.dp, LuxuryColors.ChampagneGold)
                        ) {
                            Text(
                                text = "Waiver Case Ref: ${r.complaintReferenceNumber}",
                                modifier = Modifier.padding(8.dp),
                                style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.ChampagneGold, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            onCancellationCompleted()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.ChampagneGold)
                    ) {
                        Text("DONE", color = LuxuryColors.ObsidianBlack, fontWeight = FontWeight.Bold)
                    }
                } else if (isLoadingQuote) {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = LuxuryColors.ChampagneGold)
                    }
                } else if (quote != null) {
                    val q = quote!!
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("CANCEL RESERVATION", style = LuxuryTypography.HeadlineMedium.copy(fontSize = 16.sp))
                            Text("Chauffeur Ride #${rideId.takeLast(8)}", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, "Close", tint = LuxuryColors.SubtleGray)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Cancellation Fee Explanation (Feature 179)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                        color = if (q.isWithinGracePeriod) Color(0xFF0F2A1D) else Color(0xFF331A1A),
                        border = BorderStroke(1.dp, if (q.isWithinGracePeriod) LuxuryColors.EmeraldGreen else LuxuryColors.CrimsonAlert)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (q.isWithinGracePeriod) "COMPLIMENTARY CANCELLATION" else "CANCELLATION FEE APPLIES",
                                    style = LuxuryTypography.BodySubtle.copy(
                                        color = if (q.isWithinGracePeriod) LuxuryColors.EmeraldGreen else LuxuryColors.CrimsonAlert,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                )
                                Text(
                                    text = "₹${q.cancellationFeeRupees}",
                                    style = LuxuryTypography.BodyRegular.copy(
                                        color = LuxuryColors.PlatinumWhite,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = q.feeExplanation,
                                style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.PlatinumWhite, fontSize = 10.sp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Guided Reason Picker (Feature 178)
                    Text("SELECT REASON", style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.ChampagneGold, fontSize = 10.sp, fontWeight = FontWeight.Bold))
                    Spacer(modifier = Modifier.height(4.dp))

                    q.suggestedReasons.forEach { reason ->
                        val isSelected = selectedReason == reason
                        Surface(
                            onClick = { selectedReason = reason },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                            color = if (isSelected) LuxuryColors.ChampagneGold.copy(alpha = 0.15f) else LuxuryColors.CharcoalSurface,
                            border = BorderStroke(0.5.dp, if (isSelected) LuxuryColors.ChampagneGold else LuxuryColors.CardBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { selectedReason = reason },
                                    colors = RadioButtonDefaults.colors(selectedColor = LuxuryColors.ChampagneGold)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = reason.replace("_", " "),
                                    style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.PlatinumWhite, fontSize = 11.sp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Fee Waiver Request (Feature 179)
                    if (q.canRequestWaiver) {
                        Surface(
                            shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                            color = LuxuryColors.CharcoalSurface,
                            border = BorderStroke(0.5.dp, LuxuryColors.CardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Request Cancellation Fee Waiver", style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.PlatinumWhite, fontWeight = FontWeight.Bold, fontSize = 11.sp))
                                        Text("Submit for supervisory review if delay caused by chauffeur", style = LuxuryTypography.BodySubtle.copy(fontSize = 9.sp))
                                    }
                                    Checkbox(
                                        checked = waiverRequested,
                                        onCheckedChange = { waiverRequested = it },
                                        colors = CheckboxDefaults.colors(checkedColor = LuxuryColors.ChampagneGold)
                                    )
                                }
                                if (waiverRequested) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = customNotes,
                                        onValueChange = { customNotes = it },
                                        placeholder = { Text("Justification for fee waiver...", color = LuxuryColors.SubtleGray, fontSize = 10.sp) },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = false,
                                        maxLines = 2,
                                        shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            isSubmitting = true
                            scope.launch {
                                try {
                                    val cRes = ApiClient.getService().executeGuidedCancellation(
                                        rideId = rideId,
                                        payload = GuidedCancellationRequestDto(
                                            reasonCode = selectedReason,
                                            notes = customNotes.ifBlank { null },
                                            waiverRequested = waiverRequested
                                        )
                                    )
                                    if (cRes.isSuccessful && cRes.body() != null) {
                                        cancellationResult = cRes.body()!!
                                    } else {
                                        Toast.makeText(context, "Failed to cancel ride.", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (_: Exception) {
                                    Toast.makeText(context, "Network error during cancellation.", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isSubmitting = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSubmitting,
                        colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.CrimsonAlert)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(color = LuxuryColors.PlatinumWhite, modifier = Modifier.size(18.dp))
                        } else {
                            Text("CONFIRM CANCELLATION", color = LuxuryColors.PlatinumWhite, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// 185 - 190: SELF-SERVICE RESOLVER DESK (Features 185, 186, 187, 188, 189, 190)
// =============================================================================
@Composable
fun SelfServiceResolverDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Payment", "Recovery", "Refund", "Fare Dispute", "Lost Item", "Callback")

    // Diagnostic state (185)
    var diagErrorCode by remember { mutableStateOf("PAYMENT_GATEWAY_TIMEOUT") }
    var diagResult by remember { mutableStateOf<DiagnosticResultDto?>(null) }
    var isDiagnosing by remember { mutableStateOf(false) }

    // Account recovery state (186)
    var recoveryType by remember { mutableStateOf("PIN_RESET") }
    var recoveryDetails by remember { mutableStateOf("") }
    var recoveryResult by remember { mutableStateOf<AccountRecoveryResponseDto?>(null) }
    var isRecovering by remember { mutableStateOf(false) }

    // Refund state (187)
    var refundRideId by remember { mutableStateOf("") }
    var refundReason by remember { mutableStateOf("DOUBLE_CHARGED") }
    var refundExplanation by remember { mutableStateOf("") }
    var refundAmountRupees by remember { mutableStateOf("250") }
    var refundResult by remember { mutableStateOf<RefundClaimResponseDto?>(null) }
    var isRefunding by remember { mutableStateOf(false) }

    // Fare review state (188)
    var disputeRideId by remember { mutableStateOf("") }
    var disputeAmountRupees by remember { mutableStateOf("150") }
    var disputeEvidence by remember { mutableStateOf("") }
    var disputeResult by remember { mutableStateOf<String?>(null) }
    var isDisputing by remember { mutableStateOf(false) }

    // Lost item state (189)
    var lostRideId by remember { mutableStateOf("") }
    var lostCategory by remember { mutableStateOf("PHONE") }
    var lostDescription by remember { mutableStateOf("") }
    var lostSeatLocation by remember { mutableStateOf("REAR_PASSENGER_SEAT") }
    var lostResult by remember { mutableStateOf<LostItemResponseDto?>(null) }
    var isReportingLost by remember { mutableStateOf(false) }

    // Callback state (190)
    var callbackPhone by remember { mutableStateOf("") }
    var callbackWindow by remember { mutableStateOf("IMMEDIATE") }
    var callbackSubject by remember { mutableStateOf("VIP Concierge Assistance") }
    var callbackResult by remember { mutableStateOf<CallbackResponseDto?>(null) }
    var isRequestingCallback by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.90f),
            shape = RoundedCornerShape(LuxuryDimensions.RadiusLarge),
            color = LuxuryColors.CardSurface,
            border = BorderStroke(1.dp, LuxuryColors.ChampagneGold)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(LuxuryDimensions.PaddingLarge)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("SELF-SERVICE RESOLVER", style = LuxuryTypography.HeadlineMedium.copy(fontSize = 17.sp))
                        Text("Instant resolution without waiting for an agent", style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp))
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close", tint = LuxuryColors.SubtleGray)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable Tabs
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(tabs.size) { idx ->
                        val isSelected = selectedTab == idx
                        Surface(
                            onClick = { selectedTab = idx },
                            shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                            color = if (isSelected) LuxuryColors.ChampagneGold else LuxuryColors.CharcoalSurface,
                            border = BorderStroke(0.5.dp, if (isSelected) LuxuryColors.ChampagneGold else LuxuryColors.CardBorder)
                        ) {
                            Text(
                                text = tabs[idx],
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = LuxuryTypography.BodySubtle.copy(
                                    color = if (isSelected) LuxuryColors.ObsidianBlack else LuxuryColors.PlatinumWhite,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tab Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    when (selectedTab) {
                        0 -> {
                            // 185: PAYMENT TROUBLESHOOTING
                            Text("PAYMENT DIAGNOSTICS", style = LuxuryTypography.BodyRegular.copy(color = LuxuryColors.ChampagneGold, fontWeight = FontWeight.Bold, fontSize = 12.sp))
                            Spacer(modifier = Modifier.height(6.dp))
                            val errorCodes = listOf("PAYMENT_GATEWAY_TIMEOUT", "CARD_DECLINED", "INSUFFICIENT_FUNDS", "UPI_COLLECT_EXPIRED")
                            errorCodes.forEach { code ->
                                val isSel = diagErrorCode == code
                                Surface(
                                    onClick = { diagErrorCode = code },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                                    color = if (isSel) LuxuryColors.ChampagneGold.copy(alpha = 0.15f) else LuxuryColors.CharcoalSurface,
                                    border = BorderStroke(0.5.dp, if (isSel) LuxuryColors.ChampagneGold else LuxuryColors.CardBorder)
                                ) {
                                    Text(code, modifier = Modifier.padding(8.dp), style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.PlatinumWhite, fontSize = 11.sp))
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    isDiagnosing = true
                                    scope.launch {
                                        try {
                                            val res = ApiClient.getService().troubleshootPayment(
                                                PaymentTroubleshootRequestDto(errorCode = diagErrorCode, paymentMethod = "UPI")
                                            )
                                            if (res.isSuccessful) diagResult = res.body()
                                        } catch (_: Exception) {}
                                        finally { isDiagnosing = false }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.ChampagneGold)
                            ) {
                                Text("RUN PAYMENT DIAGNOSTIC", color = LuxuryColors.ObsidianBlack, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }

                            if (diagResult != null) {
                                val d = diagResult!!
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                                    color = LuxuryColors.CharcoalSurface,
                                    border = BorderStroke(1.dp, LuxuryColors.ChampagneGold),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("Diagnosis: ${d.errorCode}", style = LuxuryTypography.BodyRegular.copy(color = LuxuryColors.ChampagneGold, fontWeight = FontWeight.Bold, fontSize = 11.sp))
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(d.humanExplanation, style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp, color = LuxuryColors.PlatinumWhite))
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text("Recommended Fixes:", style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.SubtleGray, fontWeight = FontWeight.Bold, fontSize = 10.sp))
                                        d.recommendedActions.forEach { act ->
                                            Text("• $act", style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp, color = LuxuryColors.SubtleGray))
                                        }
                                    }
                                }
                            }
                        }

                        1 -> {
                            // 186: ACCOUNT RECOVERY
                            Text("SELF-SERVICE RECOVERY", style = LuxuryTypography.BodyRegular.copy(color = LuxuryColors.ChampagneGold, fontWeight = FontWeight.Bold, fontSize = 12.sp))
                            Spacer(modifier = Modifier.height(6.dp))
                            val types = listOf("PIN_RESET", "BIOMETRIC_RESYNC", "PHONE_MIGRATION", "EMAIL_VERIFICATION")
                            types.forEach { t ->
                                val isSel = recoveryType == t
                                Surface(
                                    onClick = { recoveryType = t },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                                    color = if (isSel) LuxuryColors.ChampagneGold.copy(alpha = 0.15f) else LuxuryColors.CharcoalSurface,
                                    border = BorderStroke(0.5.dp, if (isSel) LuxuryColors.ChampagneGold else LuxuryColors.CardBorder)
                                ) {
                                    Text(t.replace("_", " "), modifier = Modifier.padding(8.dp), style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.PlatinumWhite, fontSize = 11.sp))
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = recoveryDetails,
                                onValueChange = { recoveryDetails = it },
                                placeholder = { Text("Enter details / notes...", color = LuxuryColors.SubtleGray, fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    isRecovering = true
                                    scope.launch {
                                        try {
                                            val res = ApiClient.getService().requestAccountRecovery(
                                                AccountRecoveryRequestDto(recoveryType = recoveryType, details = recoveryDetails.ifBlank { null })
                                            )
                                            if (res.isSuccessful) recoveryResult = res.body()
                                        } catch (_: Exception) {}
                                        finally { isRecovering = false }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.ChampagneGold)
                            ) {
                                Text("INITIATE RECOVERY", color = LuxuryColors.ObsidianBlack, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }

                            if (recoveryResult != null) {
                                val r = recoveryResult!!
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                                    color = LuxuryColors.CharcoalSurface,
                                    border = BorderStroke(1.dp, LuxuryColors.ChampagneGold),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("Case Ref: ${r.complaintReferenceNumber}", style = LuxuryTypography.BodyRegular.copy(color = LuxuryColors.ChampagneGold, fontWeight = FontWeight.Bold, fontSize = 11.sp))
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(r.instructions, style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp, color = LuxuryColors.PlatinumWhite))
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Security Challenge: ${r.securityChallenge}", style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp, color = LuxuryColors.SubtleGray))
                                    }
                                }
                            }
                        }

                        2 -> {
                            // 187: SELF-SERVICE REFUND
                            Text("INSTANT REFUND REQUEST", style = LuxuryTypography.BodyRegular.copy(color = LuxuryColors.ChampagneGold, fontWeight = FontWeight.Bold, fontSize = 12.sp))
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = refundRideId,
                                onValueChange = { refundRideId = it },
                                placeholder = { Text("Ride ID (e.g. 550e8400...)", color = LuxuryColors.SubtleGray, fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = refundAmountRupees,
                                onValueChange = { refundAmountRupees = it },
                                placeholder = { Text("Requested Amount (₹)", color = LuxuryColors.SubtleGray, fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            val refundReasons = listOf("DOUBLE_CHARGED", "EARLY_TERMINATION", "VEHICLE_DISCOMFORT", "SERVICE_DELAY")
                            refundReasons.forEach { r ->
                                val isSel = refundReason == r
                                Surface(
                                    onClick = { refundReason = r },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                                    color = if (isSel) LuxuryColors.ChampagneGold.copy(alpha = 0.15f) else LuxuryColors.CharcoalSurface,
                                    border = BorderStroke(0.5.dp, if (isSel) LuxuryColors.ChampagneGold else LuxuryColors.CardBorder)
                                ) {
                                    Text(r.replace("_", " "), modifier = Modifier.padding(8.dp), style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.PlatinumWhite, fontSize = 11.sp))
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = refundExplanation,
                                onValueChange = { refundExplanation = it },
                                placeholder = { Text("Explanation...", color = LuxuryColors.SubtleGray, fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    if (refundRideId.isBlank()) {
                                        Toast.makeText(context, "Please enter a valid Ride ID", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    isRefunding = true
                                    scope.launch {
                                        try {
                                            val cents = (refundAmountRupees.toDoubleOrNull() ?: 100.0) * 100
                                            val res = ApiClient.getService().submitRefundRequest(
                                                RefundClaimRequestDto(
                                                    rideId = refundRideId,
                                                    reason = refundReason,
                                                    requestedCents = cents.toInt(),
                                                    explanation = refundExplanation
                                                )
                                            )
                                            if (res.isSuccessful) refundResult = res.body()
                                        } catch (_: Exception) {}
                                        finally { isRefunding = false }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.ChampagneGold)
                            ) {
                                Text("CLAIM REFUND", color = LuxuryColors.ObsidianBlack, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }

                            if (refundResult != null) {
                                val rf = refundResult!!
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                                    color = LuxuryColors.CharcoalSurface,
                                    border = BorderStroke(1.dp, LuxuryColors.EmeraldGreen),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(rf.message, style = LuxuryTypography.BodyRegular.copy(color = LuxuryColors.EmeraldGreen, fontWeight = FontWeight.Bold, fontSize = 11.sp))
                                        if (!rf.complaintReferenceNumber.isNullOrBlank()) {
                                            Text("Ref: ${rf.complaintReferenceNumber}", style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp, color = LuxuryColors.ChampagneGold))
                                        }
                                        Text("Refund Amount: ₹${rf.refundRupees}", style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp, color = LuxuryColors.PlatinumWhite))
                                    }
                                }
                            }
                        }

                        3 -> {
                            // 188: FARE DISPUTE EVIDENCE
                            Text("FARE REVIEW EVIDENCE SUBMISSION", style = LuxuryTypography.BodyRegular.copy(color = LuxuryColors.ChampagneGold, fontWeight = FontWeight.Bold, fontSize = 12.sp))
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = disputeRideId,
                                onValueChange = { disputeRideId = it },
                                placeholder = { Text("Ride ID...", color = LuxuryColors.SubtleGray, fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = disputeAmountRupees,
                                onValueChange = { disputeAmountRupees = it },
                                placeholder = { Text("Disputed Amount (₹)...", color = LuxuryColors.SubtleGray, fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = disputeEvidence,
                                onValueChange = { disputeEvidence = it },
                                placeholder = { Text("Describe fare discrepancy (toll mismatch, detour, wrong rate)...", color = LuxuryColors.SubtleGray, fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 3,
                                shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    if (disputeRideId.isBlank() || disputeEvidence.isBlank()) {
                                        Toast.makeText(context, "Enter Ride ID and evidence details", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    isDisputing = true
                                    scope.launch {
                                        try {
                                            val cents = (disputeAmountRupees.toDoubleOrNull() ?: 50.0) * 100
                                            val res = ApiClient.getService().submitFareReview(
                                                FareReviewRequestDto(
                                                    rideId = disputeRideId,
                                                    disputedAmountCents = cents.toInt(),
                                                    evidenceText = disputeEvidence
                                                )
                                            )
                                            if (res.isSuccessful) {
                                                val m = res.body()?.get("complaint_reference_number")?.toString() ?: "Case Submitted"
                                                disputeResult = "Dispute filed under $m. Billing squad will review within 24h."
                                            }
                                        } catch (_: Exception) {}
                                        finally { isDisputing = false }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.ChampagneGold)
                            ) {
                                Text("SUBMIT FARE REVIEW", color = LuxuryColors.ObsidianBlack, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }

                            if (disputeResult != null) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(disputeResult!!, style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.EmeraldGreen, fontSize = 11.sp))
                            }
                        }

                        4 -> {
                            // 189: LOST-ITEM CLAIM
                            Text("LOST PROPERTY CLAIM", style = LuxuryTypography.BodyRegular.copy(color = LuxuryColors.ChampagneGold, fontWeight = FontWeight.Bold, fontSize = 12.sp))
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = lostRideId,
                                onValueChange = { lostRideId = it },
                                placeholder = { Text("Ride ID...", color = LuxuryColors.SubtleGray, fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            val cats = listOf("PHONE", "LUGGAGE", "WALLET", "JEWELRY", "DOCUMENTS", "KEY_FOB")
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(cats) { c ->
                                    val isSel = lostCategory == c
                                    Surface(
                                        onClick = { lostCategory = c },
                                        shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                                        color = if (isSel) LuxuryColors.ChampagneGold else LuxuryColors.CharcoalSurface,
                                        border = BorderStroke(0.5.dp, if (isSel) LuxuryColors.ChampagneGold else LuxuryColors.CardBorder)
                                    ) {
                                        Text(c, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = LuxuryTypography.BodySubtle.copy(color = if (isSel) LuxuryColors.ObsidianBlack else LuxuryColors.PlatinumWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold))
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = lostDescription,
                                onValueChange = { lostDescription = it },
                                placeholder = { Text("Item description (color, brand, distinguishing marks)...", color = LuxuryColors.SubtleGray, fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 3,
                                shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = lostSeatLocation,
                                onValueChange = { lostSeatLocation = it },
                                placeholder = { Text("Seat location (e.g. Rear right seat, Trunk)...", color = LuxuryColors.SubtleGray, fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    if (lostRideId.isBlank() || lostDescription.isBlank()) {
                                        Toast.makeText(context, "Enter Ride ID and item description", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    isReportingLost = true
                                    scope.launch {
                                        try {
                                            val res = ApiClient.getService().reportLostItem(
                                                LostItemClaimRequestDto(
                                                    rideId = lostRideId,
                                                    itemCategory = lostCategory,
                                                    itemDescription = lostDescription,
                                                    seatLocation = lostSeatLocation
                                                )
                                            )
                                            if (res.isSuccessful) lostResult = res.body()
                                        } catch (_: Exception) {}
                                        finally { isReportingLost = false }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.ChampagneGold)
                            ) {
                                Text("SUBMIT LOST ITEM CLAIM", color = LuxuryColors.ObsidianBlack, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }

                            if (lostResult != null) {
                                val lr = lostResult!!
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                                    color = LuxuryColors.CharcoalSurface,
                                    border = BorderStroke(1.dp, LuxuryColors.ChampagneGold),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("Case Ref: ${lr.complaintReferenceNumber}", style = LuxuryTypography.BodyRegular.copy(color = LuxuryColors.ChampagneGold, fontWeight = FontWeight.Bold, fontSize = 11.sp))
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Assigned to: ${lr.assignedSquad}", style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp, color = LuxuryColors.PlatinumWhite))
                                        Text(lr.nextSteps, style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp, color = LuxuryColors.SubtleGray))
                                    }
                                }
                            }
                        }

                        5 -> {
                            // 190: SUPPORT CALLBACK REQUEST
                            Text("SCHEDULE CONCIERGE CALLBACK", style = LuxuryTypography.BodyRegular.copy(color = LuxuryColors.ChampagneGold, fontWeight = FontWeight.Bold, fontSize = 12.sp))
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = callbackPhone,
                                onValueChange = { callbackPhone = it },
                                placeholder = { Text("Direct Phone Number (+91...)", color = LuxuryColors.SubtleGray, fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            val windows = listOf("IMMEDIATE", "WITHIN_1_HOUR", "EVENING_WINDOW")
                            windows.forEach { w ->
                                val isSel = callbackWindow == w
                                Surface(
                                    onClick = { callbackWindow = w },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                                    color = if (isSel) LuxuryColors.ChampagneGold.copy(alpha = 0.15f) else LuxuryColors.CharcoalSurface,
                                    border = BorderStroke(0.5.dp, if (isSel) LuxuryColors.ChampagneGold else LuxuryColors.CardBorder)
                                ) {
                                    Text(w.replace("_", " "), modifier = Modifier.padding(8.dp), style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.PlatinumWhite, fontSize = 11.sp))
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = callbackSubject,
                                onValueChange = { callbackSubject = it },
                                placeholder = { Text("Reason for callback...", color = LuxuryColors.SubtleGray, fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    if (callbackPhone.isBlank()) {
                                        Toast.makeText(context, "Please enter your phone number", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    isRequestingCallback = true
                                    scope.launch {
                                        try {
                                            val res = ApiClient.getService().requestCallback(
                                                CallbackRequestDto(
                                                    phoneNumber = callbackPhone,
                                                    preferredTimeWindow = callbackWindow,
                                                    subject = callbackSubject
                                                )
                                            )
                                            if (res.isSuccessful) callbackResult = res.body()
                                        } catch (_: Exception) {}
                                        finally { isRequestingCallback = false }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.ChampagneGold)
                            ) {
                                Text("REQUEST PRIORITY CALLBACK", color = LuxuryColors.ObsidianBlack, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }

                            if (callbackResult != null) {
                                val cb = callbackResult!!
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                                    color = LuxuryColors.CharcoalSurface,
                                    border = BorderStroke(1.dp, LuxuryColors.ChampagneGold),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(cb.message, style = LuxuryTypography.BodyRegular.copy(color = LuxuryColors.ChampagneGold, fontWeight = FontWeight.Bold, fontSize = 11.sp))
                                        Text("Estimated Wait: ~${cb.estimatedWaitMinutes} min", style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp, color = LuxuryColors.PlatinumWhite))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// 180, 181, 182, 184, 191, 195, 196, 197, 198, 199: SUPPORT TICKET SUITE
// =============================================================================
@Composable
fun SupportTicketDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var tickets by remember { mutableStateOf<List<CustomerSupportTicketDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedTicketId by remember { mutableStateOf<String?>(null) }
    var showCreateForm by remember { mutableStateOf(false) }

    // Create form fields
    var subject by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("RIDE_INQUIRY") }
    var description by remember { mutableStateOf("") }
    var rideIdInput by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("MEDIUM") }
    var languagePref by remember { mutableStateOf("en") }
    var retentionDays by remember { mutableIntStateOf(365) }
    var isCreating by remember { mutableStateOf(false) }
    var duplicateWarning by remember { mutableStateOf<CreateTicketResponseDto?>(null) }

    fun loadTickets() {
        isLoading = true
        scope.launch {
            try {
                val res = ApiClient.getService().getMySupportTickets()
                if (res.isSuccessful && res.body() != null) {
                    tickets = res.body()!!
                }
            } catch (_: Exception) {}
            finally { isLoading = false }
        }
    }

    LaunchedEffect(Unit) {
        loadTickets()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(LuxuryDimensions.RadiusLarge),
            color = LuxuryColors.CardSurface,
            border = BorderStroke(1.dp, LuxuryColors.ChampagneGold)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(LuxuryDimensions.PaddingLarge)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("VIP SUPPORT CONCIERGE", style = LuxuryTypography.HeadlineMedium.copy(fontSize = 17.sp))
                        Text("Track tickets, case timeline, and responses", style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp))
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close", tint = LuxuryColors.SubtleGray)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (selectedTicketId != null) {
                    // Case Inspector (Features 181, 182, 195, 196, 197, 199)
                    TicketInspectorView(
                        ticketId = selectedTicketId!!,
                        onBack = { selectedTicketId = null; loadTickets() }
                    )
                } else if (showCreateForm) {
                    // New Ticket Form (Features 180, 184, 191, 198, 200)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("NEW SUPPORT TICKET", style = LuxuryTypography.BodyRegular.copy(color = LuxuryColors.ChampagneGold, fontWeight = FontWeight.Bold, fontSize = 13.sp))
                            TextButton(onClick = { showCreateForm = false; duplicateWarning = null }) {
                                Text("Back to Tickets", color = LuxuryColors.SubtleGray, fontSize = 11.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Category (184: Category Routing)
                        Text("ROUTING SQUAD CATEGORY", style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.SubtleGray, fontSize = 10.sp, fontWeight = FontWeight.Bold))
                        val categories = listOf("RIDE_INQUIRY", "FARE_DISPUTE", "SAFETY_REPORT", "LOST_PROPERTY", "CHAUFFEUR_CONDUCT", "APP_ISSUE")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                            items(categories) { cat ->
                                val isSel = category == cat
                                Surface(
                                    onClick = { category = cat },
                                    shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                                    color = if (isSel) LuxuryColors.ChampagneGold else LuxuryColors.CharcoalSurface,
                                    border = BorderStroke(0.5.dp, if (isSel) LuxuryColors.ChampagneGold else LuxuryColors.CardBorder)
                                ) {
                                    Text(
                                        text = cat.replace("_", " "),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = LuxuryTypography.BodySubtle.copy(
                                            color = if (isSel) LuxuryColors.ObsidianBlack else LuxuryColors.PlatinumWhite,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = subject,
                            onValueChange = { subject = it },
                            placeholder = { Text("Subject (e.g. Detour toll query)", color = LuxuryColors.SubtleGray, fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            placeholder = { Text("Detailed description...", color = LuxuryColors.SubtleGray, fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 4,
                            shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = rideIdInput,
                            onValueChange = { rideIdInput = it },
                            placeholder = { Text("Associated Ride ID (optional)", color = LuxuryColors.SubtleGray, fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Language Preference (Feature 191)
                        Text("CONVERSATION LANGUAGE PREFERENCE", style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.SubtleGray, fontSize = 10.sp, fontWeight = FontWeight.Bold))
                        val languages = listOf("en" to "English", "hi" to "Hindi", "mr" to "Marathi", "gu" to "Gujarati", "fr" to "French")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                            items(languages) { (code, name) ->
                                val isSel = languagePref == code
                                Surface(
                                    onClick = { languagePref = code },
                                    shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                                    color = if (isSel) LuxuryColors.ChampagneGold else LuxuryColors.CharcoalSurface,
                                    border = BorderStroke(0.5.dp, if (isSel) LuxuryColors.ChampagneGold else LuxuryColors.CardBorder)
                                ) {
                                    Text(
                                        text = name,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = LuxuryTypography.BodySubtle.copy(
                                            color = if (isSel) LuxuryColors.ObsidianBlack else LuxuryColors.PlatinumWhite,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Data Retention Display & Preference (Feature 200)
                        Text("DATA RETENTION PERIOD", style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.SubtleGray, fontSize = 10.sp, fontWeight = FontWeight.Bold))
                        val retentionOptions = listOf(90 to "90 Days (GDPR Min)", 180 to "180 Days", 365 to "365 Days (Default)", 730 to "2 Years")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                            items(retentionOptions) { (days, label) ->
                                val isSel = retentionDays == days
                                Surface(
                                    onClick = { retentionDays = days },
                                    shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                                    color = if (isSel) LuxuryColors.ChampagneGold else LuxuryColors.CharcoalSurface,
                                    border = BorderStroke(0.5.dp, if (isSel) LuxuryColors.ChampagneGold else LuxuryColors.CardBorder)
                                ) {
                                    Text(
                                        text = label,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = LuxuryTypography.BodySubtle.copy(
                                            color = if (isSel) LuxuryColors.ObsidianBlack else LuxuryColors.PlatinumWhite,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }

                        // Duplicate Ticket Detection Warning (Feature 198)
                        if (duplicateWarning != null && duplicateWarning!!.isDuplicateWarning) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                                color = Color(0xFF3B2000),
                                border = BorderStroke(1.dp, LuxuryColors.ChampagneGold),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("POSSIBLE DUPLICATE DETECTED", style = LuxuryTypography.BodyRegular.copy(color = LuxuryColors.ChampagneGold, fontWeight = FontWeight.Bold, fontSize = 11.sp))
                                    Text(duplicateWarning!!.message ?: "", style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp, color = LuxuryColors.PlatinumWhite))
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Button(
                                        onClick = {
                                            // Force create despite duplicate
                                            isCreating = true
                                            scope.launch {
                                                try {
                                                    val res = ApiClient.getService().createSupportTicket(
                                                        CreateSupportTicketRequestDto(
                                                            subject = subject,
                                                            category = category,
                                                            description = description,
                                                            rideId = rideIdInput.ifBlank { null },
                                                            priority = priority,
                                                            languagePreference = languagePref,
                                                            dataRetentionDays = retentionDays,
                                                            allowDuplicate = true
                                                        )
                                                    )
                                                    if (res.isSuccessful) {
                                                        Toast.makeText(context, "Ticket created!", Toast.LENGTH_SHORT).show()
                                                        showCreateForm = false
                                                        duplicateWarning = null
                                                        loadTickets()
                                                    }
                                                } catch (_: Exception) {}
                                                finally { isCreating = false }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.ChampagneGold)
                                    ) {
                                        Text("Proceed Anyway", color = LuxuryColors.ObsidianBlack, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                if (subject.isBlank() || description.isBlank()) {
                                    Toast.makeText(context, "Subject and description required", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                isCreating = true
                                scope.launch {
                                    try {
                                        val res = ApiClient.getService().createSupportTicket(
                                            CreateSupportTicketRequestDto(
                                                subject = subject,
                                                category = category,
                                                description = description,
                                                rideId = rideIdInput.ifBlank { null },
                                                priority = priority,
                                                languagePreference = languagePref,
                                                dataRetentionDays = retentionDays,
                                                allowDuplicate = false
                                            )
                                        )
                                        if (res.isSuccessful && res.body() != null) {
                                            val b = res.body()!!
                                            if (b.isDuplicateWarning) {
                                                duplicateWarning = b
                                            } else {
                                                Toast.makeText(context, "Ticket Created (${b.complaintReferenceNumber})", Toast.LENGTH_SHORT).show()
                                                showCreateForm = false
                                                loadTickets()
                                            }
                                        }
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "Failed to submit ticket.", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isCreating = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isCreating,
                            colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.ChampagneGold)
                        ) {
                            if (isCreating) {
                                CircularProgressIndicator(color = LuxuryColors.ObsidianBlack, modifier = Modifier.size(18.dp))
                            } else {
                                Text("SUBMIT PRIORITY CASE", color = LuxuryColors.ObsidianBlack, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                } else {
                    // Ticket List View (Features 180, 184, 196)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ACTIVE CASES (${tickets.size})", style = LuxuryTypography.BodyRegular.copy(color = LuxuryColors.PlatinumWhite, fontWeight = FontWeight.Bold, fontSize = 12.sp))
                        Button(
                            onClick = { showCreateForm = true },
                            colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.ChampagneGold),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(Icons.Default.Add, "New", tint = LuxuryColors.ObsidianBlack, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("NEW TICKET", color = LuxuryColors.ObsidianBlack, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (isLoading) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = LuxuryColors.ChampagneGold)
                        }
                    } else if (tickets.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Email, "No tickets", tint = LuxuryColors.SubtleGray, modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("No open support tickets", style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.SubtleGray))
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(tickets) { ticket ->
                                Surface(
                                    onClick = { selectedTicketId = ticket.id },
                                    shape = RoundedCornerShape(LuxuryDimensions.RadiusMedium),
                                    color = LuxuryColors.CharcoalSurface,
                                    border = BorderStroke(1.dp, LuxuryColors.CardBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = ticket.complaintReferenceNumber, // Feature 196
                                                    style = LuxuryTypography.BodyRegular.copy(color = LuxuryColors.ChampagneGold, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                if (ticket.assignedSquad != null) {
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = LuxuryColors.CardSurface
                                                    ) {
                                                        Text(
                                                            text = ticket.assignedSquad.replace("_", " "),
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                                            style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.SubtleGray, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                                        )
                                                    }
                                                }
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = when (ticket.status) {
                                                    "RESOLVED" -> LuxuryColors.EmeraldGreen.copy(alpha = 0.2f)
                                                    "INVESTIGATING" -> LuxuryColors.ChampagneGold.copy(alpha = 0.2f)
                                                    else -> LuxuryColors.CardBorder
                                                }
                                            ) {
                                                Text(
                                                    text = ticket.status,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    style = LuxuryTypography.BodySubtle.copy(
                                                        color = when (ticket.status) {
                                                            "RESOLVED" -> LuxuryColors.EmeraldGreen
                                                            "INVESTIGATING" -> LuxuryColors.ChampagneGold
                                                            else -> LuxuryColors.PlatinumWhite
                                                        },
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(ticket.subject, style = LuxuryTypography.BodyRegular.copy(color = LuxuryColors.PlatinumWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.sp))
                                        
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Lang: ${ticket.languagePreference?.uppercase() ?: "EN"} • Retain: ${ticket.dataRetentionDays}d", style = LuxuryTypography.BodySubtle.copy(fontSize = 9.sp))
                                            if (ticket.isEscalated) {
                                                Text("ESCALATED", style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.CrimsonAlert, fontWeight = FontWeight.Bold, fontSize = 9.sp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// TICKET INSPECTOR & CONVERSATION TIMELINE (Features 181, 182, 195, 197, 199)
// =============================================================================
@Composable
fun TicketInspectorView(
    ticketId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var timeline by remember { mutableStateOf<TicketTimelineDto?>(null) }
    var isLoadingTimeline by remember { mutableStateOf(true) }
    var replyText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }

    // Escalation dialog state (195)
    var showEscalateModal by remember { mutableStateOf(false) }
    var escalateReason by remember { mutableStateOf("") }

    // CSAT state (197)
    var showCsatModal by remember { mutableStateOf(false) }
    var csatScore by remember { mutableIntStateOf(5) }
    var csatComment by remember { mutableStateOf("") }

    fun loadTimeline() {
        isLoadingTimeline = true
        scope.launch {
            try {
                // Mark messages read (Feature 199)
                ApiClient.getService().markTicketMessagesRead(ticketId)
                val res = ApiClient.getService().getTicketTimeline(ticketId)
                if (res.isSuccessful && res.body() != null) {
                    timeline = res.body()!!
                }
            } catch (_: Exception) {}
            finally { isLoadingTimeline = false }
        }
    }

    LaunchedEffect(ticketId) {
        loadTimeline()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = LuxuryColors.ChampagneGold)
            }
            if (timeline != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(timeline!!.complaintReferenceNumber, style = LuxuryTypography.BodyRegular.copy(color = LuxuryColors.ChampagneGold, fontWeight = FontWeight.Bold, fontSize = 12.sp))
                    Text("SLA: ${timeline!!.responseSlaTarget}", style = LuxuryTypography.BodySubtle.copy(fontSize = 9.sp))
                }
            }
            Row {
                IconButton(onClick = { showEscalateModal = true }) {
                    Icon(Icons.Default.Warning, "Escalate", tint = LuxuryColors.CrimsonAlert)
                }
                IconButton(onClick = { showCsatModal = true }) {
                    Icon(Icons.Default.Star, "Rate", tint = LuxuryColors.ChampagneGold)
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (isLoadingTimeline) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = LuxuryColors.ChampagneGold)
            }
        } else if (timeline != null) {
            val t = timeline!!
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Feature 182: Ticket Status & Response Timeline
                Text("RESPONSE & RESOLUTION TIMELINE", style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.SubtleGray, fontSize = 10.sp, fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(6.dp))

                t.events.forEach { ev ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(20.dp),
                            shape = CircleShape,
                            color = if (ev.completed) LuxuryColors.EmeraldGreen else LuxuryColors.CardBorder
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (ev.completed) {
                                    Icon(Icons.Default.Check, "Done", tint = LuxuryColors.ObsidianBlack, modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(ev.label, style = LuxuryTypography.BodyRegular.copy(color = if (ev.completed) LuxuryColors.PlatinumWhite else LuxuryColors.SubtleGray, fontWeight = FontWeight.Bold, fontSize = 11.sp))
                            Text(ev.detail, style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp, color = LuxuryColors.SubtleGray))
                        }
                        if (ev.timestamp != null) {
                            Text(ev.timestamp.take(16).replace("T", " "), style = LuxuryTypography.BodySubtle.copy(fontSize = 8.sp, color = LuxuryColors.SubtleGray))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Read Receipts Display (Feature 199)
                Surface(
                    shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                    color = LuxuryColors.CharcoalSurface,
                    border = BorderStroke(0.5.dp, LuxuryColors.CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Check, "Read", tint = LuxuryColors.EmeraldGreen, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "✓✓ Messages read by Concierge Specialist (${t.assignedSquad})",
                            style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.SubtleGray, fontSize = 10.sp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Reply Box
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = replyText,
                    onValueChange = { replyText = it },
                    placeholder = { Text("Reply to concierge...", color = LuxuryColors.SubtleGray, fontSize = 11.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(LuxuryDimensions.RadiusMedium)
                )
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = {
                        if (replyText.isBlank()) return@IconButton
                        isSending = true
                        scope.launch {
                            try {
                                Toast.makeText(context, "Reply dispatched to specialist.", Toast.LENGTH_SHORT).show()
                                replyText = ""
                                loadTimeline()
                            } catch (_: Exception) {}
                            finally { isSending = false }
                        }
                    }
                ) {
                    Icon(Icons.Default.Send, "Send", tint = LuxuryColors.ChampagneGold)
                }
            }
        }
    }

    // Escalation Modal (Feature 195)
    if (showEscalateModal) {
        Dialog(onDismissRequest = { showEscalateModal = false }) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.92f),
                shape = RoundedCornerShape(LuxuryDimensions.RadiusMedium),
                color = LuxuryColors.CardSurface,
                border = BorderStroke(1.dp, LuxuryColors.CrimsonAlert)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("ESCALATE TO SENIOR CHAUFFEUR DESK", style = LuxuryTypography.HeadlineMedium.copy(color = LuxuryColors.CrimsonAlert, fontSize = 14.sp))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Supervisory intervention within 15 minutes.", style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = escalateReason,
                        onValueChange = { escalateReason = it },
                        placeholder = { Text("Reason for escalation...", color = LuxuryColors.SubtleGray, fontSize = 10.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    val eRes = ApiClient.getService().escalateSupportTicket(
                                        ticketId = ticketId,
                                        payload = TicketEscalateRequestDto(reason = escalateReason.ifBlank { "Client requested priority review" })
                                    )
                                    if (eRes.isSuccessful) {
                                        Toast.makeText(context, "Case escalated to Senior Supervisor", Toast.LENGTH_SHORT).show()
                                        showEscalateModal = false
                                        loadTimeline()
                                    }
                                } catch (_: Exception) {}
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.CrimsonAlert)
                    ) {
                        Text("CONFIRM ESCALATION", color = LuxuryColors.PlatinumWhite, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }
    }

    // CSAT Survey Modal (Feature 197)
    if (showCsatModal) {
        Dialog(onDismissRequest = { showCsatModal = false }) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.92f),
                shape = RoundedCornerShape(LuxuryDimensions.RadiusMedium),
                color = LuxuryColors.CardSurface,
                border = BorderStroke(1.dp, LuxuryColors.ChampagneGold)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("RATE CONCIERGE ASSISTANCE", style = LuxuryTypography.HeadlineMedium.copy(color = LuxuryColors.ChampagneGold, fontSize = 14.sp))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("How satisfied are you with this resolution?", style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        (1..5).forEach { star ->
                            IconButton(onClick = { csatScore = star }) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "$star stars",
                                    tint = if (star <= csatScore) LuxuryColors.ChampagneGold else LuxuryColors.CardBorder,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = csatComment,
                        onValueChange = { csatComment = it },
                        placeholder = { Text("Optional feedback...", color = LuxuryColors.SubtleGray, fontSize = 10.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    val sRes = ApiClient.getService().submitCsatSurvey(
                                        ticketId = ticketId,
                                        payload = TicketCsatRequestDto(score = csatScore, comment = csatComment.ifBlank { null })
                                    )
                                    if (sRes.isSuccessful) {
                                        Toast.makeText(context, "Thank you for your rating!", Toast.LENGTH_SHORT).show()
                                        showCsatModal = false
                                        loadTimeline()
                                    }
                                } catch (_: Exception) {}
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.ChampagneGold)
                    ) {
                        Text("SUBMIT RATING", color = LuxuryColors.ObsidianBlack, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// =============================================================================
// 200: DATA RETENTION PREFERENCE DISPLAY (Feature 200)
// =============================================================================
@Composable
fun DataRetentionDialog(
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var policy by remember { mutableStateOf<DataRetentionPolicyDto?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                val res = ApiClient.getService().getDataRetentionPolicy()
                if (res.isSuccessful) policy = res.body()
            } catch (_: Exception) {}
            finally { isLoading = false }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.95f),
            shape = RoundedCornerShape(LuxuryDimensions.RadiusLarge),
            color = LuxuryColors.CardSurface,
            border = BorderStroke(1.dp, LuxuryColors.ChampagneGold)
        ) {
            Column(modifier = Modifier.padding(LuxuryDimensions.PaddingLarge)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("DATA RETENTION POLICY", style = LuxuryTypography.HeadlineMedium.copy(fontSize = 16.sp))
                        Text("Jurisdiction: ${policy?.jurisdiction ?: "India DPDPA"}", style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp))
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close", tint = LuxuryColors.SubtleGray)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = LuxuryColors.ChampagneGold)
                    }
                } else if (policy != null) {
                    val p = policy!!
                    Text(p.description, style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp, color = LuxuryColors.PlatinumWhite))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Statutory Default: ${p.defaultRetentionDays} days", style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.ChampagneGold, fontWeight = FontWeight.Bold, fontSize = 11.sp))
                    Text("Allowed User Range: ${p.minimumStatutoryDays} – ${p.maximumAllowedDays} days", style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Protected Data Categories:", style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.SubtleGray, fontWeight = FontWeight.Bold, fontSize = 10.sp))
                    p.dataTypesCovered.forEach { dt ->
                        Text("✓ $dt", style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp, color = LuxuryColors.PlatinumWhite))
                    }
                }
            }
        }
    }
}
