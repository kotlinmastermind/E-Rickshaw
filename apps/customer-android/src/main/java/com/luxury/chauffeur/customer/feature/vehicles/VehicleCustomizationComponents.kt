package com.luxury.chauffeur.customer.feature.vehicles

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.luxury.chauffeur.customer.core.designsystem.*
import com.luxury.chauffeur.customer.core.network.*
import kotlinx.coroutines.launch

@Composable
fun VehiclePhotoPreview(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    Box(
        modifier = modifier.background(
            androidx.compose.ui.graphics.Brush.verticalGradient(
                listOf(
                    LuxuryColors.CardSurface,
                    LuxuryColors.ObsidianBlack
                )
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(8.dp)
        ) {
            Text("✦", color = LuxuryColors.ChampagneGold, fontSize = 24.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                contentDescription ?: "Grand Chauffeur Fleet",
                color = LuxuryColors.PlatinumWhite,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

// -------------------------------------------------------------
// 1. VEHICLE GALLERY MODAL (Feature 51)
// -------------------------------------------------------------
@Composable
fun VehicleGalleryModal(
    categoryCode: String,
    categoryName: String,
    onDismiss: () -> Unit
) {
    var galleryData by remember { mutableStateOf<VehicleGalleryDto?>(null) }
    var selectedImageIndex by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(categoryCode) {
        isLoading = true
        try {
            val res = ApiClient.getService().getCategoryGallery(categoryCode)
            if (res.isSuccessful && res.body() != null) {
                galleryData = res.body()
            } else {
                galleryData = defaultFallbackGallery(categoryCode, categoryName)
            }
        } catch (_: Exception) {
            galleryData = defaultFallbackGallery(categoryCode, categoryName)
        } finally {
            isLoading = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(20.dp),
            color = LuxuryColors.ObsidianBlack,
            border = BorderStroke(1.dp, LuxuryColors.ChampagneGold.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Modal Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = categoryName.uppercase(),
                            style = LuxuryTypography.HeadlineMedium.copy(
                                color = LuxuryColors.ChampagneGold,
                                fontSize = 18.sp,
                                letterSpacing = 2.sp
                            )
                        )
                        Text(
                            text = "HIGH-RESOLUTION INTERIOR & EXTERIOR GALLERY",
                            style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp, color = LuxuryColors.MutedSilver)
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .background(LuxuryColors.DeepCharcoal, CircleShape)
                    ) {
                        Text("✕", color = LuxuryColors.ChampagneGold, fontSize = 14.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = LuxuryColors.ChampagneGold)
                    }
                } else if (galleryData != null) {
                    val images = galleryData!!.galleryImages
                    val currentImage = if (images.isNotEmpty() && selectedImageIndex < images.size) {
                        images[selectedImageIndex]
                    } else null

                    // Featured Large High-Res Photo View
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = LuxuryColors.DeepCharcoal,
                        border = BorderStroke(1.dp, LuxuryColors.CardBorder)
                    ) {
                        Box {
                            if (currentImage != null) {
                                VehiclePhotoPreview(
                                    url = currentImage.url,
                                    contentDescription = currentImage.label,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                // Angle Badge Overlay
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(12.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    color = LuxuryColors.ObsidianBlack.copy(alpha = 0.85f),
                                    border = BorderStroke(1.dp, LuxuryColors.ChampagneGold.copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                        Text(
                                            text = currentImage.angle.replace("_", " "),
                                            style = LuxuryTypography.BodySubtle.copy(
                                                color = LuxuryColors.ChampagneGold,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        )
                                        Text(
                                            text = currentImage.label,
                                            style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.PlatinumWhite, fontSize = 9.sp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Angle Selector Thumbnails Row
                    Text(
                        text = "VIEW ANGLES & PERSPECTIVES",
                        style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp, color = LuxuryColors.ChampagneGold)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(images.indices.toList()) { index ->
                            val img = images[index]
                            val isSelected = index == selectedImageIndex
                            Surface(
                                modifier = Modifier
                                    .width(100.dp)
                                    .height(70.dp)
                                    .clickable { selectedImageIndex = index },
                                shape = RoundedCornerShape(10.dp),
                                color = LuxuryColors.DeepCharcoal,
                                border = BorderStroke(
                                    if (isSelected) 2.dp else 1.dp,
                                    if (isSelected) LuxuryColors.ChampagneGold else LuxuryColors.CardBorder
                                )
                            ) {
                                Box {
                                    VehiclePhotoPreview(
                                        url = img.url,
                                        contentDescription = img.label,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth(),
                                        color = LuxuryColors.ObsidianBlack.copy(alpha = 0.8f)
                                    ) {
                                        Text(
                                            text = img.angle.take(10),
                                            style = LuxuryTypography.BodySubtle.copy(
                                                fontSize = 8.sp,
                                                textAlign = TextAlign.Center,
                                                color = if (isSelected) LuxuryColors.ChampagneGold else LuxuryColors.MutedSilver
                                            ),
                                            modifier = Modifier.padding(2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Specs & Architectural Description
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = LuxuryColors.DeepCharcoal,
                        border = BorderStroke(1.dp, LuxuryColors.CardBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = "FLEET SPECIFICATIONS & CRAFTSMANSHIP",
                                style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.ChampagneGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = galleryData!!.description,
                                style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.PlatinumWhite, fontSize = 11.sp, lineHeight = 16.sp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                SpecPill("ACOUSTICS", "≤ 52 dB Active Cancellation")
                                SpecPill("LEGROOM", "43.5\" First-Class Lounge")
                                SpecPill("AIR QUALITY", "HEPA + Ionizer Scented")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpecPill(title: String, desc: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = LuxuryColors.ObsidianBlack,
        border = BorderStroke(1.dp, LuxuryColors.ChampagneGold.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(title, style = LuxuryTypography.BodySubtle.copy(fontSize = 8.sp, color = LuxuryColors.ChampagneGold))
            Text(desc, style = LuxuryTypography.BodySubtle.copy(fontSize = 9.sp, color = LuxuryColors.PlatinumWhite))
        }
    }
}

// Fallback gallery images
private fun defaultFallbackGallery(code: String, name: String): VehicleGalleryDto {
    return VehicleGalleryDto(
        categoryCode = code,
        categoryName = name,
        description = "Crafted with hand-stitched Nappa leather, burr walnut veneers, dual-zone climate serenity, and acoustically insulated laminated privacy glass.",
        galleryImages = listOf(
            GalleryPhotoItemDto("EXTERIOR", "Aerodynamic Obsidian Silhouette", "https://images.unsplash.com/photo-1555215695-3004980ad54e"),
            GalleryPhotoItemDto("REAR_CABIN", "First-Class Executive Reclining Suite", "https://images.unsplash.com/photo-1563720223185-11003d516935"),
            GalleryPhotoItemDto("COCKPIT", "MBUX Hyperscreen Chauffeur Console", "https://images.unsplash.com/photo-1549399542-7e3f8b79c341"),
            GalleryPhotoItemDto("PANORAMIC", "Starlight Ambient Sunroof", "https://images.unsplash.com/photo-1503376780353-7e6692767b70"),
            GalleryPhotoItemDto("LUGGAGE", "Expansive Carpeted Cargo Hold", "https://images.unsplash.com/photo-1584345604476-8ec5e12e42dd")
        )
    )
}

// -------------------------------------------------------------
// 2. VEHICLE FEATURE COMPARISON SHEET (Feature 52)
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleFeatureComparisonSheet(
    onDismiss: () -> Unit,
    onSelectCategory: (categoryCode: String) -> Unit
) {
    var comparisonMatrix by remember { mutableStateOf<VehicleComparisonMatrixDto?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        isLoading = true
        try {
            val res = ApiClient.getService().compareVehicleCategories()
            if (res.isSuccessful && res.body() != null) {
                comparisonMatrix = res.body()
            } else {
                comparisonMatrix = defaultComparisonMatrix()
            }
        } catch (_: Exception) {
            comparisonMatrix = defaultComparisonMatrix()
        } finally {
            isLoading = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = LuxuryColors.ObsidianBlack,
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(44.dp)
                    .height(4.dp),
                shape = RoundedCornerShape(2.dp),
                color = LuxuryColors.ChampagneGold.copy(alpha = 0.5f)
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .fillMaxHeight(0.85f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "FLEET COMPARISON MATRIX",
                        style = LuxuryTypography.HeadlineMedium.copy(
                            color = LuxuryColors.ChampagneGold,
                            fontSize = 18.sp,
                            letterSpacing = 1.5.sp
                        )
                    )
                    Text(
                        text = "ACOUSTICS • LEGROOM • EMISSIONS • AMENITIES",
                        style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.MutedSilver, fontSize = 10.sp)
                    )
                }
                GoldBadge("4 TIERS")
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = LuxuryColors.ChampagneGold)
                }
            } else if (comparisonMatrix != null) {
                val cats = comparisonMatrix!!.categories
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(cats) { c ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = LuxuryColors.DeepCharcoal,
                            border = BorderStroke(1.dp, LuxuryColors.CardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        VehiclePhotoPreview(
                                            url = c.iconUrl,
                                            contentDescription = c.name,
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = c.name,
                                                style = LuxuryTypography.HeadlineMedium.copy(fontSize = 14.sp, color = LuxuryColors.ChampagneGold)
                                            )
                                            Text(
                                                text = "Up to ${c.capacity} Guests • ${c.luggage}",
                                                style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp, color = LuxuryColors.MutedSilver)
                                            )
                                        }
                                    }
                                    Button(
                                        onClick = {
                                            onSelectCategory(c.code)
                                            onDismiss()
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = LuxuryColors.ChampagneGold,
                                            contentColor = LuxuryColors.ObsidianBlack
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text("SELECT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Divider(color = LuxuryColors.CardBorder, thickness = 0.5.dp)
                                Spacer(modifier = Modifier.height(10.dp))

                                // Specs Grid
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    SpecMetricCol("LEGROOM", "${c.legroomInches}\"")
                                    SpecMetricCol("CABIN SOUND", "${c.quietnessDb} dB")
                                    SpecMetricCol("PROPULSION", if (c.isElectric) "100% Electric" else "${c.emissionsGPerKm.toInt()} g/km")
                                    SpecMetricCol("ACCESSIBILITY", if (c.wheelchairAccessible) "Hoist Ramp" else "Step-Free 32cm")
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                // Amenities Tags
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.horizontalScroll(rememberScrollState())
                                ) {
                                    c.signatureAmenities.forEach { amenity ->
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = LuxuryColors.ObsidianBlack,
                                            border = BorderStroke(1.dp, LuxuryColors.ChampagneGold.copy(alpha = 0.2f))
                                        ) {
                                            Text(
                                                text = "✦ $amenity",
                                                style = LuxuryTypography.BodySubtle.copy(fontSize = 9.sp, color = LuxuryColors.MutedSilver),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            LuxurySecondaryButton(text = "CLOSE MATRIX", onClick = onDismiss)
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SpecMetricCol(label: String, value: String) {
    Column {
        Text(text = label, style = LuxuryTypography.BodySubtle.copy(fontSize = 8.sp, color = LuxuryColors.MutedSilver))
        Text(text = value, style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp, color = LuxuryColors.ChampagneGold, fontWeight = FontWeight.Bold))
    }
}

private fun defaultComparisonMatrix(): VehicleComparisonMatrixDto {
    return VehicleComparisonMatrixDto(
        dimensions = listOf("capacity", "legroom_inches", "quietness_db", "emissions_g_per_km", "wheelchair_accessible"),
        categories = listOf(
            VehicleComparisonCategoryDto("SEDAN", "Executive Sedan", 3, "2 Large + 2 Small", 40.5, 54.0, false, 142.0, false, listOf("WiFi Hotspot", "Chilled Water", "Device Chargers"), "https://images.unsplash.com/photo-1555215695-3004980ad54e"),
            VehicleComparisonCategoryDto("SUV", "Prestige SUV", 5, "4 Large + 3 Small", 42.0, 52.0, false, 185.0, true, listOf("All-Wheel Drive", "Panoramic Roof", "Child Safety ISOFIX"), "https://images.unsplash.com/photo-1563720223185-11003d516935"),
            VehicleComparisonCategoryDto("ROYAL", "Royal Limousine", 4, "3 Large + 3 Small", 46.5, 48.0, false, 210.0, true, listOf("Reclining Massage", "Champagne Cooler", "Privacy Partition"), "https://images.unsplash.com/photo-1549399542-7e3f8b79c341"),
            VehicleComparisonCategoryDto("ULTRA_LUXURY_EV", "Ultra Luxury EV", 4, "3 Large + 2 Small", 43.5, 46.0, true, 0.0, true, listOf("Zero Tailpipe Emissions", "Acoustic Double-Glazing", "Burmester 4D"), "https://images.unsplash.com/photo-1503376780353-7e6692767b70")
        )
    )
}

// -------------------------------------------------------------
// 3. BESPOKE SANCTUARY & ACCESSIBILITY CUSTOMIZATION SHEET
// (Features 53-68, 71, 73, 74)
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BespokeSanctuaryCustomizationSheet(
    initialLuggageLarge: Int,
    initialLuggageSmall: Int,
    initialChildSeat: Boolean,
    initialChildSeatType: String,
    initialWheelchair: Boolean,
    initialStepFree: Boolean,
    initialAssistanceAnimal: Boolean,
    initialExtraLegroom: Boolean,
    initialQuietRide: Boolean,
    initialCabinTemp: Double,
    initialMusic: String,
    initialNoPhoneCall: Boolean,
    initialDriverLanguage: String,
    initialFemaleDriver: Boolean,
    initialVehicleColor: String,
    initialAmenities: List<String>,
    initialEvRide: Boolean,
    initialPetFriendly: Boolean,
    initialSpecialAssistanceNotes: String,
    onSavePreferencesToProfile: (CustomerPreferencesUpdatePayload) -> Unit,
    onApplyToBooking: (
        luggageLarge: Int,
        luggageSmall: Int,
        childSeat: Boolean,
        childSeatType: String,
        wheelchair: Boolean,
        stepFree: Boolean,
        assistanceAnimal: Boolean,
        extraLegroom: Boolean,
        quietRide: Boolean,
        cabinTemp: Double,
        music: String,
        noPhoneCall: Boolean,
        driverLanguage: String,
        femaleDriver: Boolean,
        vehicleColor: String,
        amenities: String,
        evRide: Boolean,
        petFriendly: Boolean,
        specialNotes: String
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var luggageLarge by remember { mutableIntStateOf(initialLuggageLarge) }
    var luggageSmall by remember { mutableIntStateOf(initialLuggageSmall) }
    var childSeatRequested by remember { mutableStateOf(initialChildSeat) }
    var childSeatType by remember { mutableStateOf(initialChildSeatType) }
    var wheelchairRequested by remember { mutableStateOf(initialWheelchair) }
    var stepFreePickup by remember { mutableStateOf(initialStepFree) }
    var assistanceAnimal by remember { mutableStateOf(initialAssistanceAnimal) }
    var extraLegroom by remember { mutableStateOf(initialExtraLegroom) }
    var quietRide by remember { mutableStateOf(initialQuietRide) }
    var cabinTemp by remember { mutableDoubleStateOf(initialCabinTemp) }
    var musicSelection by remember { mutableStateOf(initialMusic) }
    var noPhoneCall by remember { mutableStateOf(initialNoPhoneCall) }
    var driverLanguage by remember { mutableStateOf(initialDriverLanguage) }
    var femaleDriver by remember { mutableStateOf(initialFemaleDriver) }
    var vehicleColor by remember { mutableStateOf(initialVehicleColor) }
    var selectedAmenities by remember { mutableStateOf(initialAmenities.toMutableSet()) }
    var evRide by remember { mutableStateOf(initialEvRide) }
    var petFriendly by remember { mutableStateOf(initialPetFriendly) }
    var specialNotes by remember { mutableStateOf(initialSpecialAssistanceNotes) }
    var showProfileSavedBanner by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = LuxuryColors.ObsidianBlack,
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(44.dp)
                    .height(4.dp),
                shape = RoundedCornerShape(2.dp),
                color = LuxuryColors.ChampagneGold.copy(alpha = 0.5f)
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 20.dp)
        ) {
            // Sheet Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "BESPOKE SANCTUARY & ACCESSIBILITY",
                        style = LuxuryTypography.HeadlineMedium.copy(
                            color = LuxuryColors.ChampagneGold,
                            fontSize = 17.sp,
                            letterSpacing = 1.sp
                        )
                    )
                    Text(
                        text = "TAILOR YOUR CABIN ENVIRONMENT & SPECIAL NEEDS",
                        style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.MutedSilver, fontSize = 10.sp)
                    )
                }
                GoldBadge("BESPOKE")
            }

            if (showProfileSavedBanner) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = LuxuryColors.EmeraldGreen.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, LuxuryColors.EmeraldGreen)
                ) {
                    Text(
                        text = "✓ Preferences and Accessibility Needs saved to Guest Profile.",
                        style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.EmeraldGreen, fontSize = 11.sp),
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                // SECTION 1: LUGGAGE CAPACITY & SIZE CAPTURE (Features 53, 73)
                SectionTitle("1. LUGGAGE CAPACITY & PIECE SIZES")
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = LuxuryColors.DeepCharcoal,
                    border = BorderStroke(1.dp, LuxuryColors.CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Check-In Luggage (Up to 30\" Large)", style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.PlatinumWhite, fontWeight = FontWeight.Bold))
                                Text("Requires dedicated boot trunk space", style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.MutedSilver, fontSize = 9.sp))
                            }
                            Stepper(value = luggageLarge, onMinus = { if (luggageLarge > 0) luggageLarge-- }, onPlus = { if (luggageLarge < 6) luggageLarge++ })
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Divider(color = LuxuryColors.CardBorder, thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Cabin Bags / Duffels (Up to 22\" Small)", style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.PlatinumWhite, fontWeight = FontWeight.Bold))
                                Text("Fits in boot or passenger footwell", style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.MutedSilver, fontSize = 9.sp))
                            }
                            Stepper(value = luggageSmall, onMinus = { if (luggageSmall > 0) luggageSmall-- }, onPlus = { if (luggageSmall < 6) luggageSmall++ })
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // SECTION 2: ACCESSIBILITY & SPECIAL ASSISTANCE (Features 55, 56, 57, 69, 71)
                SectionTitle("2. ACCESSIBILITY & SPECIAL ASSISTANCE")
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = LuxuryColors.DeepCharcoal,
                    border = BorderStroke(1.dp, LuxuryColors.CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        PreferenceToggle(
                            title = "Wheelchair-Accessible Vehicle (WAV)",
                            subtitle = "Hydraulic hoist / low-gradient ramp & certified securements (Feature 55, 69)",
                            checked = wheelchairRequested,
                            onCheckedChange = { wheelchairRequested = it }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        PreferenceToggle(
                            title = "Step-Free Kerbside Boarding",
                            subtitle = "Vehicle lowers suspension to ≤ 32 cm curb height (Feature 56)",
                            checked = stepFreePickup,
                            onCheckedChange = { stepFreePickup = it }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        PreferenceToggle(
                            title = "Certified Assistance / Service Animal",
                            subtitle = "Guide dog space allocation & chauffeur briefing (Feature 57)",
                            checked = assistanceAnimal,
                            onCheckedChange = { assistanceAnimal = it }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Special Assistance Notes for Chauffeur (Feature 71)", style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.ChampagneGold, fontSize = 10.sp))
                        Spacer(modifier = Modifier.height(4.dp))
                        LuxuryTextField(
                            value = specialNotes,
                            onValueChange = { specialNotes = it },
                            placeholder = "e.g. Passenger uses folding wheelchair, curbside assistance required."
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // SECTION 3: CHILD SAFETY & PET-FRIENDLY (Features 54, 74)
                SectionTitle("3. CHILD SEAT & COMPANION SAFETY")
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = LuxuryColors.DeepCharcoal,
                    border = BorderStroke(1.dp, LuxuryColors.CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        PreferenceToggle(
                            title = "Child Safety Seat (Feature 54)",
                            subtitle = "ISOFIX certified premium child restraint",
                            checked = childSeatRequested,
                            onCheckedChange = { childSeatRequested = it }
                        )
                        if (childSeatRequested) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ChildSeatChip("INFANT_REAR_FACING", "Infant (0-13kg)", childSeatType == "INFANT_REAR_FACING") { childSeatType = "INFANT_REAR_FACING" }
                                ChildSeatChip("TODDLER_FORWARD", "Toddler (9-18kg)", childSeatType == "TODDLER_FORWARD") { childSeatType = "TODDLER_FORWARD" }
                                ChildSeatChip("BOOSTER_SEAT", "Booster (15-36kg)", childSeatType == "BOOSTER_SEAT") { childSeatType = "BOOSTER_SEAT" }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        PreferenceToggle(
                            title = "Pet-Friendly Ride (Feature 74)",
                            subtitle = "Vehicle equipped with leather protector liner & harness tether",
                            checked = petFriendly,
                            onCheckedChange = { petFriendly = it }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // SECTION 4: CABIN CLIMATE & ACOUSTIC TRANQUILITY (Features 58, 59, 60, 61, 62)
                SectionTitle("4. CABIN CLIMATE & ACOUSTICS")
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = LuxuryColors.DeepCharcoal,
                    border = BorderStroke(1.dp, LuxuryColors.CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        PreferenceToggle(
                            title = "Silent Discretion Mode (Feature 59)",
                            subtitle = "Chauffeur maintains tranquil silence unless spoken to",
                            checked = quietRide,
                            onCheckedChange = { quietRide = it }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        PreferenceToggle(
                            title = "No-Phone-Call Ride (Feature 62)",
                            subtitle = "In-app discreet messages only; no voice calls upon arrival",
                            checked = noPhoneCall,
                            onCheckedChange = { noPhoneCall = it }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        PreferenceToggle(
                            title = "Extra Legroom Lounge Position (Feature 58)",
                            subtitle = "Front passenger seat electrically forwarded for rear suite",
                            checked = extraLegroom,
                            onCheckedChange = { extraLegroom = it }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Preferred Cabin Climate (Feature 60)", style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.ChampagneGold, fontSize = 11.sp))
                            Text("${String.format("%.1f", cabinTemp)}°C", style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.ChampagneGold, fontWeight = FontWeight.Bold, fontSize = 12.sp))
                        }
                        Slider(
                            value = cabinTemp.toFloat(),
                            onValueChange = { cabinTemp = (Math.round(it * 2) / 2.0).toDouble() },
                            valueRange = 18f..26f,
                            steps = 15,
                            colors = SliderDefaults.colors(
                                thumbColor = LuxuryColors.ChampagneGold,
                                activeTrackColor = LuxuryColors.ChampagneGold,
                                inactiveTrackColor = LuxuryColors.CardBorder
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Curated Music Landscape (Feature 61)", style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.ChampagneGold, fontSize = 11.sp))
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            listOf("Classical Chamber", "Soft Jazz", "Acoustic Strings", "Ambient Lo-Fi", "Silence").forEach { track ->
                                FilterChip(
                                    selected = musicSelection == track,
                                    onClick = { musicSelection = track },
                                    label = { Text(track, fontSize = 10.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = LuxuryColors.ChampagneGold,
                                        selectedLabelColor = LuxuryColors.ObsidianBlack,
                                        containerColor = LuxuryColors.ObsidianBlack,
                                        labelColor = LuxuryColors.PlatinumWhite
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // SECTION 5: CHAUFFEUR & VEHICLE SPECIFICATIONS (Features 63, 64, 65, 66, 67, 68)
                SectionTitle("5. CHAUFFEUR ETIQUETTE & SUSTAINABILITY")
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = LuxuryColors.DeepCharcoal,
                    border = BorderStroke(1.dp, LuxuryColors.CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Chauffeur Language (Feature 63)", style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.ChampagneGold, fontSize = 11.sp))
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            listOf("English", "Hindi", "French", "German", "Spanish", "Japanese").forEach { lang ->
                                FilterChip(
                                    selected = driverLanguage == lang,
                                    onClick = { driverLanguage = lang },
                                    label = { Text(lang, fontSize = 10.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = LuxuryColors.ChampagneGold,
                                        selectedLabelColor = LuxuryColors.ObsidianBlack,
                                        containerColor = LuxuryColors.ObsidianBlack,
                                        labelColor = LuxuryColors.PlatinumWhite
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        PreferenceToggle(
                            title = "Female Chauffeur Preference (Feature 64)",
                            subtitle = "Dispatched where legally compliant and available",
                            checked = femaleDriver,
                            onCheckedChange = { femaleDriver = it }
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Vehicle Exterior Hue Preference (Feature 65)", style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.ChampagneGold, fontSize = 11.sp))
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Obsidian Black", "Pearl White", "Royal Navy", "Champagne Gold").forEach { col ->
                                FilterChip(
                                    selected = vehicleColor == col,
                                    onClick = { vehicleColor = col },
                                    label = { Text(col, fontSize = 9.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = LuxuryColors.ChampagneGold,
                                        selectedLabelColor = LuxuryColors.ObsidianBlack,
                                        containerColor = LuxuryColors.ObsidianBlack,
                                        labelColor = LuxuryColors.PlatinumWhite
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        PreferenceToggle(
                            title = "100% Electric Vehicle (EV) Preference (Feature 67)",
                            subtitle = "Zero exhaust emissions & whisper-quiet electric drive",
                            checked = evRide,
                            onCheckedChange = { evRide = it }
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Complimentary Executive Amenities (Feature 66)", style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.ChampagneGold, fontSize = 11.sp))
                        Spacer(modifier = Modifier.height(6.dp))
                        listOf(
                            "Evian Sparkling Mineral Water",
                            "Braided USB-C & Lightning Fast Chargers",
                            "Daily Financial Times & International Herald",
                            "Cashmere Travel Blanket",
                            "Chilled Peppermint Towel"
                        ).forEach { amenity ->
                            val isSel = selectedAmenities.contains(amenity)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isSel) selectedAmenities.remove(amenity)
                                        else selectedAmenities.add(amenity)
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSel,
                                    onCheckedChange = {
                                        if (it) selectedAmenities.add(amenity) else selectedAmenities.remove(amenity)
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = LuxuryColors.ChampagneGold,
                                        checkmarkColor = LuxuryColors.ObsidianBlack
                                    )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(amenity, style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp, color = LuxuryColors.PlatinumWhite))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
            }

            // Bottom Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val payload = CustomerPreferencesUpdatePayload(
                            quietRide = quietRide,
                            temperatureCelsius = cabinTemp,
                            preferredMusic = musicSelection,
                            noPhoneCallRide = noPhoneCall,
                            preferredDriverLanguage = driverLanguage,
                            femaleDriverPreferred = femaleDriver,
                            vehicleColorPreference = vehicleColor,
                            premiumAmenities = selectedAmenities.joinToString(", "),
                            electricVehiclePreferred = evRide,
                            wheelchairAccessRequired = wheelchairRequested,
                            stepFreePickupRequired = stepFreePickup,
                            assistanceAnimalPresent = assistanceAnimal,
                            extraLegroomRequired = extraLegroom,
                            petFriendlyPreferred = petFriendly,
                            accessibilityNotes = specialNotes.ifBlank { null }
                        )
                        onSavePreferencesToProfile(payload)
                        showProfileSavedBanner = true
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, LuxuryColors.ChampagneGold)
                ) {
                    Text("SAVE TO PROFILE", color = LuxuryColors.ChampagneGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        onApplyToBooking(
                            luggageLarge,
                            luggageSmall,
                            childSeatRequested,
                            childSeatType,
                            wheelchairRequested,
                            stepFreePickup,
                            assistanceAnimal,
                            extraLegroom,
                            quietRide,
                            cabinTemp,
                            musicSelection,
                            noPhoneCall,
                            driverLanguage,
                            femaleDriver,
                            vehicleColor,
                            selectedAmenities.joinToString(", "),
                            evRide,
                            petFriendly,
                            specialNotes
                        )
                        onDismiss()
                    },
                    modifier = Modifier.weight(1.2f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.ChampagneGold, contentColor = LuxuryColors.ObsidianBlack)
                ) {
                    Text("APPLY TO TRIP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = LuxuryTypography.BodySubtle.copy(
            color = LuxuryColors.ChampagneGold,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        ),
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun Stepper(value: Int, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = onMinus,
            modifier = Modifier
                .size(32.dp)
                .background(LuxuryColors.ObsidianBlack, CircleShape)
        ) {
            Text("-", color = LuxuryColors.ChampagneGold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            text = "$value",
            style = LuxuryTypography.HeadlineMedium.copy(fontSize = 14.sp, color = LuxuryColors.ChampagneGold),
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        IconButton(
            onClick = onPlus,
            modifier = Modifier
                .size(32.dp)
                .background(LuxuryColors.ObsidianBlack, CircleShape)
        ) {
            Text("+", color = LuxuryColors.ChampagneGold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PreferenceToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.PlatinumWhite, fontWeight = FontWeight.Bold, fontSize = 11.sp))
            Text(subtitle, style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.MutedSilver, fontSize = 9.sp))
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = LuxuryColors.ChampagneGold,
                checkedTrackColor = LuxuryColors.ChampagneGold.copy(alpha = 0.4f),
                uncheckedThumbColor = LuxuryColors.MutedSilver,
                uncheckedTrackColor = LuxuryColors.ObsidianBlack
            )
        )
    }
}

@Composable
private fun ChildSeatChip(typeCode: String, label: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) LuxuryColors.ChampagneGold else LuxuryColors.ObsidianBlack,
        border = BorderStroke(1.dp, if (isSelected) LuxuryColors.ChampagneGold else LuxuryColors.CardBorder),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            style = LuxuryTypography.BodySubtle.copy(
                fontSize = 9.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) LuxuryColors.ObsidianBlack else LuxuryColors.PlatinumWhite
            ),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}

// -------------------------------------------------------------
// 4. PRE-BOOKING PREFERENCE SUMMARY DIALOG (Feature 75)
// -------------------------------------------------------------
@Composable
fun PrebookingPreferenceSummaryDialog(
    summary: PrebookingPreferenceSummaryDto,
    onConfirmDispatch: () -> Unit,
    onAdjustPreferences: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(20.dp),
            color = LuxuryColors.ObsidianBlack,
            border = BorderStroke(1.dp, LuxuryColors.ChampagneGold)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "PRE-FLIGHT SANCTUARY AUDIT",
                            style = LuxuryTypography.HeadlineMedium.copy(
                                color = LuxuryColors.ChampagneGold,
                                fontSize = 17.sp,
                                letterSpacing = 1.sp
                            )
                        )
                        Text(
                            text = "GUEST PREFERENCE & ACCESSIBILITY SPECIFICATION",
                            style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp, color = LuxuryColors.MutedSilver)
                        )
                    }
                    GoldBadge("VERIFIED")
                }

                Spacer(modifier = Modifier.height(14.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SummarySectionCard("CABIN ATMOSPHERE & CLIMATE", summary.cabinAtmosphere, "❄ / ♫")
                    SummarySectionCard("CHAUFFEUR ETIQUETTE & DISCRETION", summary.serviceEtiquette, "🎩")
                    SummarySectionCard("ACCESSIBILITY & ASSISTANCE GUARANTEES", summary.accessibilityCompliance, "♿")
                    SummarySectionCard("AMENITIES & FLEET SPECIFICATIONS", summary.amenitiesAndFleet, "✦")
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onAdjustPreferences,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, LuxuryColors.ChampagneGold)
                    ) {
                        Text("ADJUST", color = LuxuryColors.ChampagneGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onConfirmDispatch,
                        modifier = Modifier.weight(1.5f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LuxuryColors.ChampagneGold,
                            contentColor = LuxuryColors.ObsidianBlack
                        )
                    ) {
                        Text("CONFIRM & DISPATCH", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SummarySectionCard(title: String, items: List<String>, icon: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = LuxuryColors.DeepCharcoal,
        border = BorderStroke(1.dp, LuxuryColors.CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, fontSize = 12.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    style = LuxuryTypography.BodySubtle.copy(
                        color = LuxuryColors.ChampagneGold,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            items.forEach { item ->
                Row(modifier = Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
                    Text("• ", color = LuxuryColors.ChampagneGold, fontSize = 10.sp)
                    Text(
                        text = item,
                        style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.PlatinumWhite, fontSize = 10.sp, lineHeight = 14.sp)
                    )
                }
            }
        }
    }
}
