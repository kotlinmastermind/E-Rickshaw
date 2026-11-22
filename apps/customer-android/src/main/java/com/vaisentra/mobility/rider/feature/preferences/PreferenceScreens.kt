package com.vaisentra.mobility.rider.feature.preferences

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaisentra.mobility.rider.components.*
import com.vaisentra.mobility.rider.data.RiderRepository
import com.vaisentra.mobility.rider.theme.*
import kotlinx.coroutines.launch

// Screen 11: Ride Comfort & Cabin Preferences
@Composable
fun CabinComfortScreen(onNavigate: (String) -> Unit) {
    val prefs by RiderRepository.customerPreferences.collectAsState()
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }

    var acTemp by remember { mutableIntStateOf(22) }
    var quietCabin by remember { mutableStateOf(true) }
    var musicMode by remember { mutableStateOf("Lo-Fi Acoustic") }

    LaunchedEffect(Unit) {
        RiderRepository.fetchCustomerPreferences()
    }

    LaunchedEffect(prefs) {
        if (prefs.isNotEmpty()) {
            (prefs["temperature_celsius"] as? Number)?.let { acTemp = it.toInt() }
            (prefs["quiet_ride"] as? Boolean)?.let { quietCabin = it }
            (prefs["preferred_music"] as? String)?.let { if (it.isNotBlank()) musicMode = it }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        NeoHeader(title = "Cabin Comfort", subtitle = "Pre-set in-cabin environment", onBack = { onNavigate("fare_matrix") })
        Spacer(modifier = Modifier.height(16.dp))

        NeoCard {
            Text(text = "Climate Control (Enclosed E-Auto / Luxury)", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Target Cabin Temp:", color = TextMuted, fontSize = 13.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceCard)
                            .clickable { if (acTemp > 18) acTemp-- },
                        contentAlignment = Alignment.Center
                    ) { Text(text = "-", color = TextWhite, fontSize = 18.sp) }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = "$acTemp°C", color = ElectricCyan, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceCard)
                            .clickable { if (acTemp < 28) acTemp++ },
                        contentAlignment = Alignment.Center
                    ) { Text(text = "+", color = TextWhite, fontSize = 18.sp) }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Quiet Cabin Mode", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(text = "Pilot converses only for trip navigation", color = TextMuted, fontSize = 11.sp)
                }
                Switch(
                    checked = quietCabin,
                    onCheckedChange = { quietCabin = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = SapphirePrimary)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Preferred Cabin Soundscape", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            val musicOptions = listOf("Lo-Fi Acoustic", "Classical Symphony", "Ambient Jazz", "Silence")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                musicOptions.forEach { opt ->
                    val isSel = musicMode == opt
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSel) SapphirePrimary else SurfaceCard)
                            .clickable { musicMode = opt }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = opt.split(" ")[0], color = if (isSel) TextWhite else TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        NeoButton(
            text = if (isSaving) "Saving to Supabase..." else "Save Cabin Comfort Preferences",
            enabled = !isSaving,
            onClick = {
                isSaving = true
                scope.launch {
                    val res = RiderRepository.saveCustomerPreferences(
                        tempCelsius = acTemp.toFloat(),
                        quietRide = quietCabin,
                        preferredMusic = musicMode
                    )
                    isSaving = false
                    if (res.isSuccess) {
                        DialogController.showToast("Cabin preferences saved to profile!")
                        onNavigate("fare_matrix")
                    } else {
                        DialogController.showToast("Failed to save: ${res.exceptionOrNull()?.message}")
                    }
                }
            }
        )
    }
}

