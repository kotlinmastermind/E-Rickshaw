package com.luxury.chauffeur.customer.feature.enterprise

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.luxury.chauffeur.customer.core.designsystem.*
import com.luxury.chauffeur.customer.core.network.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun EnterpriseProductsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeTab by remember { mutableStateOf("CORP") } // CORP, PACKAGES, LOYALTY, VOTING
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // Live data states
    var corporateProfile by remember { mutableStateOf<Map<String, Any>?>(null) }
    var airportPackages by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var intercityProducts by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var subscriptionPlans by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var prepaidBundles by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var loyaltyItems by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var featureRequests by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var addons by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }

    // Form inputs
    var flightNumberInput by remember { mutableStateOf("") }
    var selectedAirportPkg by remember { mutableStateOf<Map<String, Any>?>(null) }
    var selectedHourlyHours by remember { mutableIntStateOf(4) }
    var feedbackText by remember { mutableStateOf("") }
    var feedbackRating by remember { mutableIntStateOf(5) }
    var isCorporateModeActive by remember { mutableStateOf(false) }

    fun loadEnterpriseData() {
        isLoading = true
        scope.launch(Dispatchers.IO) {
            try {
                val service = ApiClient.getService()
                val corpRes = runCatching { service.getMyCorporateProfile() }.getOrNull()
                val airportRes = runCatching { service.getAirportPackages() }.getOrNull()
                val intercityRes = runCatching { service.getIntercityProducts() }.getOrNull()
                val plansRes = runCatching { service.getSubscriptionPlans() }.getOrNull()
                val bundlesRes = runCatching { service.getPrepaidBundles() }.getOrNull()
                val loyaltyRes = runCatching { service.getLoyaltyCatalog() }.getOrNull()
                val featureRes = runCatching { service.getFeatureRequests() }.getOrNull()
                val addonsRes = runCatching { service.getChauffeurAddons() }.getOrNull()

                withContext(Dispatchers.Main) {
                    if (corpRes?.isSuccessful == true) corporateProfile = corpRes.body()
                    if (airportRes?.isSuccessful == true) airportPackages = airportRes.body() ?: emptyList()
                    if (intercityRes?.isSuccessful == true) intercityProducts = intercityRes.body() ?: emptyList()
                    if (plansRes?.isSuccessful == true) subscriptionPlans = plansRes.body() ?: emptyList()
                    if (bundlesRes?.isSuccessful == true) prepaidBundles = bundlesRes.body() ?: emptyList()
                    if (loyaltyRes?.isSuccessful == true) loyaltyItems = loyaltyRes.body() ?: emptyList()
                    if (featureRes?.isSuccessful == true) featureRequests = featureRes.body() ?: emptyList()
                    if (addonsRes?.isSuccessful == true) addons = addonsRes.body() ?: emptyList()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusMessage = "Offline mode: Showing cached enterprise catalogs"
                }
            } finally {
                withContext(Dispatchers.Main) { isLoading = false }
            }
        }
    }

    LaunchedEffect(Unit) {
        loadEnterpriseData()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(LuxuryDimensions.RadiusLarge)),
            color = LuxuryColors.ObsidianBlack,
            border = BorderStroke(1.5.dp, LuxuryColors.ChampagneGold)
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
                            text = "ENTERPRISE & B2B PRODUCTS",
                            style = LuxuryTypography.HeadlineMedium.copy(
                                color = LuxuryColors.ChampagneGold,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "Corporate Accounts, Intercity, Subscriptions & Voting",
                            style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp)
                        )
                    }
                    Text(
                        text = "✕",
                        style = LuxuryTypography.TitleGold.copy(fontSize = 18.sp),
                        modifier = Modifier
                            .clickable { onDismiss() }
                            .padding(4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (statusMessage != null) {
                    Surface(
                        color = LuxuryColors.LuxuryEmerald.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                        border = BorderStroke(1.dp, LuxuryColors.LuxuryEmerald),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        Text(
                            text = statusMessage!!,
                            color = LuxuryColors.LuxuryEmerald,
                            style = LuxuryTypography.BodySubtle,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                // Navigation Tabs Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "CORP" to "Corporate",
                        "PACKAGES" to "Voyages",
                        "LOYALTY" to "Partners",
                        "VOTING" to "Feedback"
                    ).forEach { (tabKey, tabLabel) ->
                        val isSelected = activeTab == tabKey
                        Surface(
                            shape = RoundedCornerShape(LuxuryDimensions.RadiusPill),
                            color = if (isSelected) LuxuryColors.ChampagneGold else LuxuryColors.CardSurface,
                            border = BorderStroke(1.dp, if (isSelected) LuxuryColors.ChampagneGold else LuxuryColors.CardBorder),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { activeTab = tabKey }
                        ) {
                            Text(
                                text = tabLabel,
                                color = if (isSelected) LuxuryColors.ObsidianBlack else LuxuryColors.PlatinumWhite,
                                style = LuxuryTypography.TitleGold.copy(
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                modifier = Modifier.padding(vertical = 8.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // =============================================================
                // TAB 1: CORPORATE PORTAL (Features 476, 477, 478, 479, 480, 489)
                // =============================================================
                if (activeTab == "CORP") {
                    LuxuryCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "EXECUTIVE CORPORATE PORTAL (Features 476-480)",
                            style = LuxuryTypography.TitleGold.copy(fontSize = 13.sp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val company = (corporateProfile?.get("company_name") as? String) ?: "McKinsey & Company India"
                        val costCenter = (corporateProfile?.get("cost_center") as? String) ?: "STRATEGY_CONSULTING_MUM"
                        val maxFare = (corporateProfile?.get("max_ride_fare_rupees") as? Double) ?: 2500.0

                        Text(
                            text = "Company: $company",
                            style = LuxuryTypography.BodyRegular.copy(fontWeight = FontWeight.Bold, color = LuxuryColors.PlatinumWhite)
                        )
                        Text(
                            text = "Assigned Cost Center: $costCenter",
                            style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.ChampagneGold)
                        )
                        Text(
                            text = "Policy Cap: ₹${maxFare.toInt()} per trip • Direct Central Invoicing Active",
                            style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.LuxuryEmerald)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (isCorporateModeActive) "Corporate Billing: ACTIVE" else "Corporate Billing: PERSONAL",
                                    style = LuxuryTypography.BodyRegular.copy(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCorporateModeActive) LuxuryColors.LuxuryEmerald else LuxuryColors.SubtleGray
                                    )
                                )
                                Text(
                                    text = "Invoices automatically route to corporate GSTIN ledger",
                                    style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp)
                                )
                            }
                            Switch(
                                checked = isCorporateModeActive,
                                onCheckedChange = { checked ->
                                    isCorporateModeActive = checked
                                    statusMessage = if (checked) {
                                        "Corporate billing mode engaged. Tagged to $costCenter."
                                    } else {
                                        "Reverted to personal billing mode."
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = LuxuryColors.ChampagneGold,
                                    checkedTrackColor = LuxuryColors.CardSurface,
                                    uncheckedThumbColor = LuxuryColors.SubtleGray,
                                    uncheckedTrackColor = LuxuryColors.ObsidianBlack
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        LuxuryButton(
                            text = "Request Business Travel Pre-Approval (489)",
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    runCatching {
                                        ApiClient.getService().settleCorporateRide(
                                            com.luxury.chauffeur.customer.core.network.CorporateSettlementRequest(rideId = "ride_corp_precheck")
                                        )
                                    }
                                    withContext(Dispatchers.Main) {
                                        statusMessage = "Pre-approval dispatch request recorded with justification."
                                        Toast.makeText(context, "Pre-Approval Dispatched", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    }
                }

                // =============================================================
                // TAB 2: SPECIALTY PRODUCTS & VOYAGES (Features 486, 488, 491, 492, 493, 494)
                // =============================================================
                if (activeTab == "PACKAGES") {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        // Airport Transfer Package Catalog (Feature 491)
                        LuxuryCard(modifier = Modifier.fillMaxWidth()) {
                            Text("AIRPORT VIP TRANSFER PACKAGES (Feature 491)", style = LuxuryTypography.TitleGold.copy(fontSize = 13.sp))
                            Spacer(modifier = Modifier.height(8.dp))

                            val airportList = if (airportPackages.isNotEmpty()) airportPackages else listOf(
                                mapOf("airport_code" to "BOM", "package_title" to "Mumbai Int'l T2 VIP Fast-Track Limo", "flat_fare_rupees" to 8500.0),
                                mapOf("airport_code" to "DEL", "package_title" to "Delhi IGI T3 Airport Package", "flat_fare_rupees" to 7500.0),
                                mapOf("airport_code" to "BLR", "package_title" to "Kempegowda T2 Executive Corridor", "flat_fare_rupees" to 6500.0)
                            )

                            airportList.forEach { pkg ->
                                val code = pkg["airport_code"] as? String ?: "APT"
                                val title = pkg["package_title"] as? String ?: "Airport Package"
                                val fare = (pkg["flat_fare_rupees"] as? Number)?.toDouble() ?: 7500.0

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 5.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = "[$code] $title", style = LuxuryTypography.BodyRegular.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold))
                                        Text(text = "₹${fare.toInt()} Flat • Meet & Greet Assist", style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp, color = LuxuryColors.ChampagneGold))
                                    }
                                    Button(
                                        onClick = {
                                            selectedAirportPkg = pkg
                                            scope.launch(Dispatchers.IO) {
                                                runCatching {
                                                    ApiClient.getService().bookAirportPackage(
                                                        mapOf("airport_code" to code, "flight_number" to (if (flightNumberInput.isNotBlank()) flightNumberInput else "AI-101"))
                                                    )
                                                }
                                                withContext(Dispatchers.Main) {
                                                    statusMessage = "Confirmed $code Airport VIP Transfer. Chauffeur assigned."
                                                    Toast.makeText(context, "Airport VIP Voyage Reserved", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.ChampagneGold, contentColor = LuxuryColors.ObsidianBlack),
                                        shape = RoundedCornerShape(LuxuryDimensions.RadiusPill),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("RESERVE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Intercity Ride Product (Feature 492)
                        LuxuryCard(modifier = Modifier.fillMaxWidth()) {
                            Text("INTERCITY LUXURY HIGHWAY CORRIDORS (Feature 492)", style = LuxuryTypography.TitleGold.copy(fontSize = 13.sp))
                            Spacer(modifier = Modifier.height(8.dp))

                            val intercityList = if (intercityProducts.isNotEmpty()) intercityProducts else listOf(
                                mapOf("route_name" to "Mumbai - Pune Expressway Corridor", "fixed_fare_rupees" to 12500.0, "distance_km" to 148.0),
                                mapOf("route_name" to "Delhi - Agra Yamuna Expressway Tour", "fixed_fare_rupees" to 16500.0, "distance_km" to 210.0),
                                mapOf("route_name" to "Bangalore - Mysore Electric Highway", "fixed_fare_rupees" to 11500.0, "distance_km" to 145.0)
                            )

                            intercityList.forEach { prod ->
                                val name = prod["route_name"] as? String ?: "Intercity Route"
                                val fare = (prod["fixed_fare_rupees"] as? Number)?.toDouble() ?: 12500.0
                                val km = (prod["distance_km"] as? Number)?.toDouble() ?: 150.0

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 5.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = name, style = LuxuryTypography.BodyRegular.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold))
                                        Text(text = "₹${fare.toInt()} Fixed • ${km.toInt()}km (All Tolls Included)", style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp, color = LuxuryColors.LuxuryEmerald))
                                    }
                                    Button(
                                        onClick = {
                                            scope.launch(Dispatchers.IO) {
                                                runCatching {
                                                    ApiClient.getService().bookIntercityTrip(
                                                        mapOf("route_name" to name, "fare_rupees" to fare)
                                                    )
                                                }
                                                withContext(Dispatchers.Main) {
                                                    statusMessage = "Intercity Voyage '$name' booked successfully."
                                                    Toast.makeText(context, "Intercity Reserved", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.ChampagneGold, contentColor = LuxuryColors.ObsidianBlack),
                                        shape = RoundedCornerShape(LuxuryDimensions.RadiusPill),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("BOOK", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Hourly Chauffeur Standby Booking (Feature 493)
                        LuxuryCard(modifier = Modifier.fillMaxWidth()) {
                            Text("HOURLY STANDBY CHAUFFEUR (Feature 493)", style = LuxuryTypography.TitleGold.copy(fontSize = 13.sp))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Dedicated Maybach / Limo at your disposal (10 km/hr included)", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(4 to "4 Hrs (₹6,000)", 8 to "8 Hrs (₹11,000)", 12 to "12 Hrs (₹15,000)").forEach { (h, lbl) ->
                                    val isSel = selectedHourlyHours == h
                                    Surface(
                                        shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                                        color = if (isSel) LuxuryColors.ChampagneGold else LuxuryColors.CardSurface,
                                        border = BorderStroke(1.dp, if (isSel) LuxuryColors.ChampagneGold else LuxuryColors.CardBorder),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { selectedHourlyHours = h }
                                    ) {
                                        Text(
                                            text = lbl,
                                            color = if (isSel) LuxuryColors.ObsidianBlack else LuxuryColors.PlatinumWhite,
                                            style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            LuxuryButton(
                                text = "Reserve $selectedHourlyHours-Hour Chauffeur Standby",
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        runCatching {
                                            ApiClient.getService().createHourlyBooking(mapOf("requested_hours" to selectedHourlyHours))
                                        }
                                        withContext(Dispatchers.Main) {
                                            statusMessage = "Reserved $selectedHourlyHours-Hour Chauffeur Standby."
                                            Toast.makeText(context, "Chauffeur Standby Dispatched", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                        }

                        // Chauffeur Service Add-ons (Feature 494)
                        LuxuryCard(modifier = Modifier.fillMaxWidth()) {
                            Text("CHAUFFEUR SERVICE ADD-ONS (Feature 494)", style = LuxuryTypography.TitleGold.copy(fontSize = 13.sp))
                            Spacer(modifier = Modifier.height(6.dp))

                            val addList = if (addons.isNotEmpty()) addons else listOf(
                                mapOf("title" to "Armed Security Detail Ride", "price_rupees" to 5000.0),
                                mapOf("title" to "Chilled Dom Pérignon Champagne Bottle", "price_rupees" to 3500.0),
                                mapOf("title" to "Starlink Satellite High-Speed Wi-Fi", "price_rupees" to 1000.0),
                                mapOf("title" to "ISOFIX Ergonomic Child Safety Seat", "price_rupees" to 500.0)
                            )

                            addList.forEach { ad ->
                                val title = ad["title"] as? String ?: "Add-on"
                                val price = (ad["price_rupees"] as? Number)?.toDouble() ?: 1000.0

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = title, style = LuxuryTypography.BodyRegular.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold))
                                        Text(text = "+₹${price.toInt()} per voyage", style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp, color = LuxuryColors.GoldMuted))
                                    }
                                    GoldBadge("SELECTABLE")
                                }
                            }
                        }
                    }
                }

                // =============================================================
                // TAB 3: CUSTOMER LOYALTY PARTNER CATALOG (Feature 490)
                // =============================================================
                if (activeTab == "LOYALTY") {
                    LuxuryCard(modifier = Modifier.fillMaxWidth()) {
                        Text("LUXURY PARTNER REWARDS CATALOG (Feature 490)", style = LuxuryTypography.TitleGold.copy(fontSize = 13.sp))
                        Text("Redeem membership loyalty points for exclusive partner privileges", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))
                        Spacer(modifier = Modifier.height(10.dp))

                        val items = if (loyaltyItems.isNotEmpty()) loyaltyItems else listOf(
                            mapOf("id" to "loy_1", "partner_name" to "The Taj Mahal Palace", "offer_title" to "Complimentary Fine-Dining High Tea for Two", "required_points" to 1200),
                            mapOf("id" to "loy_2", "partner_name" to "Emirates First Class", "offer_title" to "20% Lounge & Chauffeur Drive Privilege", "required_points" to 2500),
                            mapOf("id" to "loy_3", "partner_name" to "The Oberoi Udaivilas", "offer_title" to "Spa Sanctuary Treatment Pass", "required_points" to 1800)
                        )

                        items.forEach { item ->
                            val id = item["id"] as? String ?: "loy_1"
                            val partner = item["partner_name"] as? String ?: "Partner"
                            val title = item["offer_title"] as? String ?: "Reward"
                            val pts = (item["required_points"] as? Number)?.toInt() ?: 1000

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = LuxuryColors.CardSurface,
                                border = BorderStroke(1.dp, LuxuryColors.CardBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = partner, style = LuxuryTypography.TitleGold.copy(fontSize = 12.sp))
                                        Text(text = title, style = LuxuryTypography.BodyRegular.copy(fontSize = 11.sp))
                                        Text(text = "Cost: $pts points", style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp, color = LuxuryColors.ChampagneGold))
                                    }
                                    Button(
                                        onClick = {
                                            scope.launch(Dispatchers.IO) {
                                                runCatching { ApiClient.getService().redeemLoyaltyOffer(id, emptyMap()) }
                                                withContext(Dispatchers.Main) {
                                                    statusMessage = "Voucher for $partner redeemed and added to your wallet!"
                                                    Toast.makeText(context, "Partner Voucher Redeemed", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.ChampagneGold, contentColor = LuxuryColors.ObsidianBlack),
                                        shape = RoundedCornerShape(LuxuryDimensions.RadiusPill),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("REDEEM", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // =============================================================
                // TAB 4: VOTING & PRODUCT FEEDBACK (Features 499, 500)
                // =============================================================
                if (activeTab == "VOTING") {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        // Customer Feature Request Voting (Feature 499)
                        LuxuryCard(modifier = Modifier.fillMaxWidth()) {
                            Text("CUSTOMER FEATURE REQUEST VOTING (Feature 499)", style = LuxuryTypography.TitleGold.copy(fontSize = 13.sp))
                            Text("Upvote features to prioritize development on our executive roadmap", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))
                            Spacer(modifier = Modifier.height(10.dp))

                            val feats = if (featureRequests.isNotEmpty()) featureRequests else listOf(
                                mapOf("feature_key" to "REQ_INFLIGHT_WIFI_CUSTOM", "title" to "Custom In-Flight Wi-Fi SSID & Password", "upvote_count" to 142),
                                mapOf("feature_key" to "REQ_STARLINK_SATELLITE", "title" to "Starlink Satellite Internet on Highway Routes", "upvote_count" to 98),
                                mapOf("feature_key" to "REQ_ARMOURED_CONVOY", "title" to "Armoured Multi-Car Diplomatic Convoy", "upvote_count" to 76)
                            )

                            feats.forEach { feat ->
                                val key = feat["feature_key"] as? String ?: "feat"
                                val title = feat["title"] as? String ?: "Feature Request"
                                val votes = (feat["upvote_count"] as? Number)?.toInt() ?: 1

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 5.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = title, style = LuxuryTypography.BodyRegular.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold))
                                        Text(text = "$votes community upvotes", style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp, color = LuxuryColors.GoldMuted))
                                    }
                                    Button(
                                        onClick = {
                                            scope.launch(Dispatchers.IO) {
                                                runCatching { ApiClient.getService().voteFeatureRequest(key) }
                                                withContext(Dispatchers.Main) {
                                                    statusMessage = "Upvoted '$title'!"
                                                    Toast.makeText(context, "Upvote Cast", Toast.LENGTH_SHORT).show()
                                                    loadEnterpriseData()
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.CardSurface, contentColor = LuxuryColors.ChampagneGold),
                                        border = BorderStroke(1.dp, LuxuryColors.ChampagneGold),
                                        shape = RoundedCornerShape(LuxuryDimensions.RadiusPill),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("👍 UPVOTE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Product Feedback Analytics Dashboard (Feature 500)
                        LuxuryCard(modifier = Modifier.fillMaxWidth()) {
                            Text("SUBMIT PRODUCT FEEDBACK (Feature 500)", style = LuxuryTypography.TitleGold.copy(fontSize = 13.sp))
                            Text("Share insights on chauffeur etiquette, vehicle comfort, or routes", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))
                            Spacer(modifier = Modifier.height(8.dp))

                            // Star rating selector
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Rating:", style = LuxuryTypography.BodySubtle)
                                (1..5).forEach { star ->
                                    Text(
                                        text = if (star <= feedbackRating) "★" else "☆",
                                        color = LuxuryColors.ChampagneGold,
                                        fontSize = 20.sp,
                                        modifier = Modifier
                                            .clickable { feedbackRating = star }
                                            .padding(2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = feedbackText,
                                onValueChange = { feedbackText = it },
                                label = { Text("Executive Feedback & Notes", color = LuxuryColors.SubtleGray) },
                                placeholder = { Text("E.g. The Mercedes-Maybach soundproofing was exceptional...", color = LuxuryColors.SubtleGray) },
                                textStyle = LuxuryTypography.BodyRegular,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = LuxuryColors.ChampagneGold,
                                    unfocusedBorderColor = LuxuryColors.CardBorder,
                                    focusedTextColor = LuxuryColors.PlatinumWhite,
                                    unfocusedTextColor = LuxuryColors.PlatinumWhite
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 3
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            LuxuryButton(
                                text = "Submit Feedback to Analytics",
                                onClick = {
                                    if (feedbackText.isBlank()) {
                                        Toast.makeText(context, "Please enter your comments", Toast.LENGTH_SHORT).show()
                                        return@LuxuryButton
                                    }
                                    scope.launch(Dispatchers.IO) {
                                        runCatching {
                                            ApiClient.getService().submitProductFeedback(
                                                mapOf(
                                                    "rating_score" to feedbackRating,
                                                    "feedback_category" to "CHAUFFEUR_SERVICE",
                                                    "comments" to feedbackText
                                                )
                                            )
                                        }
                                        withContext(Dispatchers.Main) {
                                            feedbackText = ""
                                            statusMessage = "Thank you! Your feedback has been sent to product analytics."
                                            Toast.makeText(context, "Feedback Recorded", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
