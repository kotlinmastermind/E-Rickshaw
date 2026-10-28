package com.luxury.chauffeur.customer.feature.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.luxury.chauffeur.customer.core.designsystem.*
import com.luxury.chauffeur.customer.core.network.*
import com.luxury.chauffeur.customer.feature.fares.FareEstimateBreakdownDialog
import com.luxury.chauffeur.customer.feature.sharedride.SharedRideBookingDialog
import com.luxury.chauffeur.customer.feature.fares.VehicleClassComparisonDialog
import com.luxury.chauffeur.customer.feature.loyalty.*
import com.luxury.chauffeur.customer.feature.map.LuxuryMapView
import com.luxury.chauffeur.customer.feature.support.*
import com.luxury.chauffeur.customer.feature.vehicles.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class LuxuryPlace(val name: String, val address: String, val lat: Double, val lng: Double,
    val placeId: String? = null, val coordinatesVerified: Boolean = true, val providerMode: String = "CURATED_DIRECTORY")
data class SavedPinLocal(val id: String, val label: String, val address: String, val latitude: Double, val longitude: Double, val pinType: String)
data class LuxuryVehicleCategory(
    val id: String,
    val name: String,
    val desc: String,
    val capacity: Int,
    val fareRupees: Int,
    val code: String = "SEDAN",
    val legroomInches: Double = 40.5,
    val quietnessDb: Double = 54.0,
    val isElectric: Boolean = false,
    val wheelchairAccessible: Boolean = false,
    val petFriendly: Boolean = false,
    val luggageCapacity: String = "2 Large + 2 Small"
)

data class LocalStopInput(
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val dwellTimeMinutes: Int = 10,
    val passengerInstructions: String? = null,
    val entranceLandmark: String? = null
)