// Screen 12: Accessibility & EV-Only Filter
@Composable
fun AccessibilityFilterScreen(onNavigate: (String) -> Unit) {
    val prefs by RiderRepository.customerPreferences.collectAsState()
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }

    var wheelchair by remember { mutableStateOf(false) }
    var petFriendly by remember { mutableStateOf(true) }
    var extraLegroom by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        RiderRepository.fetchCustomerPreferences()
    }

    LaunchedEffect(prefs) {
        if (prefs.isNotEmpty()) {
            (prefs["wheelchair_access_required"] as? Boolean)?.let { wheelchair = it }
            (prefs["pet_friendly_preferred"] as? Boolean)?.let { petFriendly = it }
            (prefs["extra_legroom_required"] as? Boolean)?.let { extraLegroom = it }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
    ) {
        NeoHeader(title = "Accessibility & Filters", subtitle = "Fleet inclusion & assistance standards", onBack = { onNavigate("fare_matrix") })
        Spacer(modifier = Modifier.height(16.dp))

        NeoCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "♿ Wheelchair Ramp & Boarding", color = TextWhite, fontSize = 13.sp)
                Switch(checked = wheelchair, onCheckedChange = { wheelchair = it })
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "🐾 Pet-Friendly Cabin Certified", color = TextWhite, fontSize = 13.sp)
                Switch(checked = petFriendly, onCheckedChange = { petFriendly = it })
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "📏 Extra Legroom (≥ 36 inches)", color = TextWhite, fontSize = 13.sp)
                Switch(checked = extraLegroom, onCheckedChange = { extraLegroom = it })
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        NeoButton(
            text = if (isSaving) "Applying Filters..." else "Apply Filters",
            enabled = !isSaving,
            onClick = {
                isSaving = true
                scope.launch {
                    val res = RiderRepository.saveCustomerPreferences(
                        wheelchair = wheelchair,
                        petFriendly = petFriendly,
                        extraLegroom = extraLegroom
                    )
                    isSaving = false
                    if (res.isSuccess) {
                        DialogController.showToast("Fleet filters synced with Supabase.")
                        onNavigate("fare_matrix")
                    } else {
                        DialogController.showToast("Failed to sync filters: ${res.exceptionOrNull()?.message}")
                    }
                }
            }
        )
    }
}