@SuppressLint("MissingPermission")
@Composable
fun LocationScreen(
    onRideRequested: (rideId: String, otp: String) -> Unit,
    onNavigateProfile: () -> Unit,
    onNavigateNotifications: () -> Unit,
    onNavigateGovernance: () -> Unit = {},
    onSharedRideBooked: (rideId: String, otp: String, pooledTripId: String, bookingId: String) -> Unit = { _, _, _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // Real device GPS states
    var pickupLatLng by remember { mutableStateOf(LatLng(18.9220, 72.8347)) }
    var pickupAddress by remember { mutableStateOf("Locating your distinguished presence...") }
    var isGpsLive by remember { mutableStateOf(false) }
    var pickupVerified by remember { mutableStateOf(false) }
    var gpsAccuracyMeters by remember { mutableStateOf<Float?>(null) }
    var isDetectingLocation by remember { mutableStateOf(false) }
    var showGpsGuide by remember { mutableStateOf(false) }
    var showSavePinDialog by remember { mutableStateOf(false) }
    var savedPinLabel by remember { mutableStateOf("") }
    var savedPinType by remember { mutableStateOf("PICKUP") }
    var savedPins by remember { mutableStateOf<List<SavedPinLocal>>(emptyList()) }
    var locationMessage by remember { mutableStateOf("") }
    var mapProviderDisclosure by remember { mutableStateOf("Map provider status is being checked…") }
    var locationEligibility by remember { mutableStateOf<Map<String, Any>?>(null) }
    var routeAlternatives by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var locationDataLoading by remember { mutableStateOf(false) }

    // Autocomplete & Destination states
    var destinationSearchQuery by remember { mutableStateOf("") }
    var selectedDestination by remember { mutableStateOf<LuxuryPlace?>(null) }
    var dynamicDestinations by remember { mutableStateOf<List<LuxuryPlace>>(emptyList()) }
    var isSearchingDestinations by remember { mutableStateOf(false) }

    // Recent destinations shortcuts (Feature 47)
    var recentDestinations by remember { mutableStateOf<List<RecentDestinationDto>>(emptyList()) }

    // Dialog flags
    var showTemplatesDialog by remember { mutableStateOf(false) }
    var showSharedLinkDialog by remember { mutableStateOf(false) }
    var showTrafficRecDialog by remember { mutableStateOf(false) }
    var showRecurringDialog by remember { mutableStateOf(false) }
    var showDependentsDialog by remember { mutableStateOf(false) }
    var showPickupPinDialog by remember { mutableStateOf(false) }
    var showDestPinDialog by remember { mutableStateOf(false) }
    var showLoyaltyDialog by remember { mutableStateOf(false) }
    var showReferralsDialog by remember { mutableStateOf(false) }
    var showOffersDialog by remember { mutableStateOf(false) }
    var showJournalsDialog by remember { mutableStateOf(false) }
    var showAirportZoneDialog by remember { mutableStateOf(false) }
    var showEntrancePinDialog by remember { mutableStateOf(false) }
    var showRouteAlternativesDialog by remember { mutableStateOf(false) }
    var showGpsRecoveryDialog by remember { mutableStateOf(false) }
    var showMapAttributionDialog by remember { mutableStateOf(false) }

    // Passenger selection (Features 26, 27, 45)
    var bookingForSelf by remember { mutableStateOf(true) }
    var isBookingForDependent by remember { mutableStateOf(false) }
    var passengerName by remember { mutableStateOf("") }
    var passengerPhone by remember { mutableStateOf("") }
    var passengerRelationship by remember { mutableStateOf("Colleague") }
    var selectedDependentId by remember { mutableStateOf<String?>(null) }

    // Booking profile mode (Features 43, 44)
    var bookingMode by remember { mutableStateOf("STANDARD") } // STANDARD, HOTEL_CONCIERGE, BUSINESS
    var hotelRoomNumber by remember { mutableStateOf("") }
    var hotelGuestName by remember { mutableStateOf("") }
    var businessCostCenter by remember { mutableStateOf("") }
    var businessProjectCode by remember { mutableStateOf("") }
    var businessTaxId by remember { mutableStateOf("") }

    // Driver & Landmark Guidance (Features 33, 34, 35)
    var showGuidanceSection by remember { mutableStateOf(false) }
    var pickupNotes by remember { mutableStateOf("") }
    var destinationNotes by remember { mutableStateOf("") }
    var entranceGuidance by remember { mutableStateOf("") }
    var landmarkGuidance by remember { mutableStateOf("") }

    // Multi-stops (Features 36, 37, 38)
    var showAddStopModal by remember { mutableStateOf(false) }
    var intermediateStops by remember { mutableStateOf<List<LocalStopInput>>(emptyList()) }
    var stopAddressInput by remember { mutableStateOf("") }
    var stopDwellMinutes by remember { mutableIntStateOf(10) }
    var stopInstructions by remember { mutableStateOf("") }
    var stopLandmark by remember { mutableStateOf("") }

    // Return trip & Round trip (Features 28, 39)
    var isRoundTrip by remember { mutableStateOf(false) }
    var isReturnTripScheduled by remember { mutableStateOf(false) }
    var returnOffsetHours by remember { mutableIntStateOf(4) }

    // Airport flight telemetry (Features 40, 41, 42)
    var isAirportBooking by remember { mutableStateOf(false) }
    var airportTerminal by remember { mutableStateOf("Terminal 2 (VIP Arrivals)") }
    var airlineCode by remember { mutableStateOf("EK") }
    var flightNumberInput by remember { mutableStateOf("EK504") }
    var flightTelemetry by remember { mutableStateOf<FlightStatusResponse?>(null) }
    var isCheckingFlight by remember { mutableStateOf(false) }

    // Categories & Ride options
    var dynamicCategories by remember {
        mutableStateOf(
            listOf(
                LuxuryVehicleCategory("cat_1", "Standard E-Rickshaw", "Eco-friendly electric 3-wheeler for quick city commutes and local errands", 4, 20, "E_RICKSHAW_STANDARD", 36.0, 45.0, true, false, true, "1 Bag"),
                LuxuryVehicleCategory("cat_2", "Shared E-Rickshaw (Eco-Pool)", "Ultra-affordable per-seat shared electric rickshaw on metro feeder routes", 4, 10, "E_RICKSHAW_SHARE", 35.0, 45.0, true, false, false, "Small Bag"),
                LuxuryVehicleCategory("cat_3", "High-Speed Electric Auto", "Modern enclosed L5M electric auto rickshaw with weather doors", 3, 30, "E_AUTO", 38.0, 44.0, true, true, true, "2 Bags"),
                LuxuryVehicleCategory("cat_4", "E-Loader Cargo", "Heavy-duty electric cargo loader for parcels, shop goods, and heavy luggage", 1, 50, "E_CARGO_LOADER", 34.0, 46.0, true, false, false, "400kg Cargo")
            )
        )
    }
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }
    var isCalculatingFare by remember { mutableStateOf(false) }
    var isSharedRide by remember { mutableStateOf(false) }
    var showSharedRideFlow by remember { mutableStateOf(false) }
    var selectedSeats by remember { mutableIntStateOf(1) }
    var serverEstimateId by remember { mutableStateOf<String?>(null) }
    var serverFareRupees by remember { mutableStateOf<Int?>(null) }
    var isBookingInProgress by remember { mutableStateOf(false) }

    // Part 3 Bespoke Customization & Preferences State (Features 51-75)
    var showVehicleGalleryModal by remember { mutableStateOf(false) }
    var galleryCategoryCode by remember { mutableStateOf("E_RICKSHAW_STANDARD") }
    var galleryCategoryName by remember { mutableStateOf("Standard E-Rickshaw") }
    var showFeatureComparisonSheet by remember { mutableStateOf(false) }
    var showBespokeCustomizationSheet by remember { mutableStateOf(false) }
    var showPrebookingSummaryDialog by remember { mutableStateOf(false) }
    var prebookingSummaryData by remember { mutableStateOf<PrebookingPreferenceSummaryDto?>(null) }
    var activeCategoryFilter by remember { mutableStateOf("ALL") }

    var luggageLargeCount by remember { mutableIntStateOf(1) }
    var luggageSmallCount by remember { mutableIntStateOf(1) }
    var childSeatRequested by remember { mutableStateOf(false) }
    var childSeatType by remember { mutableStateOf("TODDLER_FORWARD") }
    var wheelchairRequested by remember { mutableStateOf(false) }
    var stepFreePickup by remember { mutableStateOf(false) }
    var assistanceAnimal by remember { mutableStateOf(false) }
    var extraLegroomRequested by remember { mutableStateOf(false) }
    var quietRideRequested by remember { mutableStateOf(true) }
    var cabinTempRequested by remember { mutableDoubleStateOf(21.0) }
    var musicRequested by remember { mutableStateOf("Classical Chamber") }
    var noPhoneCall by remember { mutableStateOf(false) }
    var driverLanguageRequested by remember { mutableStateOf("English") }
    var femaleDriverRequested by remember { mutableStateOf(false) }
    var vehicleColorRequested by remember { mutableStateOf("Obsidian Black") }
    var amenitiesRequested by remember { mutableStateOf("Evian Mineral Water, Braided Charger") }
    var evRideRequested by remember { mutableStateOf(false) }
    var petFriendlyRequested by remember { mutableStateOf(false) }
    var specialAssistanceNotes by remember { mutableStateOf("") }

    // Part 4 Fares & Settlements State (Features 76-82, 98-99)
    var fullEstimateResponse by remember { mutableStateOf<FareEstimateResponse?>(null) }
    var showFareBreakdownDialog by remember { mutableStateOf(false) }
    var showVehicleComparisonDialog by remember { mutableStateOf(false) }
    var vehicleComparisons by remember { mutableStateOf<List<VehicleFareComparisonDto>>(emptyList()) }
    var isPriceLocked by remember { mutableStateOf(false) }
    var promoCodeInput by remember { mutableStateOf("") }
    var promoEligibilityResult by remember { mutableStateOf<PromoEligibilityResponse?>(null) }
    var isVerifyingPromo by remember { mutableStateOf(false) }
    var expiringPromos by remember { mutableStateOf<List<ExpiringPromoDto>>(emptyList()) }

    // Confirmation calendar invite prompt state (Features 30, 50)
    var completedRideIdForCalendar by remember { mutableStateOf<String?>(null) }

    // Part 8 Support, Concierge & Help Center State (Features 176 - 200)
    var showHelpCenterDialog by remember { mutableStateOf(false) }
    var showSupportTicketsDialog by remember { mutableStateOf(false) }
    var showSelfServiceDialog by remember { mutableStateOf(false) }
    var showGuidedCancellationDialog by remember { mutableStateOf(false) }
    var helpContextTag by remember { mutableStateOf<String?>(null) }
    var activeIncidents by remember { mutableStateOf<List<ServiceIncidentDto>>(emptyList()) }

    LaunchedEffect(Unit) {
        try {
            val incRes = ApiClient.getService().getActiveIncidentBanners()
            if (incRes.isSuccessful && incRes.body() != null) {
                activeIncidents = incRes.body()!!
            }
        } catch (_: Exception) {}
    }

    val defaultCuratedDestinations = remember {
        listOf(
            LuxuryPlace("CSM T2 VIP Private Terminal", "Sahar, Andheri East, Mumbai", 19.0974, 72.8744),
            LuxuryPlace("The Oberoi Grand Pavilion", "Nariman Point, Marine Drive, Mumbai", 18.9272, 72.8205),
            LuxuryPlace("Bandra Kurla Financial Enclave", "G Block BKC, Bandra East, Mumbai", 19.0657, 72.8687),
            LuxuryPlace("The St. Regis Penthouse Suites", "462 Senapati Bapat Marg, Lower Parel, Mumbai", 18.9934, 72.8242)
        )
    }

    // Reservation dispatch used by both the booking button and confirmation dialog.
    fun executeBespokeReservation() {
        if (isBookingInProgress) return
        isBookingInProgress = true
        val dest = selectedDestination ?: defaultCuratedDestinations.first()
        val estId = serverEstimateId ?: "est_${System.currentTimeMillis()}"

        val stopsDto = intermediateStops.map {
            IntermediateStopInputDto(
                address = it.address,
                latitude = it.latitude,
                longitude = it.longitude,
                dwellTimeMinutes = it.dwellTimeMinutes,
                passengerInstructions = it.passengerInstructions,
                entranceLandmark = it.entranceLandmark
            )
        }

        val payload = AdvancedRideRequestPayload(
            estimateId = estId,
            pickupAddress = pickupAddress,
            destinationAddress = dest.address,
            paymentMethod = "UPI",
            bookingForSelf = bookingForSelf,
            passengerName = if (!bookingForSelf) passengerName.ifBlank { null } else null,
            passengerPhone = if (!bookingForSelf) passengerPhone.ifBlank { null } else null,
            passengerRelationship = if (!bookingForSelf) passengerRelationship else null,
            isReturnTrip = isReturnTripScheduled,
            returnPickupOffsetHours = if (isReturnTripScheduled) returnOffsetHours else null,
            isRoundTrip = isRoundTrip,
            pickupNotes = pickupNotes.ifBlank { null },
            destinationNotes = destinationNotes.ifBlank { null },
            entranceGuidance = entranceGuidance.ifBlank { null },
            landmarkGuidance = landmarkGuidance.ifBlank { null },
            airportCode = if (isAirportBooking) "BOM" else null,
            terminalName = if (isAirportBooking) airportTerminal else null,
            airlineCode = if (isAirportBooking) airlineCode else null,
            flightNumber = if (isAirportBooking) flightNumberInput else null,
            bookingMode = bookingMode,
            hotelRoomNumber = if (bookingMode == "HOTEL_CONCIERGE") hotelRoomNumber.ifBlank { null } else null,
            hotelGuestName = if (bookingMode == "HOTEL_CONCIERGE") hotelGuestName.ifBlank { null } else null,
            businessCostCenter = if (bookingMode == "BUSINESS") businessCostCenter.ifBlank { null } else null,
            businessProjectCode = if (bookingMode == "BUSINESS") businessProjectCode.ifBlank { null } else null,
            businessTaxId = if (bookingMode == "BUSINESS") businessTaxId.ifBlank { null } else null,
            dependentId = selectedDependentId,
            stops = stopsDto.ifEmpty { null },
            // Part 3 Bespoke Parameters (Features 53-75)
            childSeatRequested = childSeatRequested,
            childSeatType = if (childSeatRequested) childSeatType else null,
            luggageLargeCount = luggageLargeCount,
            luggageSmallCount = luggageSmallCount,
            wheelchairRequested = wheelchairRequested,
            stepFreePickup = stepFreePickup,
            assistanceAnimal = assistanceAnimal,
            extraLegroomRequested = extraLegroomRequested,
            quietRideRequested = quietRideRequested,
            cabinTempRequested = cabinTempRequested,
            musicRequested = musicRequested,
            noPhoneCall = noPhoneCall,
            driverLanguageRequested = driverLanguageRequested,
            femaleDriverRequested = femaleDriverRequested,
            vehicleColorRequested = vehicleColorRequested,
            amenitiesRequested = amenitiesRequested,
            evRideRequested = evRideRequested,
            petFriendlyRequested = petFriendlyRequested,
            specialAssistanceNotes = specialAssistanceNotes.ifBlank { null }
        )

        scope.launch {
            try {
                val res = ApiClient.getService().requestAdvancedRide(payload)
                if (res.isSuccessful && res.body() != null) {
                    val b = res.body()!!
                    val rideId = b["ride_id"] as? String
                    val otp = b["start_otp"] as? String ?: "849201"
                    if (!rideId.isNullOrBlank()) {
                        onRideRequested(rideId, otp)
                    } else {
                        val errMsg = (b["error"] ?: b["detail"] ?: "Unable to confirm ride reservation. Please retry.") as String
                        Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show()
                    }
                } else {
                    val rawErr = res.errorBody()?.string() ?: ""
                    val errMsg = if (rawErr.contains("detail")) {
                        try {
                            org.json.JSONObject(rawErr).optString("detail", rawErr)
                        } catch (_: Exception) { rawErr }
                    } else {
                        "Reservation failed (HTTP ${res.code()}). Please retry."
                    }
                    Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Network error: ${e.localizedMessage ?: "Unable to connect"}", Toast.LENGTH_LONG).show()
            } finally {
                isBookingInProgress = false
            }
        }
    }

    // Acquire real device GPS and reverse-geocode with backend
    fun fetchLiveDeviceLocation() {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            pickupAddress = "Choose your pickup pin or enable location access"
            isGpsLive = false
            pickupVerified = false
            return
        }

        isDetectingLocation = true
        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location ->
                    isDetectingLocation = false
                    if (location != null) {
                        pickupLatLng = LatLng(location.latitude, location.longitude)
                        isGpsLive = true
                        pickupVerified = true
                        gpsAccuracyMeters = if (location.hasAccuracy()) location.accuracy else null
                        scope.launch {
                            try {
                                val revRes = ApiClient.getService().reverseGeocode(
                                    mapOf("latitude" to location.latitude, "longitude" to location.longitude)
                                )
                                if (revRes.isSuccessful && revRes.body() != null) {
                                    val formatted = revRes.body()!!["formatted_address"] as? String
                                    if (!formatted.isNullOrBlank()) {
                                        pickupAddress = formatted
                                    }
                                }
                            } catch (_: Exception) {
                                pickupAddress = "Near Lat: %.4f, Lng: %.4f".format(location.latitude, location.longitude)
                            }
                        }
                    } else {
                        fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                            if (lastLoc != null) {
                                pickupLatLng = LatLng(lastLoc.latitude, lastLoc.longitude)
                                isGpsLive = true
                                pickupVerified = true
                                gpsAccuracyMeters = if (lastLoc.hasAccuracy()) lastLoc.accuracy else null
                                scope.launch {
                                    try {
                                        val revRes = ApiClient.getService().reverseGeocode(
                                            mapOf("latitude" to lastLoc.latitude, "longitude" to lastLoc.longitude)
                                        )
                                        if (revRes.isSuccessful && revRes.body() != null) {
                                            val formatted = revRes.body()!!["formatted_address"] as? String
                                            if (!formatted.isNullOrBlank()) {
                                                pickupAddress = formatted
                                            }
                                        }
                                    } catch (_: Exception) {
                                        pickupAddress = "Near Lat: %.4f, Lng: %.4f".format(lastLoc.latitude, lastLoc.longitude)
                                    }
                                }
                            } else {
                                isGpsLive = false
                                pickupAddress = "Location unavailable • choose a pickup pin"
                            }
                        }
                    }
                }
                .addOnFailureListener {
                    isDetectingLocation = false
                    isGpsLive = false
                    pickupAddress = "Location unavailable • choose a pickup pin"
                }
        } catch (_: Exception) {
            isDetectingLocation = false
            isGpsLive = false
            pickupAddress = "Location unavailable • choose a pickup pin"
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            fetchLiveDeviceLocation()
        } else {
            isGpsLive = false
            pickupVerified = false
            pickupAddress = "Location access denied • choose a pickup pin"
            showGpsGuide = true
        }
    }

    LaunchedEffect(Unit) {
        try {
            val api = ApiClient.getService()
            val health = api.getMapProviderHealth()
            if (health.isSuccessful) {
                val body = health.body().orEmpty()
                mapProviderDisclosure = "${body["provider"] ?: "Map"} · ${body["status"] ?: "status unknown"}. ${body["message"] ?: "Map provider disclosure"}"
            } else mapProviderDisclosure = "Map provider health is unavailable; route and place estimates may be approximate."
            val pinsResponse = api.getSavedLocationPins()
            if (pinsResponse.isSuccessful) savedPins = pinsResponse.body().orEmpty().mapNotNull { p ->
                val id = p["id"] as? String ?: return@mapNotNull null
                SavedPinLocal(id, p["label"] as? String ?: "Saved pin", p["address"] as? String ?: "",
                    (p["latitude"] as? Number)?.toDouble() ?: return@mapNotNull null,
                    (p["longitude"] as? Number)?.toDouble() ?: return@mapNotNull null, p["pin_type"] as? String ?: "PICKUP")
            }
        } catch (_: Exception) { mapProviderDisclosure = "Map provider unavailable. Saved pins will load when connectivity returns." }
    }

    LaunchedEffect(pickupLatLng, selectedDestination) {
        val destination = selectedDestination ?: return@LaunchedEffect
        if (!pickupVerified || !destination.coordinatesVerified) return@LaunchedEffect
        while (true) {
            locationDataLoading = true
            try {
                val api = ApiClient.getService()
                val eligibility = api.checkLocationEligibility(mapOf(
                    "latitude" to pickupLatLng.latitude, "longitude" to pickupLatLng.longitude,
                    "destination_latitude" to destination.lat, "destination_longitude" to destination.lng))
                if (eligibility.isSuccessful) locationEligibility = eligibility.body()
                val alternatives = api.getRouteAlternatives(pickupLatLng.latitude, pickupLatLng.longitude, destination.lat, destination.lng)
                if (alternatives.isSuccessful) routeAlternatives = (alternatives.body()?.get("routes") as? List<*>)?.mapNotNull { it as? Map<String, Any> }.orEmpty()
            } catch (_: Exception) { locationMessage = "Route refresh unavailable. Your selected pin is unchanged." }
            locationDataLoading = false
            delay(60_000)
        }
    }

    fun chooseVerifiedPlace(place: LuxuryPlace) {
        scope.launch {
            var resolved = place
            if (!place.coordinatesVerified && !place.placeId.isNullOrBlank()) {
                locationDataLoading = true
                try {
                    val response = ApiClient.getService().verifyPlaceDetails(mapOf("place_id" to place.placeId))
                    val data = response.body().orEmpty()
                    if (response.isSuccessful && data["available"] == true) {
                        val lat = (data["lat"] as? Number)?.toDouble()
                        val lng = (data["lng"] as? Number)?.toDouble()
                        if (lat != null && lng != null) resolved = place.copy(
                            name = data["name"] as? String ?: place.name,
                            address = data["address"] as? String ?: place.address,
                            lat = lat, lng = lng,
                            coordinatesVerified = data["provider_verified"] == true,
                            providerMode = data["provider_mode"] as? String ?: place.providerMode)
                    }
                } catch (_: Exception) { locationMessage = "Place coordinate lookup failed. Confirm the pin manually before booking." }
                locationDataLoading = false
            }
            selectedDestination = resolved
            destinationSearchQuery = resolved.name
            if (!resolved.coordinatesVerified) showDestPinDialog = true
        }
    }

    // Initial load: GPS, Vehicle categories, and Recent destination shortcuts (Feature 47)
    LaunchedEffect(Unit) {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasFine || hasCoarse) {
            fetchLiveDeviceLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }

        try {
            val catRes = ApiClient.getService().getVehicleCategories()
            if (catRes.isSuccessful && !catRes.body().isNullOrEmpty()) {
                val cats = catRes.body()!!.map { dto ->
                    val safeCode = dto.code ?: "E_RICKSHAW_STANDARD"
                    val fare = when (safeCode.uppercase()) {
                        "E_RICKSHAW_SHARE", "SHARE", "POOL" -> 10
                        "E_AUTO", "AUTO" -> 30
                        "E_CARGO_LOADER", "CARGO", "LOADER" -> 50
                        else -> 20
                    }
                    LuxuryVehicleCategory(
                        id = dto.id ?: "cat_1",
                        name = dto.name ?: "Standard E-Rickshaw",
                        desc = dto.description ?: "Clean Electric Transit",
                        capacity = dto.capacity,
                        fareRupees = fare,
                        code = safeCode,
                        legroomInches = dto.legroomInches ?: 42.0,
                        quietnessDb = dto.quietnessDb ?: 50.0,
                        isElectric = dto.isElectric ?: false,
                        wheelchairAccessible = dto.wheelchairAccessible ?: false,
                        petFriendly = dto.petFriendly ?: false,
                        luggageCapacity = "${dto.luggageLargeCapacity ?: 2} Large + ${dto.luggageSmallCapacity ?: 2} Small"
                    )
                }
                dynamicCategories = cats
            }
        } catch (_: Exception) {}

        // Load saved customer preferences into booking parameters (Feature 70)
        try {
            val prefRes = ApiClient.getService().getCustomerPreferences()
            if (prefRes.isSuccessful && prefRes.body() != null) {
                val p = prefRes.body()!!
                quietRideRequested = p.quietRide
                cabinTempRequested = p.temperatureCelsius
                p.preferredMusic?.let { musicRequested = it }
                noPhoneCall = p.noPhoneCallRide
                p.preferredDriverLanguage?.let { driverLanguageRequested = it }
                femaleDriverRequested = p.femaleDriverPreferred
                p.vehicleColorPreference?.let { vehicleColorRequested = it }
                p.premiumAmenities?.let { amenitiesRequested = it }
                evRideRequested = p.electricVehiclePreferred
                wheelchairRequested = p.wheelchairAccessRequired
                stepFreePickup = p.stepFreePickupRequired
                assistanceAnimal = p.assistanceAnimalPresent
                extraLegroomRequested = p.extraLegroomRequired
                petFriendlyRequested = p.petFriendlyPreferred
                p.accessibilityNotes?.let { specialAssistanceNotes = it }
            }
        } catch (_: Exception) {}

        // Load recent destination shortcuts (Feature 47)
        try {
            val recRes = ApiClient.getService().getRecentDestinations()
            if (recRes.isSuccessful && !recRes.body().isNullOrEmpty()) {
                recentDestinations = recRes.body()!!
            }
        } catch (_: Exception) {}

        // Load expiring promo reminders (Feature 99)
        try {
            val promoRes = ApiClient.getService().getExpiringPromos()
            if (promoRes.isSuccessful && !promoRes.body().isNullOrEmpty()) {
                expiringPromos = promoRes.body()!!
            }
        } catch (_: Exception) {}
    }

    // Autocomplete Search
    LaunchedEffect(destinationSearchQuery) {
        val query = destinationSearchQuery.trim()
        if (query.length >= 2) {
            delay(300)
            isSearchingDestinations = true
            try {
                val autoRes = ApiClient.getService().autocompletePlaces(query)
                if (autoRes.isSuccessful && autoRes.body() != null) {
                    val rawResults = autoRes.body()!!["results"] as? List<*>
                    if (rawResults != null) {
                        val parsed = rawResults.mapNotNull { item ->
                            val map = item as? Map<*, *> ?: return@mapNotNull null
                            val name = map["name"] as? String ?: return@mapNotNull null
                            val address = map["address"] as? String ?: name
                            val lat = (map["lat"] as? Number)?.toDouble() ?: 19.0760
                            val lng = (map["lng"] as? Number)?.toDouble() ?: 72.8777
                            LuxuryPlace(name = name, address = address, lat = lat, lng = lng)
                        }
                        dynamicDestinations = parsed
                    }
                }
            } catch (_: Exception) {
                dynamicDestinations = defaultCuratedDestinations.filter {
                    it.name.contains(query, ignoreCase = true) || it.address.contains(query, ignoreCase = true)
                }
            } finally {
                isSearchingDestinations = false
            }
        } else {
            dynamicDestinations = emptyList()
            isSearchingDestinations = false
        }
    }

    // Dynamic Fare Calculation
    LaunchedEffect(selectedDestination, selectedCategoryIndex, isSharedRide, selectedSeats, pickupLatLng, intermediateStops.size, isRoundTrip) {
        val dest = selectedDestination ?: defaultCuratedDestinations.first()
        val cat = dynamicCategories.getOrNull(selectedCategoryIndex) ?: dynamicCategories.first()
        isCalculatingFare = true
        try {
            if (isSharedRide) {
                val res = ApiClient.getService().getSharedEstimate(
                    SharedEstimateRequest(
                        categoryId = cat.id,
                        pickupLatitude = pickupLatLng.latitude,
                        pickupLongitude = pickupLatLng.longitude,
                        destinationLatitude = dest.lat,
                        destinationLongitude = dest.lng,
                        seatsRequested = selectedSeats
                    )
                )
                if (res.isSuccessful && res.body() != null) {
                    val b = res.body()!!
                    serverEstimateId = b.estimateId
                    var fare = b.totalPayableCents / 100
                    if (isRoundTrip) fare = (fare * 1.85).toInt()
                    fare += intermediateStops.size * 250
                    serverFareRupees = fare
                }
            } else {
                val res = ApiClient.getService().calculateFareEstimate(
                    FareEstimateRequest(
                        categoryId = cat.id,
                        pickupLatitude = pickupLatLng.latitude,
                        pickupLongitude = pickupLatLng.longitude,
                        destinationLatitude = dest.lat,
                        destinationLongitude = dest.lng
                    )
                )
                if (res.isSuccessful && res.body() != null) {
                    val b = res.body()!!
                    fullEstimateResponse = b
                    serverEstimateId = b.estimateId
                    var fare = b.estimatedFareCents / 100
                    if (isRoundTrip) fare = (fare * 1.85).toInt()
                    fare += intermediateStops.size * 350
                    serverFareRupees = fare
                }
            }
        } catch (_: Exception) {
        } finally {
            isCalculatingFare = false
        }
    }

    // Feature 78: Compare all vehicle tiers
    fun loadComparisons() {
        val dest = selectedDestination ?: defaultCuratedDestinations.first()
        scope.launch {
            try {
                val compRes = ApiClient.getService().compareVehicleFares(
                    FareCompareRequest(
                        pickupLatitude = pickupLatLng.latitude,
                        pickupLongitude = pickupLatLng.longitude,
                        destinationLatitude = dest.lat,
                        destinationLongitude = dest.lng
                    )
                )
                if (compRes.isSuccessful && !compRes.body().isNullOrEmpty()) {
                    vehicleComparisons = compRes.body()!!
                    showVehicleComparisonDialog = true
                }
            } catch (_: Exception) {
                Toast.makeText(context, "Fleet comparisons available when fleet is connected.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Dialog components
    if (showTemplatesDialog) {
        val dest = selectedDestination ?: defaultCuratedDestinations.first()
        TripTemplatesDialog(
            currentPickupAddress = pickupAddress,
            currentPickupLatLng = pickupLatLng,
            currentDestAddress = dest.address,
            currentDestLatLng = LatLng(dest.lat, dest.lng),
            onDismiss = { showTemplatesDialog = false },
            onApplyTemplate = { tmpl ->
                pickupAddress = tmpl.pickupAddress
                pickupLatLng = LatLng(tmpl.pickupLatitude, tmpl.pickupLongitude)
                selectedDestination = LuxuryPlace(tmpl.title, tmpl.destinationAddress, tmpl.destinationLatitude, tmpl.destinationLongitude)
                destinationSearchQuery = tmpl.title
                if (!tmpl.notes.isNullOrBlank()) {
                    pickupNotes = tmpl.notes
                    showGuidanceSection = true
                }
                Toast.makeText(context, "Applied Template: ${tmpl.title}", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showSharedLinkDialog) {
        SharedLocationDialog(
            onDismiss = { showSharedLinkDialog = false },
            onLocationResolved = { lat, lng, label, isDest ->
                if (isDest) {
                    selectedDestination = LuxuryPlace(label, label, lat, lng)
                    destinationSearchQuery = label
                    Toast.makeText(context, "Destination set from shared link", Toast.LENGTH_SHORT).show()
                } else {
                    pickupLatLng = LatLng(lat, lng)
                    pickupAddress = label
                    Toast.makeText(context, "Pickup set from shared link", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (showTrafficRecDialog) {
        val dest = selectedDestination ?: defaultCuratedDestinations.first()
        TrafficRecommendationDialog(
            pickupLatLng = pickupLatLng,
            destLatLng = LatLng(dest.lat, dest.lng),
            onDismiss = { showTrafficRecDialog = false }
        )
    }

    if (showRecurringDialog) {
        val dest = selectedDestination ?: defaultCuratedDestinations.first()
        val cat = dynamicCategories.getOrNull(selectedCategoryIndex) ?: dynamicCategories.first()
        RecurringScheduleDialog(
            pickupAddress = pickupAddress,
            pickupLatLng = pickupLatLng,
            destAddress = dest.address,
            destLatLng = LatLng(dest.lat, dest.lng),
            categoryId = cat.id,
            onDismiss = { showRecurringDialog = false }
        )
    }

    if (showDependentsDialog) {
        DependentsDialog(
            onDismiss = { showDependentsDialog = false },
            onSelectDependent = { dep ->
                bookingForSelf = false
                isBookingForDependent = true
                selectedDependentId = dep.id
                passengerName = dep.fullName
                passengerPhone = dep.phoneNumber
                passengerRelationship = dep.relationship
                if (!dep.emergencyNotes.isNullOrBlank()) {
                    pickupNotes = "Special Care: ${dep.emergencyNotes}"
                    showGuidanceSection = true
                }
                Toast.makeText(context, "Passenger set to dependent: ${dep.fullName}", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showPickupPinDialog) {
        MapPinAdjustDialog(
            initialLatLng = pickupLatLng,
            title = "Adjust Pickup Pin",
            onDismiss = { showPickupPinDialog = false },
            onConfirm = { newLatLng, newAddress ->
                pickupLatLng = newLatLng
                pickupAddress = newAddress
                Toast.makeText(context, "Pickup location updated", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showDestPinDialog) {
        val dest = selectedDestination ?: defaultCuratedDestinations.first()
        MapPinAdjustDialog(
            initialLatLng = LatLng(dest.lat, dest.lng),
            title = "Select Destination on Map",
            onDismiss = { showDestPinDialog = false },
            onConfirm = { newLatLng, newAddress ->
                selectedDestination = LuxuryPlace(newAddress, newAddress, newLatLng.latitude, newLatLng.longitude)
                destinationSearchQuery = newAddress
                Toast.makeText(context, "Destination selected on map", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Modal for Adding Intermediate Stop (Features 36, 37, 38)
    if (showAddStopModal) {
        AlertDialog(
            onDismissRequest = { showAddStopModal = false },
            containerColor = LuxuryColors.CardSurface,
            title = { Text("ADD INTERMEDIATE WAYPOINT", style = LuxuryTypography.TitleGold.copy(fontSize = 15.sp)) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = stopAddressInput,
                        onValueChange = { stopAddressInput = it },
                        label = { Text("Stop Address / Venue") },
                        placeholder = { Text("e.g. Trident Hotel Nariman Point", color = LuxuryColors.SubtleGray) },
                        textStyle = LuxuryTypography.BodyRegular,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Dwell Time (Chauffeur Waiting Period):", style = LuxuryTypography.BodySubtle.copy(fontWeight = FontWeight.Bold))
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(5, 10, 15, 30, 45, 60).forEach { mins ->
                            val isSelected = stopDwellMinutes == mins
                            Button(
                                onClick = { stopDwellMinutes = mins },
                                colors = ButtonDefaults.buttonColors(containerColor = if (isSelected) LuxuryColors.ChampagneGold else LuxuryColors.CardBorder),
                                modifier = Modifier.weight(1f).height(30.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("${mins}m", fontSize = 10.sp, color = if (isSelected) LuxuryColors.ObsidianBlack else LuxuryColors.PlatinumWhite)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = stopInstructions,
                        onValueChange = { stopInstructions = it },
                        label = { Text("Passenger & Chauffeur Instructions") },
                        placeholder = { Text("e.g. Wait at lobby with documents", color = LuxuryColors.SubtleGray) },
                        textStyle = LuxuryTypography.BodyRegular,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = stopLandmark,
                        onValueChange = { stopLandmark = it },
                        label = { Text("Entrance & Landmark Guidance") },
                        placeholder = { Text("e.g. East Gate fountain entrance", color = LuxuryColors.SubtleGray) },
                        textStyle = LuxuryTypography.BodyRegular,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (stopAddressInput.isNotBlank()) {
                            intermediateStops = intermediateStops + LocalStopInput(
                                address = stopAddressInput.trim(),
                                latitude = 19.0760,
                                longitude = 72.8777,
                                dwellTimeMinutes = stopDwellMinutes,
                                passengerInstructions = stopInstructions.ifBlank { null },
                                entranceLandmark = stopLandmark.ifBlank { null }
                            )
                            stopAddressInput = ""
                            stopInstructions = ""
                            stopLandmark = ""
                            showAddStopModal = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.ChampagneGold)
                ) {
                    Text("ADD STOP", color = LuxuryColors.ObsidianBlack, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddStopModal = false }) {
                    Text("CANCEL", color = LuxuryColors.PlatinumWhite)
                }
            }
        )
    }

    Scaffold(
        containerColor = LuxuryColors.ObsidianBlack,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "GRAND CHAUFFEUR",
                    style = LuxuryTypography.HeadlineMedium.copy(color = LuxuryColors.ChampagneGold, letterSpacing = 2.sp, fontSize = 18.sp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = onNavigateNotifications) {
                        Text("Alerts", color = LuxuryColors.PlatinumWhite, fontSize = 13.sp)
                    }
                    TextButton(onClick = onNavigateProfile) {
                        Text("Profile", color = LuxuryColors.PlatinumWhite, fontSize = 13.sp)
                    }
                    TextButton(onClick = onNavigateGovernance) {
                        Text("VIP Suite", color = LuxuryColors.ChampagneGold, fontSize = 13.sp)
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Horizontal Quick Actions Bar (Features 46, 47, 49, 31, 29, 176, 180, 185)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
            ) {
                item {
                    Surface(
                        onClick = { showAirportZoneDialog = true },
                        shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                        color = LuxuryColors.CardSurface,
                        border = BorderStroke(1.dp, LuxuryColors.ChampagneGold)
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("✈ AIRPORT VIP ZONES", style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.ChampagneGold, fontWeight = FontWeight.Bold, fontSize = 10.sp))
                        }
                    }
                }
                item {
                    Surface(
                        onClick = { showEntrancePinDialog = true },
                        shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                        color = LuxuryColors.CardSurface,
                        border = BorderStroke(1.dp, LuxuryColors.ChampagneGold.copy(alpha = 0.6f))
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("🏛 ENTRANCE & GATES", style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.ChampagneGold, fontWeight = FontWeight.Bold, fontSize = 10.sp))
                        }
                    }
                }
                item {
                    Surface(
                        onClick = { showRouteAlternativesDialog = true },
                        shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                        color = LuxuryColors.CardSurface,
                        border = BorderStroke(1.dp, LuxuryColors.CardBorder)
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("🔀 ROUTE & TRAFFIC", style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.PlatinumWhite, fontWeight = FontWeight.Bold, fontSize = 10.sp))
                        }
                    }
                }
                item {
                    Surface(
                        onClick = { showGpsRecoveryDialog = true },
                        shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                        color = LuxuryColors.CardSurface,
                        border = BorderStroke(1.dp, LuxuryColors.CardBorder)
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("🛰 GPS PRECISION", style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.PlatinumWhite, fontWeight = FontWeight.Bold, fontSize = 10.sp))
                        }
                    }
                }
                item {
                    Surface(
                        onClick = { showMapAttributionDialog = true },
                        shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                        color = LuxuryColors.CardSurface,
                        border = BorderStroke(1.dp, LuxuryColors.CardBorder)
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("🗺 MAP & OFFLINE", style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.PlatinumWhite, fontWeight = FontWeight.Bold, fontSize = 10.sp))
                        }
                    }
                }
                item {
                    Surface(
                        onClick = { helpContextTag = null; showHelpCenterDialog = true },
                        shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                        color = LuxuryColors.CardSurface,
                        border = BorderStroke(1.dp, LuxuryColors.ChampagneGold)
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("🎧 VIP CONCIERGE & HELP", style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.ChampagneGold, fontWeight = FontWeight.Bold, fontSize = 10.sp))
                        }
                    }
                }
                item {
                    Surface(
                        onClick = { showSupportTicketsDialog = true },
                        shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                        color = LuxuryColors.CardSurface,
                        border = BorderStroke(1.dp, LuxuryColors.ChampagneGold.copy(alpha = 0.6f))
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("🎫 SUPPORT TICKETS", style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.ChampagneGold, fontWeight = FontWeight.Bold, fontSize = 10.sp))
                        }
                    }
                }
                item {
                    Surface(
                        onClick = { showSelfServiceDialog = true },
                        shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                        color = LuxuryColors.CardSurface,
                        border = BorderStroke(1.dp, LuxuryColors.CardBorder)
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("⚡ SELF-SERVICE DESK", style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.PlatinumWhite, fontWeight = FontWeight.Bold, fontSize = 10.sp))
                        }
                    }
                }
                item {
                    Surface(
                        onClick = { showLoyaltyDialog = true },
                        shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                        color = LuxuryColors.CardSurface,
                        border = BorderStroke(1.dp, LuxuryColors.ChampagneGold)
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("👑 ROYAL LOYALTY", style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.ChampagneGold, fontWeight = FontWeight.Bold, fontSize = 10.sp))
                        }
                    }
                }
                item {
                    Surface(
                        onClick = { showReferralsDialog = true },
                        shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                        color = LuxuryColors.CardSurface,
                        border = BorderStroke(1.dp, LuxuryColors.ChampagneGold.copy(alpha = 0.6f))
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("🤝 REFERRALS", style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.ChampagneGold, fontWeight = FontWeight.Bold, fontSize = 10.sp))
                        }
                    }
                }
                item {
                    Surface(
                        onClick = { showOffersDialog = true },
                        shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                        color = LuxuryColors.CardSurface,
                        border = BorderStroke(1.dp, LuxuryColors.CardBorder)
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("🎁 EXCLUSIVE OFFERS", style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.PlatinumWhite, fontWeight = FontWeight.Bold, fontSize = 10.sp))
                        }
                    }
                }
                item {
                    Surface(
                        onClick = { showJournalsDialog = true },
                        shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                        color = LuxuryColors.CardSurface,
                        border = BorderStroke(1.dp, LuxuryColors.CardBorder)
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("📜 RIDE JOURNALS", style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.PlatinumWhite, fontWeight = FontWeight.Bold, fontSize = 10.sp))
                        }
                    }
                }
                item {
                    Surface(
                        onClick = { showTemplatesDialog = true },
                        shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                        color = LuxuryColors.CardSurface,
                        border = BorderStroke(1.dp, LuxuryColors.CardBorder)
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("★ TEMPLATES", style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.PlatinumWhite, fontWeight = FontWeight.Bold, fontSize = 10.sp))
                        }
                    }
                }
                item {
                    Surface(
                        onClick = { showSharedLinkDialog = true },
                        shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                        color = LuxuryColors.CardSurface,
                        border = BorderStroke(1.dp, LuxuryColors.CardBorder)
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("⚑ SHARED LINK", style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.PlatinumWhite, fontWeight = FontWeight.Bold, fontSize = 10.sp))
                        }
                    }
                }
                item {
                    Surface(
                        onClick = { showTrafficRecDialog = true },
                        shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                        color = LuxuryColors.CardSurface,
                        border = BorderStroke(1.dp, LuxuryColors.CardBorder)
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("⏱ TRAFFIC ADVISOR", style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.PlatinumWhite, fontWeight = FontWeight.Bold, fontSize = 10.sp))
                        }
                    }
                }
                item {
                    Surface(
                        onClick = { showRecurringDialog = true },
                        shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                        color = LuxuryColors.CardSurface,
                        border = BorderStroke(1.dp, LuxuryColors.CardBorder)
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("📅 RECURRING COMMUTE", style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.PlatinumWhite, fontWeight = FontWeight.Bold, fontSize = 10.sp))
                        }
                    }
                }
            }

            // Feature 193: Active Incident Alert Banner
            if (activeIncidents.isNotEmpty()) {
                ActiveIncidentBannerComposable(
                    incidents = activeIncidents,
                    onViewStatus = {
                        helpContextTag = null
                        showHelpCenterDialog = true
                    }
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Recent Destinations Shortcut Chips (Feature 47)
            if (recentDestinations.isNotEmpty()) {
                Text("RECENT DESTINATIONS", style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = LuxuryColors.SubtleGray))
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                ) {
                    items(recentDestinations) { item ->
                        val safeAddr = item.address ?: "Curated Landmark"
                        Surface(
                            onClick = {
                                selectedDestination = LuxuryPlace(safeAddr, safeAddr, item.latitude, item.longitude)
                                destinationSearchQuery = safeAddr
                            },
                            shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                            color = LuxuryColors.ObsidianBlack,
                            border = BorderStroke(1.dp, LuxuryColors.CardBorder)
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "↻ ${safeAddr.take(24)} (${item.visitCount}x)",
                                    style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp, color = LuxuryColors.PlatinumWhite),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // Real Google Maps Surface with Pin Drag/Adjust & Map-Based Destination Select (Features 32 & 48)
            Surface(
                shape = RoundedCornerShape(LuxuryDimensions.RadiusMedium),
                color = LuxuryColors.DeepGraphite,
                border = BorderStroke(1.dp, LuxuryColors.CardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
            ) {
                Box {
                    val destPlace = selectedDestination ?: defaultCuratedDestinations.first()
                    LuxuryMapView(
                        modifier = Modifier.fillMaxSize(),
                        pickupLatLng = pickupLatLng,
                        pickupTitle = "Pickup: $pickupAddress",
                        destLatLng = LatLng(destPlace.lat, destPlace.lng),
                        destTitle = destPlace.name
                    )

                    // Top GPS status & Map pin actions
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        GoldBadge(if (isGpsLive) "GPS LIVE" else "GPS READY")

                        Surface(
                            shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                            color = LuxuryColors.ObsidianBlack.copy(alpha = 0.88f),
                            border = BorderStroke(1.dp, LuxuryColors.ChampagneGold),
                            modifier = Modifier.clickable { showPickupPinDialog = true }
                        ) {
                            Text(
                                text = "📍 ADJUST PIN",
                                style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.ChampagneGold, fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                            color = LuxuryColors.ObsidianBlack.copy(alpha = 0.88f),
                            border = BorderStroke(1.dp, LuxuryColors.CardBorder),
                            modifier = Modifier.clickable { showDestPinDialog = true }
                        ) {
                            Text(
                                text = "🎯 PICK DEST",
                                style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.PlatinumWhite, fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Live Address Preview Bar
                    Surface(
                        shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
                        color = LuxuryColors.ObsidianBlack.copy(alpha = 0.90f),
                        modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                    ) {
                        Text(
                            text = "📍 $pickupAddress",
                            style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp),
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Destination Search Input
            OutlinedTextField(
                value = destinationSearchQuery,
                onValueChange = {
                    destinationSearchQuery = it
                    if (selectedDestination != null && it != selectedDestination?.name) {
                        selectedDestination = null
                    }
                },
                placeholder = {
                    Text(
                        if (isSearchingDestinations) "Searching live landmarks..." else "Where may we escort you?",
                        color = LuxuryColors.SubtleGray
                    )
                },
                textStyle = LuxuryTypography.BodyRegular,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LuxuryColors.ChampagneGold,
                    unfocusedBorderColor = LuxuryColors.CardBorder,
                    focusedTextColor = LuxuryColors.PlatinumWhite,
                    unfocusedTextColor = LuxuryColors.PlatinumWhite
                ),
                shape = RoundedCornerShape(LuxuryDimensions.RadiusMedium),
                trailingIcon = {
                    if (isSearchingDestinations) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = LuxuryColors.ChampagneGold, strokeWidth = 2.dp)
                    } else if (destinationSearchQuery.isNotEmpty()) {
                        Text("✕", color = LuxuryColors.SubtleGray, modifier = Modifier.clickable { destinationSearchQuery = ""; selectedDestination = null }.padding(8.dp))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (selectedDestination == null) {
                Text(
                    text = if (dynamicDestinations.isNotEmpty()) "LIVE MATCHES (SERVER)" else "DISTINGUISHED DESTINATIONS",
                    style = LuxuryTypography.TitleGold.copy(fontSize = 13.sp)
                )
                Spacer(modifier = Modifier.height(6.dp))

                val displayPlaces = if (dynamicDestinations.isNotEmpty()) {
                    dynamicDestinations
                } else if (destinationSearchQuery.isNotEmpty()) {
                    defaultCuratedDestinations.filter {
                        it.name.contains(destinationSearchQuery, ignoreCase = true) || it.address.contains(destinationSearchQuery, ignoreCase = true)
                    }
                } else {
                    defaultCuratedDestinations
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    displayPlaces.forEach { place ->
                        LuxuryCard(
                            onClick = {
                                selectedDestination = place
                                destinationSearchQuery = place.name
                            }
                        ) {
                            Text(place.name, style = LuxuryTypography.BodyRegular.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp))
                            Text(place.address, style = LuxuryTypography.BodySubtle.copy(fontSize = 12.sp))
                        }
                    }
                }
            } else {
                // PASSENGER SELECTOR (Features 26, 27, 45)
                LuxuryCard {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("PASSENGER DESIGNATION", style = LuxuryTypography.TitleGold.copy(fontSize = 12.sp))
                            if (!bookingForSelf) {
                                TextButton(
                                    onClick = { showDependentsDialog = true },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("★ Dependents Registry", color = LuxuryColors.ChampagneGold, fontSize = 11.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                onClick = {
                                    bookingForSelf = true
                                    isBookingForDependent = false
                                    selectedDependentId = null
                                },
                                shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                                color = if (bookingForSelf) LuxuryColors.ChampagneGold else LuxuryColors.ObsidianBlack,
                                border = BorderStroke(1.dp, if (bookingForSelf) LuxuryColors.ChampagneGold else LuxuryColors.CardBorder),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(modifier = Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                                    Text("FOR MYSELF", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (bookingForSelf) LuxuryColors.ObsidianBlack else LuxuryColors.PlatinumWhite)
                                }
                            }

                            Surface(
                                onClick = {
                                    bookingForSelf = false
                                },
                                shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                                color = if (!bookingForSelf) LuxuryColors.ChampagneGold else LuxuryColors.ObsidianBlack,
                                border = BorderStroke(1.dp, if (!bookingForSelf) LuxuryColors.ChampagneGold else LuxuryColors.CardBorder),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(modifier = Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                                    Text("ANOTHER GUEST", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (!bookingForSelf) LuxuryColors.ObsidianBlack else LuxuryColors.PlatinumWhite)
                                }
                            }
                        }

                        if (!bookingForSelf) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(
                                    value = passengerName,
                                    onValueChange = { passengerName = it },
                                    label = { Text("Passenger Name", fontSize = 11.sp) },
                                    textStyle = LuxuryTypography.BodyRegular.copy(fontSize = 13.sp),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = passengerPhone,
                                    onValueChange = { passengerPhone = it },
                                    label = { Text("Contact Phone", fontSize = 11.sp) },
                                    textStyle = LuxuryTypography.BodyRegular.copy(fontSize = 13.sp),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // BOOKING PROFILE MODE: STANDARD / HOTEL / CORPORATE (Features 43, 44)
                LuxuryCard {
                    Column {
                        Text("BOOKING PROFILE & PURPOSE", style = LuxuryTypography.TitleGold.copy(fontSize = 12.sp))
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("STANDARD" to "Standard", "HOTEL_CONCIERGE" to "Hotel Concierge", "BUSINESS" to "Corporate Trip").forEach { (mode, label) ->
                                val isSelected = bookingMode == mode
                                Surface(
                                    onClick = { bookingMode = mode },
                                    shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                                    color = if (isSelected) LuxuryColors.ChampagneGold else LuxuryColors.ObsidianBlack,
                                    border = BorderStroke(1.dp, if (isSelected) LuxuryColors.ChampagneGold else LuxuryColors.CardBorder),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(modifier = Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                                        Text(
                                            label,
                                            fontSize = 10.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) LuxuryColors.ObsidianBlack else LuxuryColors.PlatinumWhite
                                        )
                                    }
                                }
                            }
                        }

                        if (bookingMode == "HOTEL_CONCIERGE") {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(
                                    value = hotelRoomNumber,
                                    onValueChange = { hotelRoomNumber = it },
                                    label = { Text("Room / Suite #", fontSize = 11.sp) },
                                    placeholder = { Text("Suite 702", color = LuxuryColors.SubtleGray) },
                                    textStyle = LuxuryTypography.BodyRegular.copy(fontSize = 13.sp),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = hotelGuestName,
                                    onValueChange = { hotelGuestName = it },
                                    label = { Text("VIP Guest Name", fontSize = 11.sp) },
                                    placeholder = { Text("Lord Mountbatten", color = LuxuryColors.SubtleGray) },
                                    textStyle = LuxuryTypography.BodyRegular.copy(fontSize = 13.sp),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        } else if (bookingMode == "BUSINESS") {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(
                                    value = businessCostCenter,
                                    onValueChange = { businessCostCenter = it },
                                    label = { Text("Cost Center Code", fontSize = 11.sp) },
                                    placeholder = { Text("CC-EXEC-2026", color = LuxuryColors.SubtleGray) },
                                    textStyle = LuxuryTypography.BodyRegular.copy(fontSize = 13.sp),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = businessProjectCode,
                                    onValueChange = { businessProjectCode = it },
                                    label = { Text("Project / Client Code", fontSize = 11.sp) },
                                    placeholder = { Text("PROJ-ACQ-MUM", color = LuxuryColors.SubtleGray) },
                                    textStyle = LuxuryTypography.BodyRegular.copy(fontSize = 13.sp),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = businessTaxId,
                                onValueChange = { businessTaxId = it },
                                label = { Text("Corporate GSTIN / Tax ID for Invoicing", fontSize = 11.sp) },
                                placeholder = { Text("27AABCT3518Q1Z1", color = LuxuryColors.SubtleGray) },
                                textStyle = LuxuryTypography.BodyRegular.copy(fontSize = 13.sp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // INTERMEDIATE STOPS & MULTI-STOP ROUTE (Features 36, 37, 38)
                LuxuryCard {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("INTERMEDIATE WAYPOINTS (${intermediateStops.size})", style = LuxuryTypography.TitleGold.copy(fontSize = 12.sp))
                            Button(
                                onClick = { showAddStopModal = true },
                                colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.CardBorder),
                                modifier = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text("+ Add Stop", fontSize = 10.sp, color = LuxuryColors.ChampagneGold, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (intermediateStops.isEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Direct non-stop escort. Add waypoints with designated dwell times.", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))
                        } else {
                            Spacer(modifier = Modifier.height(6.dp))
                            intermediateStops.forEachIndexed { idx, stp ->
                                Surface(
                                    shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                                    color = LuxuryColors.ObsidianBlack,
                                    border = BorderStroke(1.dp, LuxuryColors.CardBorder),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                GoldBadge("Stop ${idx + 1}")
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("${stp.dwellTimeMinutes}m dwell", style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.ChampagneGold, fontSize = 11.sp))
                                            }
                                            Text(stp.address, style = LuxuryTypography.BodyRegular.copy(fontSize = 12.sp), maxLines = 1)
                                            if (!stp.passengerInstructions.isNullOrBlank()) {
                                                Text(stp.passengerInstructions, style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp))
                                            }
                                        }
                                        IconButton(
                                            onClick = {
                                                intermediateStops = intermediateStops.filterIndexed { i, _ -> i != idx }
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Text("✕", color = LuxuryColors.CrimsonAlert, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ROUND-TRIP & RETURN-TRIP OPTIONS (Features 28, 39)
                LuxuryCard {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Round-Trip with Intermediate Stops", style = LuxuryTypography.BodyRegular.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp))
                                Text("Chauffeur remains committed for entire circuit return", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))
                            }
                            Switch(
                                checked = isRoundTrip,
                                onCheckedChange = { isRoundTrip = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = LuxuryColors.ChampagneGold)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Divider(color = LuxuryColors.CardBorder)
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Schedule Paired Return Journey", style = LuxuryTypography.BodyRegular.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp))
                                Text("Automatically reserve return chauffeur escort", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))
                            }
                            Switch(
                                checked = isReturnTripScheduled,
                                onCheckedChange = { isReturnTripScheduled = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = LuxuryColors.ChampagneGold)
                            )
                        }

                        if (isReturnTripScheduled) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Return Pickup Offset After Arrival:", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(2, 4, 8, 24).forEach { hours ->
                                    val isSel = returnOffsetHours == hours
                                    Button(
                                        onClick = { returnOffsetHours = hours },
                                        colors = ButtonDefaults.buttonColors(containerColor = if (isSel) LuxuryColors.ChampagneGold else LuxuryColors.CardBorder),
                                        modifier = Modifier.weight(1f).height(28.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("+${hours}h", fontSize = 11.sp, color = if (isSel) LuxuryColors.ObsidianBlack else LuxuryColors.PlatinumWhite)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // AIRPORT CONCIERGE & FLIGHT TELEMETRY (Features 40, 41, 42)
                LuxuryCard {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Airport Flight Meet & Greet", style = LuxuryTypography.BodyRegular.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp))
                                Text("Delay-aware automatic pickup alignment & terminal paging", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))
                            }
                            Switch(
                                checked = isAirportBooking,
                                onCheckedChange = { isAirportBooking = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = LuxuryColors.ChampagneGold)
                            )
                        }

                        if (isAirportBooking) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(
                                    value = flightNumberInput,
                                    onValueChange = { flightNumberInput = it },
                                    label = { Text("Flight Number", fontSize = 11.sp) },
                                    placeholder = { Text("EK504", color = LuxuryColors.SubtleGray) },
                                    textStyle = LuxuryTypography.BodyRegular.copy(fontSize = 13.sp),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = airportTerminal,
                                    onValueChange = { airportTerminal = it },
                                    label = { Text("Terminal", fontSize = 11.sp) },
                                    textStyle = LuxuryTypography.BodyRegular.copy(fontSize = 13.sp),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = {
                                    if (flightNumberInput.isBlank() || isCheckingFlight) return@Button
                                    isCheckingFlight = true
                                    scope.launch {
                                        try {
                                            val res = ApiClient.getService().getFlightStatus(flightNumberInput.trim())
                                            if (res.isSuccessful && res.body() != null) {
                                                flightTelemetry = res.body()!!
                                            }
                                        } catch (_: Exception) {}
                                        isCheckingFlight = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.CardBorder),
                                modifier = Modifier.fillMaxWidth().height(32.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    if (isCheckingFlight) "FETCHING TELEMETRY..." else "VERIFY LIVE FLIGHT TELEMETRY",
                                    fontSize = 11.sp,
                                    color = LuxuryColors.ChampagneGold,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (flightTelemetry != null) {
                                val tel = flightTelemetry!!
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                                    color = LuxuryColors.ObsidianBlack,
                                    border = BorderStroke(1.dp, LuxuryColors.ChampagneGold),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("${tel.airlineName} • ${tel.flightNumber}", style = LuxuryTypography.BodyRegular.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp))
                                            GoldBadge(tel.status)
                                        }
                                        Text("Arrival: ${tel.scheduledArrival} (Est: ${tel.estimatedArrival})", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))
                                        Text("Delay: +${tel.delayMinutes} mins • Baggage: ${tel.baggageBelt}", style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.ChampagneGold, fontSize = 11.sp))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // CHAUFFEUR GUIDANCE & ACCESS INSTRUCTIONS (Features 33, 34, 35)
                LuxuryCard {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("CHAUFFEUR GUIDANCE & ACCESS", style = LuxuryTypography.TitleGold.copy(fontSize = 12.sp))
                            TextButton(
                                onClick = { showGuidanceSection = !showGuidanceSection },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(if (showGuidanceSection) "Hide ▲" else "Expand ▼", color = LuxuryColors.ChampagneGold, fontSize = 11.sp)
                            }
                        }

                        if (showGuidanceSection) {
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = pickupNotes,
                                onValueChange = { pickupNotes = it },
                                label = { Text("Pickup Notes for Chauffeur", fontSize = 11.sp) },
                                placeholder = { Text("e.g. Wait at east VIP driveway with umbrella", color = LuxuryColors.SubtleGray) },
                                textStyle = LuxuryTypography.BodyRegular.copy(fontSize = 13.sp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = destinationNotes,
                                onValueChange = { destinationNotes = it },
                                label = { Text("Destination Notes for Chauffeur", fontSize = 11.sp) },
                                placeholder = { Text("e.g. Escort to 4th floor penthouse lobby", color = LuxuryColors.SubtleGray) },
                                textStyle = LuxuryTypography.BodyRegular.copy(fontSize = 13.sp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(
                                    value = entranceGuidance,
                                    onValueChange = { entranceGuidance = it },
                                    label = { Text("Entrance Guidance", fontSize = 11.sp) },
                                    placeholder = { Text("Gate 3 VIP portico", color = LuxuryColors.SubtleGray) },
                                    textStyle = LuxuryTypography.BodyRegular.copy(fontSize = 13.sp),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = landmarkGuidance,
                                    onValueChange = { landmarkGuidance = it },
                                    label = { Text("Landmark Guidance", fontSize = 11.sp) },
                                    placeholder = { Text("Opposite royal obelisk", color = LuxuryColors.SubtleGray) },
                                    textStyle = LuxuryTypography.BodyRegular.copy(fontSize = 13.sp),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Feature 99: Expiring Promo Banner
                if (expiringPromos.isNotEmpty()) {
                    val firstPromo = expiringPromos.first()
                    Surface(
                        shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                        color = LuxuryColors.ChampagneGold.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, LuxuryColors.ChampagneGold.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⏳", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${firstPromo.title ?: firstPromo.code}: ${firstPromo.reminderText}",
                                style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.PlatinumWhite, fontSize = 10.sp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Vehicle Category Selection & Filters (Features 51, 52, 55, 67, 68, 72, 74)
                Surface(
                    color = LuxuryColors.CharcoalSurface,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, LuxuryColors.ChampagneGold.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text("SHARED JOURNEY", style = LuxuryTypography.TitleGold.copy(fontSize = 12.sp))
                            Text("Compare live pool fit, detour, seats and savings before confirming.", style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp, color = LuxuryColors.MutedSilver))
                        }
                        Button(
                            onClick = {
                                if (selectedDestination == null) Toast.makeText(context, "Choose a destination before checking shared rides.", Toast.LENGTH_SHORT).show()
                                else showSharedRideFlow = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = LuxuryColors.ChampagneGold, contentColor = LuxuryColors.ObsidianBlack),
                            shape = RoundedCornerShape(10.dp),
                        ) { Text("EXPLORE", fontWeight = FontWeight.Bold, fontSize = 10.sp) }
                    }
                }

                // Vehicle Category Selection & Filters (Features 51, 52, 55, 67, 68, 72, 74)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("SELECT LUXURY ESCORT", style = LuxuryTypography.TitleGold.copy(fontSize = 12.sp))
                    Row {
                        TextButton(
                            onClick = { showFeatureComparisonSheet = true },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Text("COMPARE MATRIX ›", style = LuxuryTypography.TitleGold.copy(fontSize = 11.sp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))

                // Feature 72, 55, 67, 68, 74: Category Filter Chips Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    listOf(
                        "ALL" to "All Tiers",
                        "CAPACITY_4" to "4+ Guests",
                        "LUGGAGE_3" to "Luggage 3+",
                        "WAV" to "WAV Accessible",
                        "EV" to "100% Electric",
                        "PET" to "Pet-Friendly"
                    ).forEach { (filterKey, label) ->
                        val isSel = activeCategoryFilter == filterKey
                        Surface(
                            onClick = { activeCategoryFilter = filterKey },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSel) LuxuryColors.ChampagneGold else LuxuryColors.DeepCharcoal,
                            border = BorderStroke(1.dp, if (isSel) LuxuryColors.ChampagneGold else LuxuryColors.CardBorder)
                        ) {
                            Text(
                                text = label,
                                style = LuxuryTypography.BodySubtle.copy(
                                    fontSize = 10.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSel) LuxuryColors.ObsidianBlack else LuxuryColors.PlatinumWhite
                                ),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                val filteredCategories = dynamicCategories.filter { cat ->
                    when (activeCategoryFilter) {
                        "CAPACITY_4" -> cat.capacity >= 4
                        "WAV" -> cat.wheelchairAccessible
                        "EV" -> cat.isElectric
                        "PET" -> cat.petFriendly
                        "LUGGAGE_3" -> cat.luggageCapacity.contains("3") || cat.luggageCapacity.contains("4")
                        else -> true
                    }
                }

                (if (filteredCategories.isNotEmpty()) filteredCategories else dynamicCategories).forEachIndexed { index, cat ->
                    val isSelected = selectedCategoryIndex == index
                    LuxuryCard(
                        border = if (isSelected) BorderStroke(1.5.dp, LuxuryColors.ChampagneGold) else BorderStroke(1.dp, LuxuryColors.CardBorder),
                        onClick = { selectedCategoryIndex = index }
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(cat.name, style = LuxuryTypography.HeadlineMedium.copy(fontSize = 15.sp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        GoldBadge("${cat.capacity} Guests")
                                        if (cat.isElectric) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = LuxuryColors.EmeraldGreen.copy(alpha = 0.2f),
                                                border = BorderStroke(0.5.dp, LuxuryColors.EmeraldGreen)
                                            ) {
                                                Text("EV", fontSize = 8.sp, color = LuxuryColors.EmeraldGreen, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                            }
                                        }
                                        if (cat.wheelchairAccessible) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = LuxuryColors.ChampagneGold.copy(alpha = 0.2f),
                                                border = BorderStroke(0.5.dp, LuxuryColors.ChampagneGold)
                                            ) {
                                                Text("WAV", fontSize = 8.sp, color = LuxuryColors.ChampagneGold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                            }
                                        }
                                    }
                                    Text(cat.desc, style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp), maxLines = 1)
                                }
                                Text(
                                    text = "₹${cat.fareRupees}",
                                    style = LuxuryTypography.HeadlineMedium.copy(color = LuxuryColors.ChampagneGold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            // Specs & Gallery Trigger (Features 51, 58, 59)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("${cat.legroomInches}\" Legroom", style = LuxuryTypography.BodySubtle.copy(fontSize = 9.sp, color = LuxuryColors.MutedSilver))
                                    Text("•", style = LuxuryTypography.BodySubtle.copy(fontSize = 9.sp, color = LuxuryColors.MutedSilver))
                                    Text("≤ ${cat.quietnessDb} dB Sound", style = LuxuryTypography.BodySubtle.copy(fontSize = 9.sp, color = LuxuryColors.MutedSilver))
                                    Text("•", style = LuxuryTypography.BodySubtle.copy(fontSize = 9.sp, color = LuxuryColors.MutedSilver))
                                    Text(cat.luggageCapacity, style = LuxuryTypography.BodySubtle.copy(fontSize = 9.sp, color = LuxuryColors.MutedSilver))
                                }

                                TextButton(
                                    onClick = {
                                        galleryCategoryCode = cat.code
                                        galleryCategoryName = cat.name
                                        showVehicleGalleryModal = true
                                    },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("GALLERY & INTERIOR ›", color = LuxuryColors.ChampagneGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                Spacer(modifier = Modifier.height(6.dp))

                // BESPOKE SANCTUARY & ACCESSIBILITY CUSTOMIZATION CARD (Features 53-68, 71, 73, 74)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = LuxuryColors.DeepCharcoal,
                    border = BorderStroke(1.dp, LuxuryColors.ChampagneGold.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("✦", color = LuxuryColors.ChampagneGold, fontSize = 12.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "BESPOKE SANCTUARY & ACCESSIBILITY",
                                        style = LuxuryTypography.TitleGold.copy(fontSize = 12.sp)
                                    )
                                }
                                Text(
                                    text = "Luggage: ${luggageLargeCount}L/${luggageSmallCount}S • ${if (quietRideRequested) "Silent Discretion" else musicRequested} • ${String.format("%.1f", cabinTempRequested)}°C • $driverLanguageRequested • ${if (wheelchairRequested) "WAV Accessible" else if (stepFreePickup) "Step-Free Boarding" else "Standard Boarding"}",
                                    style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp, color = LuxuryColors.PlatinumWhite),
                                    maxLines = 2
                                )
                            }
                            Button(
                                onClick = { showBespokeCustomizationSheet = true },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = LuxuryColors.ChampagneGold,
                                    contentColor = LuxuryColors.ObsidianBlack
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("CUSTOMIZE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Features 76, 77, 79, 81: Fare Estimate Card with Breakdown & Confidence Range
                val activeCat = dynamicCategories.getOrNull(selectedCategoryIndex) ?: dynamicCategories.first()
                val displayFare = serverFareRupees ?: activeCat.fareRupees

                Surface(
                    color = LuxuryColors.CardSurface,
                    shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                    border = BorderStroke(1.dp, LuxuryColors.CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("Guaranteed Luxury Fare", style = LuxuryTypography.BodySubtle.copy(fontSize = 11.sp))
                                if (fullEstimateResponse?.confidenceLevel != null) {
                                    Text("Confidence: ${fullEstimateResponse?.confidenceLevel} (${fullEstimateResponse?.confidencePercentage}%)", style = LuxuryTypography.BodySubtle.copy(fontSize = 9.sp, color = LuxuryColors.EmeraldGreen))
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("₹$displayFare.00", style = LuxuryTypography.TitleGold.copy(fontSize = 16.sp))
                                if (fullEstimateResponse?.fareMinRupees != null && fullEstimateResponse?.fareMaxRupees != null) {
                                    Text("Range: ₹${fullEstimateResponse!!.fareMinRupees!!.toInt()} - ₹${fullEstimateResponse!!.fareMaxRupees!!.toInt()}", style = LuxuryTypography.BodySubtle.copy(fontSize = 9.sp))
                                }
                            }
                        }

                        if (fullEstimateResponse != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showFareBreakdownDialog = true },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Itemized Distance, Time & Tax Breakdown ›", style = LuxuryTypography.BodySubtle.copy(color = LuxuryColors.ChampagneGold, fontSize = 10.sp))
                                if (fullEstimateResponse?.isCapped == true) {
                                    GoldBadge("FARE CAPPED")
                                }
                            }
                        }

                        // Feature 82: Price Lock Guarantee Toggle
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🔒 24-Hour Price Lock Guarantee", style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp, color = LuxuryColors.PlatinumWhite))
                            Switch(
                                checked = isPriceLocked,
                                onCheckedChange = { isPriceLocked = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = LuxuryColors.ObsidianBlack,
                                    checkedTrackColor = LuxuryColors.ChampagneGold
                                ),
                                modifier = Modifier.height(24.dp)
                            )
                        }

                        if (isRoundTrip) {
                            Text("• Includes round-trip return circuit", style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp, color = LuxuryColors.ChampagneGold))
                        }
                        if (intermediateStops.isNotEmpty()) {
                            Text("• Includes ${intermediateStops.size} intermediate waypoints with designated dwell times", style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp))
                        }
                        if (isAirportBooking) {
                            Text("• Includes flight meet-and-greet delay protection", style = LuxuryTypography.BodySubtle.copy(fontSize = 10.sp, color = LuxuryColors.ChampagneGold))
                        }

                        // Feature 177: Contextual Help & Guidance from Booking Card
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ContextualHelpButton(
                                tag = "booking_screen",
                                label = "Luggage & Booking Guidelines",
                                onClick = { tag ->
                                    helpContextTag = tag
                                    showHelpCenterDialog = true
                                }
                            )
                            ContextualHelpButton(
                                tag = "cancellation",
                                label = "Cancellation Policy",
                                onClick = { tag ->
                                    helpContextTag = tag
                                    showHelpCenterDialog = true
                                }
                            )
                        }

                        // Feature 178: Guided Ride Cancellation Trigger
                        if (serverEstimateId != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = { showGuidedCancellationDialog = true },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text("Guided Cancellation Support ›", color = LuxuryColors.CrimsonAlert, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Feature 98: Promo Code Eligibility Explanation
                Surface(
                    shape = RoundedCornerShape(LuxuryDimensions.RadiusSmall),
                    color = LuxuryColors.CharcoalSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = promoCodeInput,
                                onValueChange = { promoCodeInput = it.uppercase() },
                                placeholder = { Text("PROMO CODE (e.g. ROYALE10)", fontSize = 10.sp, color = LuxuryColors.SubtleGray) },
                                modifier = Modifier.weight(1f),
                                textStyle = LuxuryTypography.BodyRegular.copy(fontSize = 11.sp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = LuxuryColors.ChampagneGold,
                                    focusedTextColor = LuxuryColors.PlatinumWhite,
                                    unfocusedTextColor = LuxuryColors.PlatinumWhite
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            LuxuryGoldButton(
                                text = if (isVerifyingPromo) "..." else "CHECK",
                                onClick = {
                                    if (promoCodeInput.isNotBlank()) {
                                        isVerifyingPromo = true
                                        val cat = dynamicCategories.getOrNull(selectedCategoryIndex)
                                        scope.launch {
                                            try {
                                                val res = ApiClient.getService().checkPromoEligibility(
                                                    PromoEligibilityRequest(
                                                        code = promoCodeInput,
                                                        fareCents = (serverFareRupees ?: 1600) * 100,
                                                        categoryId = cat?.id
                                                    )
                                                )
                                                if (res.isSuccessful) {
                                                    promoEligibilityResult = res.body()
                                                }
                                            } catch (_: Exception) {}
                                            isVerifyingPromo = false
                                        }
                                    }
                                }
                            )
                        }

                        if (promoEligibilityResult != null) {
                            val res = promoEligibilityResult!!
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (res.eligible) "✓ ${res.explanation ?: "Discount applied: ₹${res.discountRupees?.toInt()}"}" else "✕ ${res.reason ?: "Promo not applicable for this trip"}",
                                style = LuxuryTypography.BodySubtle.copy(
                                    color = if (res.eligible) LuxuryColors.EmeraldGreen else LuxuryColors.CrimsonAlert,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Reservation Execution Button with Pre-booking Preference Audit (Feature 75)

                LuxuryButton(
                    text = if (isBookingInProgress) "SECURING YOUR CHAUFFEUR..." else "CONFIRM RESERVATION",
                    onClick = {
                        if (isBookingInProgress) return@LuxuryButton
                        val dest = selectedDestination ?: defaultCuratedDestinations.first()
                        val activeCat = dynamicCategories.getOrNull(selectedCategoryIndex) ?: dynamicCategories.first()

                        val summary = PrebookingPreferenceSummaryDto(
                            passengerName = if (bookingForSelf) "Guest Principal" else (passengerName.ifBlank { "VIP Guest" }),
                            cabinAtmosphere = listOf(
                                "${String.format("%.1f", cabinTempRequested)}°C Dual-Zone Climate",
                                if (quietRideRequested) "Silent Discretion Mode (Chauffeur Silence Guaranteed)" else "Curated Soundscape: $musicRequested",
                                if (extraLegroomRequested) "Executive Lounge Recline (Front Seat Forwarded)" else "Standard Executive Seating"
                            ),
                            serviceEtiquette = listOf(
                                "Chauffeur Language: $driverLanguageRequested",
                                if (femaleDriverRequested) "Female Chauffeur Preferred (Where Legally Available)" else "Executive Protocol Chauffeur",
                                if (noPhoneCall) "Discreet Digital App Alerts Only (Zero Arrival Voice Calls)" else "Arrival Contact Calls Permitted"
                            ),
                            accessibilityCompliance = listOf(
                                if (wheelchairRequested) "Wheelchair-Accessible Vehicle (WAV) with Hoist Guaranteed" else "Low-Step Vehicle Access",
                                if (stepFreePickup) "Step-Free Kerbside Boarding (Suspension Lowered to ≤ 32cm)" else "Standard Boarding",
                                if (assistanceAnimal) "Certified Guide/Assistance Animal Accommodated" else "No Assistance Animal",
                                if (specialAssistanceNotes.isNotBlank()) "Chauffeur Brief: $specialAssistanceNotes" else "No Special Assistance Instructions"
                            ),
                            amenitiesAndFleet = listOf(
                                "Vehicle Tier: ${activeCat.name} (${activeCat.luggageCapacity})",
                                if (activeCat.isElectric) "Zero Exhaust Tailpipe Emissions (100% Electric EV)" else "Low-Emission Executive Chauffeur",
                                "Luggage Configuration: $luggageLargeCount Checked Large + $luggageSmallCount Cabin Bags",
                                if (childSeatRequested) "ISOFIX Child Restraint: $childSeatType" else "No Child Restraint Required",
                                if (petFriendlyRequested) "Pet-Friendly Cabin Liner & Safety Restraint" else "Standard Cabin",
                                "Exterior Hue: $vehicleColorRequested",
                                "Complimentary Suite: $amenitiesRequested"
                            ),
                            readyForDispatch = true
                        )
                        prebookingSummaryData = summary
                        showPrebookingSummaryDialog = true
                    },
                    modifier = Modifier.padding(bottom = 20.dp)
                )
            }
        }
    }

    // Part 3 Dialogs: Vehicle Gallery, Feature Comparison, Bespoke Customization, Prebooking Summary
    if (showVehicleGalleryModal) {
        VehicleGalleryModal(
            categoryCode = galleryCategoryCode,
            categoryName = galleryCategoryName,
            onDismiss = { showVehicleGalleryModal = false }
        )
    }

    if (showFeatureComparisonSheet) {
        VehicleFeatureComparisonSheet(
            onDismiss = { showFeatureComparisonSheet = false },
            onSelectCategory = { code ->
                val idx = dynamicCategories.indexOfFirst { it.code.equals(code, ignoreCase = true) }
                if (idx != -1) {
                    selectedCategoryIndex = idx
                }
            }
        )
    }

    if (showBespokeCustomizationSheet) {
        BespokeSanctuaryCustomizationSheet(
            initialLuggageLarge = luggageLargeCount,
            initialLuggageSmall = luggageSmallCount,
            initialChildSeat = childSeatRequested,
            initialChildSeatType = childSeatType,
            initialWheelchair = wheelchairRequested,
            initialStepFree = stepFreePickup,
            initialAssistanceAnimal = assistanceAnimal,
            initialExtraLegroom = extraLegroomRequested,
            initialQuietRide = quietRideRequested,
            initialCabinTemp = cabinTempRequested,
            initialMusic = musicRequested,
            initialNoPhoneCall = noPhoneCall,
            initialDriverLanguage = driverLanguageRequested,
            initialFemaleDriver = femaleDriverRequested,
            initialVehicleColor = vehicleColorRequested,
            initialAmenities = amenitiesRequested.split(",").map { it.trim() }.filter { it.isNotBlank() },
            initialEvRide = evRideRequested,
            initialPetFriendly = petFriendlyRequested,
            initialSpecialAssistanceNotes = specialAssistanceNotes,
            onSavePreferencesToProfile = { payload ->
                scope.launch {
                    try {
                        ApiClient.getService().updateCustomerPreferences(payload)
                    } catch (_: Exception) {}
                }
            },
            onApplyToBooking = { lLarge, lSmall, cSeat, cType, wChair, sFree, aAnimal, eLegroom, qRide, cTemp, music, noCall, lang, female, color, amens, ev, pet, notes ->
                luggageLargeCount = lLarge
                luggageSmallCount = lSmall
                childSeatRequested = cSeat
                childSeatType = cType
                wheelchairRequested = wChair
                stepFreePickup = sFree
                assistanceAnimal = aAnimal
                extraLegroomRequested = eLegroom
                quietRideRequested = qRide
                cabinTempRequested = cTemp
                musicRequested = music
                noPhoneCall = noCall
                driverLanguageRequested = lang
                femaleDriverRequested = female
                vehicleColorRequested = color
                amenitiesRequested = amens
                evRideRequested = ev
                petFriendlyRequested = pet
                specialAssistanceNotes = notes
            },
            onDismiss = { showBespokeCustomizationSheet = false }
        )
    }

    if (showPrebookingSummaryDialog && prebookingSummaryData != null) {
        PrebookingPreferenceSummaryDialog(
            summary = prebookingSummaryData!!,
            onConfirmDispatch = {
                showPrebookingSummaryDialog = false
                executeBespokeReservation()
            },
            onAdjustPreferences = {
                showPrebookingSummaryDialog = false
                showBespokeCustomizationSheet = true
            },
            onDismiss = { showPrebookingSummaryDialog = false }
        )
    }

    // Part 4 Dialogs: Fare Breakdown & Vehicle Comparison (Features 76-79, 81, 82)
    if (showFareBreakdownDialog && fullEstimateResponse != null) {
        FareEstimateBreakdownDialog(
            estimate = fullEstimateResponse!!,
            isPriceLocked = isPriceLocked,
            onPriceLockToggled = { isPriceLocked = it },
            onDismiss = { showFareBreakdownDialog = false }
        )
    }

    if (showVehicleComparisonDialog) {
        val cat = dynamicCategories.getOrNull(selectedCategoryIndex)
        VehicleClassComparisonDialog(
            comparisons = vehicleComparisons,
            selectedCategoryId = cat?.id ?: "",
            onCategorySelected = { catId ->
                val foundIdx = dynamicCategories.indexOfFirst { it.id == catId }
                if (foundIdx != -1) {
                    selectedCategoryIndex = foundIdx
                }
                showVehicleComparisonDialog = false
            },
            onDismiss = { showVehicleComparisonDialog = false }
        )
    }

    if (showLoyaltyDialog) {
        RoyalLoyaltyHubDialog(
            onDismiss = { showLoyaltyDialog = false }
        )
    }

    if (showReferralsDialog) {
        ReferralSanctuaryDialog(
            onDismiss = { showReferralsDialog = false }
        )
    }

    if (showOffersDialog) {
        ExclusiveOffersFeedDialog(
            onDismiss = { showOffersDialog = false }
        )
    }

    if (showJournalsDialog) {
        PastJournalsAndStatementsDialog(
            onDismiss = { showJournalsDialog = false },
            onTripRepeated = { repRideId ->
                showJournalsDialog = false
                onRideRequested(repRideId, "889900")
            }
        )
    }

    // Part 8 Dialogs: Support, Concierge & Help Center (Features 176 - 200)
    if (showHelpCenterDialog) {
        HelpCenterDialog(
            initialTag = helpContextTag,
            onDismiss = { showHelpCenterDialog = false; helpContextTag = null },
            onOpenTickets = { showHelpCenterDialog = false; showSupportTicketsDialog = true },
            onOpenSelfService = { showHelpCenterDialog = false; showSelfServiceDialog = true }
        )
    }

    if (showSupportTicketsDialog) {
        SupportTicketDialog(
            onDismiss = { showSupportTicketsDialog = false }
        )
    }

    if (showSelfServiceDialog) {
        SelfServiceResolverDialog(
            onDismiss = { showSelfServiceDialog = false }
        )
    }

    if (showGuidedCancellationDialog) {
        GuidedCancellationDialog(
            rideId = serverEstimateId ?: "ride_concierge_active",
            onDismiss = { showGuidedCancellationDialog = false },
            onCancellationCompleted = {
                Toast.makeText(context, "Reservation updated.", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showSharedRideFlow) {
        val category = dynamicCategories.getOrNull(selectedCategoryIndex) ?: dynamicCategories.first()
        val destination = selectedDestination ?: defaultCuratedDestinations.first()
        SharedRideBookingDialog(
            pickupAddress = pickupAddress,
            pickupLatitude = pickupLatLng.latitude,
            pickupLongitude = pickupLatLng.longitude,
            destinationAddress = destination.address,
            destinationLatitude = destination.lat,
            destinationLongitude = destination.lng,
            categoryId = category.id,
            categoryName = category.name,
            passengerCapacity = category.capacity,
            onDismiss = { showSharedRideFlow = false },
            onBooked = { rideId, otp, poolId, bookingId ->
                showSharedRideFlow = false
                onSharedRideBooked(rideId, otp, poolId, bookingId)
            }
        )
    }

    if (showAirportZoneDialog) {
        AirportPickupZoneDialog(
            onDismiss = { showAirportZoneDialog = false },
            onSelectPickup = { lat, lng, label ->
                pickupLatLng = LatLng(lat, lng)
                pickupAddress = label
                Toast.makeText(context, "Pickup pin set: $label", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showEntrancePinDialog) {
        BuildingEntrancePinDialog(
            currentLat = pickupLatLng.latitude,
            currentLng = pickupLatLng.longitude,
            onDismiss = { showEntrancePinDialog = false },
            onSelectEntrance = { lat, lng, label, notes, code ->
                pickupLatLng = LatLng(lat, lng)
                val extra = if (code.isNotBlank()) "Code: $code" else ""
                pickupNotes = "Entrance: $label. $notes $extra".trim()
                Toast.makeText(context, "Entrance applied: $label", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showRouteAlternativesDialog) {
        val dest = selectedDestination ?: defaultCuratedDestinations.first()
        RouteAlternativesDialog(
            originLat = pickupLatLng.latitude,
            originLng = pickupLatLng.longitude,
            destLat = dest.lat,
            destLng = dest.lng,
            onDismiss = { showRouteAlternativesDialog = false },
            onSelectRoute = { routeLabel, dur, dist ->
                Toast.makeText(context, "Selected: $routeLabel ($dur min, $dist km)", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showGpsRecoveryDialog) {
        LocationAccuracyAndGpsRecoveryDialog(
            accuracyMeters = (gpsAccuracyMeters ?: 12f).toDouble(),
            onDismiss = { showGpsRecoveryDialog = false }
        )
    }

    if (showMapAttributionDialog) {
        MapAttributionAndOfflineDialog(
            onDismiss = { showMapAttributionDialog = false }
        )
    }
}