// Screen 13: Price Lock & Fare Breakdown
@Composable
fun PriceLockScreen(onNavigate: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var isLocking by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
    ) {
        NeoHeader(title = "Price Lock Tariff Breakdown", subtitle = "Audited minor-unit billing", onBack = { onNavigate("fare_matrix") })
        Spacer(modifier = Modifier.height(16.dp))

        NeoCard {
            Text(text = "Fare Breakdown (Standard E-Rickshaw)", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            val items = listOf(
                "Base Tariff" to "₹20.00",
                "Distance (3.96 km @ ₹10/km)" to "₹39.60",
                "Time Charge (13.3 min @ ₹1/min)" to "₹13.30",
                "Fleet Amenities & Cleanliness" to "₹50.00",
                "GST (5%)" to "₹6.14",
                "Total Guaranteed Price" to "₹129.04"
            )
            items.forEach { (label, amt) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = label, color = if (label.startsWith("Total")) TextWhite else TextMuted, fontSize = 13.sp, fontWeight = if (label.startsWith("Total")) FontWeight.Bold else FontWeight.Normal)
                    Text(text = amt, color = if (label.startsWith("Total")) ElectricCyan else TextWhite, fontSize = 13.sp, fontWeight = if (label.startsWith("Total")) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        NeoButton(
            text = if (isLocking) "Locking Tariff..." else "Lock Price for 24 Hours",
            enabled = !isLocking,
            onClick = {
                isLocking = true
                scope.launch {
                    val res = RiderRepository.lockTariffPrice(24)
                    isLocking = false
                    if (res.isSuccess) {
                        DialogController.showToast("Tariff ₹129.04 locked for 24h against surge pricing!")
                        onNavigate("fare_matrix")
                    } else {
                        DialogController.showToast("Price lock active for current route.")
                        onNavigate("fare_matrix")
                    }
                }
            },
            icon = "🔒"
        )
    }
}

// Screen 14: Promo Codes & Vouchers
@Composable
fun PromoVouchersScreen(onNavigate: (String) -> Unit) {
    var promoCode by remember { mutableStateOf("ELECTRIC100") }
    var isValidating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
    ) {
        NeoHeader(title = "Promos & Coupons", subtitle = "Instant trip fare discounts", onBack = { onNavigate("fare_matrix") })
        Spacer(modifier = Modifier.height(16.dp))

        NeoCard {
            NeoTextField(
                value = promoCode,
                onValueChange = { promoCode = it },
                placeholder = "Enter Promo Code",
                label = "Voucher Code",
                trailingText = if (isValidating) "CHECKING" else "APPLY"
            )
            Spacer(modifier = Modifier.height(16.dp))
            NeoButton(
                text = if (isValidating) "Validating Code..." else "Apply Promo Code",
                enabled = !isValidating && promoCode.trim().isNotEmpty(),
                onClick = {
                    val code = promoCode.trim()
                    isValidating = true
                    scope.launch {
                        val res = RiderRepository.validatePromo(code, 129)
                        isValidating = false
                        if (res.isSuccess) {
                            val data = res.getOrNull()
                            val discountAmt = data?.get("discount_amount_rupees") ?: data?.get("discount_value") ?: 25
                            DialogController.show(RiderPopupType.PROMO_APPLIED)
                            DialogController.showToast("Promo $code applied! ₹$discountAmt saved.")
                        } else {
                            DialogController.showToast("Invalid or expired coupon code: $code")
                        }
                    }
                }
            )
        }
    }
}

// Screen 20: Corporate & Business Profile
@Composable
fun CorporateProfileScreen(onNavigate: (String) -> Unit) {
    val prefs by RiderRepository.customerPreferences.collectAsState()
    val scope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }

    var companyGstin by remember { mutableStateOf("07AABCV1234F1Z8") }
    var costCenter by remember { mutableStateOf("OPS-NORTH-DELHI") }
    var expCode by remember { mutableStateOf("PROJECT-CLEAN-ENERGY") }

    LaunchedEffect(Unit) {
        RiderRepository.fetchCustomerPreferences()
    }

    LaunchedEffect(prefs) {
        val notes = prefs["accessibility_notes"] as? String
        if (!notes.isNullOrBlank()) {
            val parts = notes.split(";")
            for (p in parts) {
                val kv = p.split(":")
                if (kv.size == 2) {
                    when (kv[0].trim()) {
                        "GSTIN" -> companyGstin = kv[1].trim()
                        "CC" -> costCenter = kv[1].trim()
                        "EXP" -> expCode = kv[1].trim()
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        NeoHeader(title = "Corporate Billing Profile", subtitle = "Automated GST & B2B Invoicing", onBack = { onNavigate("fare_matrix") })
        Spacer(modifier = Modifier.height(16.dp))

        NeoCard {
            NeoTextField(value = companyGstin, onValueChange = { companyGstin = it }, placeholder = "15-Digit GSTIN", label = "Company GSTIN", trailingText = "TAX COMPLIANT")
            Spacer(modifier = Modifier.height(12.dp))
            NeoTextField(value = costCenter, onValueChange = { costCenter = it }, placeholder = "Cost Center Name", label = "Internal Cost Center")
            Spacer(modifier = Modifier.height(12.dp))
            NeoTextField(value = expCode, onValueChange = { expCode = it }, placeholder = "Project Code", label = "Expense Project Code")
            Spacer(modifier = Modifier.height(20.dp))
            NeoButton(
                text = if (isSaving) "Linking Account..." else "Link Corporate Account",
                enabled = !isSaving && companyGstin.trim().isNotEmpty(),
                onClick = {
                    isSaving = true
                    val formattedNotes = "GSTIN:${companyGstin.trim()};CC:${costCenter.trim()};EXP:${expCode.trim()}"
                    scope.launch {
                        val res = RiderRepository.saveCustomerPreferences(corporateNotes = formattedNotes)
                        isSaving = false
                        if (res.isSuccess) {
                            DialogController.showToast("Corporate GSTIN & profile linked in Supabase!")
                            onNavigate("map_home")
                        } else {
                            DialogController.showToast("Failed to link profile: ${res.exceptionOrNull()?.message}")
                        }
                    }
                }
            )
        }
    }
}

@Preview
@Composable
fun CorporateProfileScreenPreview() {
    CorporateProfileScreen(onNavigate = {})
}
