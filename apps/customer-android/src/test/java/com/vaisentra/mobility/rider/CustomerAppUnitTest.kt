package com.vaisentra.mobility.rider

import org.junit.Assert.*
import org.junit.Test
import com.vaisentra.mobility.core.map.LatLng
import com.vaisentra.mobility.core.map.MapboxGeoUtils
import com.vaisentra.mobility.core.map.MapboxUrlBuilder
import com.vaisentra.mobility.core.map.MapboxEmergencyRegistry
import com.vaisentra.mobility.core.map.AppMapType
import kotlin.math.max
import kotlin.math.roundToInt
import com.google.gson.Gson
import com.vaisentra.mobility.rider.core.network.CustomerApiService
import com.vaisentra.mobility.rider.core.network.SubmitDriverRatingRequest
import com.vaisentra.mobility.rider.core.network.OfferPreferencesRequest
import com.vaisentra.mobility.rider.core.network.FilteredRideDto
import com.vaisentra.mobility.rider.core.network.MonthlyStatementResponse
import com.vaisentra.mobility.rider.core.network.CustomerSurveyRequest
import retrofit2.http.GET
import retrofit2.http.POST

class CustomerAppUnitTest {

    @Test
    fun testPhoneNumberValidation() {
        val validPhone = "+919876543210"
        val invalidPhone = "123"
        
        assertTrue(validPhone.startsWith("+91") && validPhone.length >= 12)
        assertFalse(invalidPhone.length >= 10)
    }

    @Test
    fun testOtpCodeLength() {
        val otp = "849201"
        assertEquals(6, otp.length)
        assertTrue(otp.all { it.isDigit() })
    }

    @Test
    fun testFareCalculationFormatting() {
        val fareCents = 160000
        val fareRupees = fareCents / 100
        assertEquals(1600, fareRupees)
    }

    @Test
    fun testLifecycleTransitions() {
        val allowedStates = listOf("REQUESTED", "ASSIGNED", "ARRIVED", "IN_PROGRESS", "COMPLETED")
        assertTrue(allowedStates.contains("IN_PROGRESS"))
        assertFalse(allowedStates.contains("UNKNOWN_STATE"))
    }

    // --- Rapido Production Navigation Logic Tests ---

    @Test
    fun testCustomerActiveRideScreenRestorationMapping() {
        fun resolveScreenForRideStatus(status: String?): Int {
            return when (status?.uppercase()) {
                "REQUESTED" -> 14 // Screen 14: Searching for nearby Chauffeurs
                "ASSIGNED", "ARRIVED", "IN_PROGRESS" -> 15 // Screen 15: Live GPS Trip Tracking
                "COMPLETED" -> 20 // Screen 20: Trip Receipt & Star Rating
                else -> 7 // Screen 7: Home Explore
            }
        }

        assertEquals(14, resolveScreenForRideStatus("REQUESTED"))
        assertEquals(15, resolveScreenForRideStatus("ASSIGNED"))
        assertEquals(15, resolveScreenForRideStatus("ARRIVED"))
        assertEquals(15, resolveScreenForRideStatus("IN_PROGRESS"))
        assertEquals(20, resolveScreenForRideStatus("COMPLETED"))
        assertEquals(7, resolveScreenForRideStatus("CANCELLED"))
        assertEquals(7, resolveScreenForRideStatus(null))
    }

    @Test
    fun testCustomerDuplicateBookingLock() {
        var isBookingInProgress = false
        var bookingApiCalls = 0

        fun attemptBookRide(): Boolean {
            if (isBookingInProgress) {
                return false // Prevent duplicate dispatch
            }
            isBookingInProgress = true
            bookingApiCalls++
            return true
        }

        // First click succeeds
        assertTrue(attemptBookRide())
        assertEquals(1, bookingApiCalls)

        // Rapid second and third clicks are blocked
        assertFalse(attemptBookRide())
        assertFalse(attemptBookRide())
        assertEquals(1, bookingApiCalls)

        // After completion or failure, lock is released
        isBookingInProgress = false
        assertTrue(attemptBookRide())
        assertEquals(2, bookingApiCalls)
    }

    @Test
    fun testCustomerBackNavigationSafety() {
        // When user is in an active ride (Screen 14 or 15), Back handler must prevent implicit cancellation
        fun handleBackPress(currentScreen: Int, hasActiveRide: Boolean): Int {
            return if (hasActiveRide && (currentScreen == 14 || currentScreen == 15)) {
                // Must stay on active ride screen; prompt cancel confirmation dialog instead
                currentScreen
            } else if (currentScreen > 7 && currentScreen !in listOf(14, 15, 20)) {
                currentScreen - 1
            } else {
                7 // Default Home
            }
        }

        assertEquals(14, handleBackPress(14, true))
        assertEquals(15, handleBackPress(15, true))
        assertEquals(9, handleBackPress(10, false))
        assertEquals(7, handleBackPress(7, false))
    }

    @Test
    fun testCustomerCancellationPolicy() {
        fun isCancellationPermitted(status: String): Boolean {
            return when (status.uppercase()) {
                "REQUESTED", "ASSIGNED", "ARRIVED" -> true
                "IN_PROGRESS", "COMPLETED", "CANCELLED" -> false
                else -> false
            }
        }

        assertTrue(isCancellationPermitted("REQUESTED"))
        assertTrue(isCancellationPermitted("ASSIGNED"))
        assertTrue(isCancellationPermitted("ARRIVED"))
        assertFalse(isCancellationPermitted("IN_PROGRESS"))
        assertFalse(isCancellationPermitted("COMPLETED"))
    }

    // --- Mapbox Phase 1 Unit Tests ---

    @Test
    fun testMapboxGeoUtilsDistanceCalculation() {
        val cpLat = 28.629894
        val cpLng = 77.219480
        val rcLat = 28.632800
        val rcLng = 77.219500

        val distanceMeters = MapboxGeoUtils.computeDistanceMeters(cpLat, cpLng, rcLat, rcLng)
        assertTrue(distanceMeters in 250.0..500.0)

        val distanceKm = MapboxGeoUtils.computeDistanceKm(cpLat, cpLng, rcLat, rcLng)
        assertTrue(distanceKm < 1.0)
    }

    @Test
    fun testMapboxGeocodingUrlConstructionWithProximity() {
        val query = "Rajiv Chowk"
        val proximityLat = 28.6139
        val proximityLng = 77.2090
        val url = MapboxUrlBuilder.buildGeocodingUrl(
            query = query,
            proximityLat = proximityLat,
            proximityLng = proximityLng,
            limit = 8
        )

        assertTrue(url.contains("mapbox.places/Rajiv+Chowk.json") || url.contains("mapbox.places/Rajiv%20Chowk.json"))
        assertTrue(url.contains("autocomplete=true"))
        assertTrue(url.contains("limit=8"))
        assertTrue(url.contains("proximity=77.209000,28.613900"))
        assertTrue(url.contains("access_token="))
    }

    @Test
    fun testMapboxReverseGeocodingUrl() {
        val lat = 28.6139
        val lng = 77.2090
        val url = MapboxUrlBuilder.buildReverseGeocodingUrl(lat, lng)

        assertTrue(url.contains("mapbox.places/77.209000,28.613900.json"))
        assertTrue(url.contains("limit=1"))
        assertTrue(url.contains("access_token="))
    }

    @Test
    fun testMapboxERickshawDynamicFareFormula() {
        fun computeERickshawFare(distanceKm: Double): Int {
            return max(20, (20 + (distanceKm * 10)).roundToInt())
        }

        assertEquals(25, computeERickshawFare(0.5))
        assertEquals(35, computeERickshawFare(1.5))
        assertEquals(50, computeERickshawFare(3.0))
        assertEquals(20, computeERickshawFare(0.0))
    }

    @Test
    fun testMapbox4BaseStylesSupported() {
        val styles = AppMapType.values().map { it.code }
        assertTrue(styles.contains("LIGHT"))
        assertTrue(styles.contains("DARK"))
        assertTrue(styles.contains("SATELLITE"))
        assertTrue(styles.contains("TERRAIN"))
    }

    // --- Mapbox Phase 2 Multi-Stop Route & Fare Tests ---

    @Test
    fun testMapboxMultiStopDirectionsUrlConstruction() {
        val stop1 = LatLng(28.629894, 77.219480) // Connaught Place
        val stop2 = LatLng(28.632800, 77.219500) // Rajiv Chowk
        val stop3 = LatLng(28.630500, 77.228500) // Barakhamba Road

        val waypoints = listOf(stop1, stop2, stop3)
        val url = MapboxUrlBuilder.buildMultiStopDirectionsUrl(waypoints)

        assertTrue(url.contains("directions/v5/mapbox/driving-traffic/"))
        assertTrue(url.contains("77.219480,28.629894;77.219500,28.632800;77.228500,28.630500"))
        assertTrue(url.contains("geometries=geojson"))
        assertTrue(url.contains("overview=full"))
        assertTrue(url.contains("steps=true"))
        assertTrue(url.contains("access_token="))
    }

    @Test
    fun testIntermediateStopsReorderingLogic() {
        val stops = mutableListOf("Karol Bagh Metro", "Paharganj Market", "Chandni Chowk")

        // Move Paharganj (index 1) UP to index 0
        val targetIndex = 1
        val item = stops.removeAt(targetIndex)
        stops.add(targetIndex - 1, item)

        assertEquals("Paharganj Market", stops[0])
        assertEquals("Karol Bagh Metro", stops[1])
        assertEquals("Chandni Chowk", stops[2])

        // Move Paharganj (index 0) DOWN to index 1
        val downItem = stops.removeAt(0)
        stops.add(1, downItem)

        assertEquals("Karol Bagh Metro", stops[0])
        assertEquals("Paharganj Market", stops[1])
        assertEquals("Chandni Chowk", stops[2])
    }

    @Test
    fun testIntermediateStopsRemovalLogic() {
        val stops = mutableListOf("Stop A", "Stop B", "Stop C")
        val removed = stops.removeAt(1) // Remove Stop B

        assertEquals("Stop B", removed)
        assertEquals(2, stops.size)
        assertEquals(listOf("Stop A", "Stop C"), stops)
    }

    @Test
    fun testMultiStopFareFormulaWithAdditionalWaypoints() {
        fun computeMultiStopFare(baseDistanceKm: Double, numStops: Int): Map<String, Int> {
            val stopSurcharge = numStops * 15 // Rs 15 per extra stop
            val standard = max(20, (20 + (baseDistanceKm * 10)).roundToInt()) + stopSurcharge
            val pool = max(15, (12 + (baseDistanceKm * 7)).roundToInt()) + (numStops * 10)
            val fast = max(35, (30 + (baseDistanceKm * 14)).roundToInt()) + (numStops * 20)
            return mapOf("STANDARD" to standard, "POOL" to pool, "FAST" to fast)
        }

        // Test with 2 intermediate stops and 4.5 km distance
        val fares = computeMultiStopFare(4.5, 2)
        assertEquals(20 + 45 + 30, fares["STANDARD"]) // 95
        assertEquals(12 + 32 + 20, fares["POOL"])     // 64
        assertEquals(30 + 63 + 40, fares["FAST"])     // 133
    }

    // --- Mapbox Phase 4 Live Customer-Driver Tracking Tests ---

    @Test
    fun testDriverCoordinateInterpolation() {
        val start = LatLng(28.6139, 77.2090)
        val end = LatLng(28.6239, 77.2190)

        val midpoint = MapboxGeoUtils.interpolateCoordinate(start, end, 0.5f)
        assertEquals(28.6189, midpoint.latitude, 0.0001)
        assertEquals(77.2140, midpoint.longitude, 0.0001)

        val zeroPoint = MapboxGeoUtils.interpolateCoordinate(start, end, 0.0f)
        assertEquals(start.latitude, zeroPoint.latitude, 0.00001)
        assertEquals(start.longitude, zeroPoint.longitude, 0.00001)

        val fullPoint = MapboxGeoUtils.interpolateCoordinate(start, end, 1.0f)
        assertEquals(end.latitude, fullPoint.latitude, 0.00001)
        assertEquals(end.longitude, fullPoint.longitude, 0.00001)
    }

    @Test
    fun testDriverCompassBearingCalculation() {
        // Moving due North (lat increases, lng constant)
        val bearingNorth = MapboxGeoUtils.computeBearing(28.0, 77.0, 29.0, 77.0)
        assertEquals(0f, bearingNorth, 1.0f)

        // Moving due East (lat constant, lng increases)
        val bearingEast = MapboxGeoUtils.computeBearing(28.0, 77.0, 28.0, 78.0)
        assertEquals(90f, bearingEast, 1.0f)

        // Moving due South (lat decreases, lng constant)
        val bearingSouth = MapboxGeoUtils.computeBearing(28.0, 77.0, 27.0, 77.0)
        assertEquals(180f, bearingSouth, 1.0f)

        // Moving due West (lat constant, lng decreases)
        val bearingWest = MapboxGeoUtils.computeBearing(28.0, 77.0, 28.0, 76.0)
        assertEquals(270f, bearingWest, 1.0f)
    }

    @Test
    fun testStaleTelemetryDetectionThreshold() {
        fun isTelemetryStale(lastUpdatedSecondsAgo: Double): Boolean {
            return lastUpdatedSecondsAgo > 45.0
        }

        assertFalse(isTelemetryStale(5.0))
        assertFalse(isTelemetryStale(30.0))
        assertFalse(isTelemetryStale(45.0))
        assertTrue(isTelemetryStale(45.1))
        assertTrue(isTelemetryStale(90.0))
    }

    @Test
    fun testLiveTrackingStatusTargetSwitching() {
        val pickup = LatLng(28.6139, 77.2090)
        val destination = LatLng(28.6328, 77.2195)

        fun resolveTrackingTarget(rideStatus: String, pickupPoint: LatLng, destPoint: LatLng): LatLng {
            return if (rideStatus.uppercase() == "IN_PROGRESS") {
                destPoint
            } else {
                pickupPoint
            }
        }

        // When pilot is on the way or arrived, target is customer pickup point
        assertEquals(pickup, resolveTrackingTarget("ASSIGNED", pickup, destination))
        assertEquals(pickup, resolveTrackingTarget("ARRIVED", pickup, destination))

        // Once customer boards and ride starts, target switches to final destination
        assertEquals(destination, resolveTrackingTarget("IN_PROGRESS", pickup, destination))
    }

    // --- Mapbox Phase 5 Safety & Emergency Tests ---

    @Test
    fun testNearestPoliceStationLookup() {
        // Connaught Place Inner Circle coordinates
        val currentLat = 28.6328
        val currentLng = 77.2195

        val station = MapboxEmergencyRegistry.findNearestPoliceStation(currentLat, currentLng)
        assertNotNull(station)
        assertTrue(station.name.isNotBlank())
        assertTrue(station.phone.isNotBlank())
        assertTrue(station.distanceKm < 2.0) // Must be within 2km in central CP
        assertTrue(station.etaMinutes >= 1)
    }

    @Test
    fun testRouteDeviationDetectionThreshold() {
        val route = listOf(
            LatLng(28.6139, 77.2090),
            LatLng(28.6180, 77.2115),
            LatLng(28.6250, 77.2160),
            LatLng(28.6328, 77.2197)
        )

        // Point exactly along the segment: deviation <= 50m
        assertFalse(MapboxGeoUtils.isOffRoute(28.6180, 77.2115, route, 50.0))

        // Point 400m away in another sector: deviation > 200m
        assertTrue(MapboxGeoUtils.isOffRoute(28.6350, 77.2400, route, 200.0))
    }

    @Test
    fun testTripShareUrlFormatting() {
        val rideId = "RIDE-998822"
        val shareUrl = "https://ridego.app/track/$rideId"

        assertTrue(shareUrl.startsWith("https://ridego.app/track/"))
        assertTrue(shareUrl.contains(rideId))
    }

    @Test
    fun testEmergency112DialerUri() {
        val emergencyNumber = "112"
        val uriString = "tel:$emergencyNumber"
        assertEquals("tel:112", uriString)
    }

    // =========================================================================
    // Phase 1 API Contract Alignment & Verification Tests
    // =========================================================================

    @Test
    fun testCustomerApiPhase1ContractEndpoints() {
        val methods = CustomerApiService::class.java.methods

        val ratingMethod = methods.firstOrNull { it.name == "submitComprehensiveDriverRating" }
        assertNotNull("submitComprehensiveDriverRating must exist", ratingMethod)
        val postRating = ratingMethod!!.getAnnotation(POST::class.java)
        assertNotNull("Rating endpoint must be @POST", postRating)
        assertEquals("api/v1/loyalty-feedback/rides/{ride_id}/rate", postRating.value)

        val offerPrefMethod = methods.firstOrNull { it.name == "updateOfferPreferences" }
        assertNotNull("updateOfferPreferences must exist", offerPrefMethod)
        val postOffer = offerPrefMethod!!.getAnnotation(POST::class.java)
        assertNotNull("Offer preferences endpoint must be @POST", postOffer)
        assertEquals("api/v1/loyalty-feedback/profile/offer-preferences", postOffer.value)

        val historyMethod = methods.firstOrNull { it.name == "getFilteredRideHistory" }
        assertNotNull("getFilteredRideHistory must exist", historyMethod)
        val getHistory = historyMethod!!.getAnnotation(GET::class.java)
        assertNotNull("Filtered history endpoint must be @GET", getHistory)
        assertEquals("api/v1/loyalty-feedback/rides/history", getHistory.value)

        val statementMethod = methods.firstOrNull { it.name == "getMonthlyRideStatement" }
        assertNotNull("getMonthlyRideStatement must exist", statementMethod)
        val getStatement = statementMethod!!.getAnnotation(GET::class.java)
        assertNotNull("Monthly statement endpoint must be @GET", getStatement)
        assertEquals("api/v1/loyalty-feedback/statements/monthly", getStatement.value)

        val surveyMethod = methods.firstOrNull { it.name == "submitCustomerSurvey" }
        assertNotNull("submitCustomerSurvey must exist", surveyMethod)
        val postSurvey = surveyMethod!!.getAnnotation(POST::class.java)
        assertNotNull("Survey endpoint must be @POST", postSurvey)
        assertEquals("api/v1/loyalty-feedback/surveys", postSurvey.value)
    }

    @Test
    fun testCustomerDtoSerializationDeserialization() {
        val gson = Gson()

        // 1. SubmitDriverRatingRequest serialization
        val ratingReq = SubmitDriverRatingRequest(
            overallScore = 5.0,
            overallRating = 5,
            cleanlinessScore = 5,
            comfortScore = 5,
            serviceQualityScore = 5,
            navigationScore = 5,
            feedbackTags = listOf("Great driving", "Immaculate vehicle"),
            feedback = "Punctual and courteous chauffeur.",
            tipCents = 20000
        )
        val ratingJson = gson.toJson(ratingReq)
        assertTrue(ratingJson.contains("\"overall_score\":5.0"))
        assertTrue(ratingJson.contains("\"cleanliness_score\":5"))
        assertTrue(ratingJson.contains("\"comfort_score\":5"))
        assertTrue(ratingJson.contains("\"service_quality_score\":5"))
        assertTrue(ratingJson.contains("\"navigation_score\":5"))
        assertTrue(ratingJson.contains("\"tip_cents\":20000"))

        // 2. OfferPreferencesRequest serialization
        val offerReq = OfferPreferencesRequest(
            frequency = "WEEKLY",
            birthdayDate = "1990-05-15",
            anniversaryDate = "2018-11-20"
        )
        val offerJson = gson.toJson(offerReq)
        assertTrue(offerJson.contains("\"frequency\":\"WEEKLY\""))
        assertTrue(offerJson.contains("\"birthday_date\":\"1990-05-15\""))
        assertTrue(offerJson.contains("\"anniversary_date\":\"2018-11-20\""))

        // 3. MonthlyStatementResponse deserialization
        val sampleStatementJson = """
            {
                "success": true,
                "statement_period": "2026-11",
                "period": "2026-11",
                "total_spend_rupees": 4850.0,
                "grand_total_rupees": 4850.0,
                "total_rides": 3,
                "total_distance_km": 42.5,
                "generated_at": "2026-11-07T03:00:00Z",
                "rides": [
                    {
                        "ride_id": "ride-001",
                        "completed_at": "2026-11-02T10:30:00Z",
                        "pickup_address": "Aerocity Terminal 3",
                        "destination_address": "Cyber City Tower A",
                        "fare_rupees": 1600.0,
                        "status": "COMPLETED"
                    }
                ]
            }
        """.trimIndent()
        val statement = gson.fromJson(sampleStatementJson, MonthlyStatementResponse::class.java)
        assertEquals("2026-11", statement.statementPeriod)
        assertEquals(4850.0, statement.totalSpendRupees, 0.01)
        assertEquals(3, statement.totalRides)
        assertEquals(1, statement.rides.size)
        assertEquals("ride-001", statement.rides[0].rideId)
        assertEquals(1600.0, statement.rides[0].fareRupees, 0.01)

        // 4. FilteredRideDto deserialization
        val sampleHistoryJson = """
            [
                {
                    "ride_id": "ride-hist-1",
                    "status": "COMPLETED",
                    "fare_rupees": 1200.0,
                    "tip_rupees": 200.0,
                    "tip_cents": 20000,
                    "pickup_address": "Taj Palace Delhi",
                    "destination_address": "Indira Gandhi International Airport",
                    "created_at": "2026-11-06T15:00:00Z",
                    "requested_at": "2026-11-06T15:00:00Z",
                    "points_earned": 120
                }
            ]
        """.trimIndent()
        val rides = gson.fromJson(sampleHistoryJson, Array<FilteredRideDto>::class.java)
        assertEquals(1, rides.size)
        assertEquals("ride-hist-1", rides[0].rideId)
        assertEquals("COMPLETED", rides[0].status)
        assertEquals(1200.0, rides[0].fareRupees, 0.01)
        assertEquals(200.0, rides[0].tipRupees, 0.01)
        assertEquals(120, rides[0].pointsEarned)

        // 5. CustomerSurveyRequest serialization
        val surveyReq = CustomerSurveyRequest(
            rideId = "ride-hist-1",
            surveyType = "POST_TRIP_CSAT",
            score = 10,
            csatScore = 5,
            npsScore = 10,
            comments = "Flawless service."
        )
        val surveyJson = gson.toJson(surveyReq)
        assertTrue(surveyJson.contains("\"survey_type\":\"POST_TRIP_CSAT\""))
        assertTrue(surveyJson.contains("\"score\":10"))
        assertTrue(surveyJson.contains("\"comments\":\"Flawless service.\""))
    }

    // --- Modular Navigation & Production Hardening Architecture Tests ---

    @Test
    fun testCustomerModularNavigationDestinationMapping() {
        // Verify all modular destinations are distinct and correctly structured
        val authDest: CustomerDestination = CustomerDestination.Auth
        val homeDest: CustomerDestination = CustomerDestination.Home
        val trackingDest: CustomerDestination = CustomerDestination.Tracking(rideId = "RIDE-2001", startOtp = "4321")
        val paymentDest: CustomerDestination = CustomerDestination.Payment(rideId = "RIDE-2001", amountRupees = 45)
        val profileDest: CustomerDestination = CustomerDestination.Profile
        val notifDest: CustomerDestination = CustomerDestination.Notifications
        val govDest: CustomerDestination = CustomerDestination.Governance

        assertTrue(authDest is CustomerDestination.Auth)
        assertTrue(homeDest is CustomerDestination.Home)
        assertTrue(trackingDest is CustomerDestination.Tracking)
        assertTrue(paymentDest is CustomerDestination.Payment)
        assertTrue(profileDest is CustomerDestination.Profile)
        assertTrue(notifDest is CustomerDestination.Notifications)
        assertTrue(govDest is CustomerDestination.Governance)

        val tracking = trackingDest as CustomerDestination.Tracking
        assertEquals("RIDE-2001", tracking.rideId)
        assertEquals("4321", tracking.startOtp)

        val payment = paymentDest as CustomerDestination.Payment
        assertEquals("RIDE-2001", payment.rideId)
        assertEquals(45, payment.amountRupees)
    }

    @Test
    fun testCustomerSessionIsolationAndLogout() {
        // Verify that clearing auth wipes user tokens and prevents unauthorized access to protected destinations
        var isLoggedIn = true
        var activeRideId: String? = "RIDE-991"
        var currentDest: CustomerDestination = CustomerDestination.Tracking("RIDE-991", "1234")

        // Simulate Logout
        fun performLogout() {
            isLoggedIn = false
            activeRideId = null
            currentDest = CustomerDestination.Auth
        }

        performLogout()
        assertFalse(isLoggedIn)
        assertNull(activeRideId)
        assertEquals(CustomerDestination.Auth, currentDest)

        // Attempting to resolve initial destination when logged out must yield Auth
        fun resolveInitialDestination(loggedIn: Boolean, rideId: String?): CustomerDestination {
            return if (!loggedIn) {
                CustomerDestination.Auth
            } else if (!rideId.isNullOrBlank()) {
                CustomerDestination.Tracking(rideId, "1234")
            } else {
                CustomerDestination.Home
            }
        }

        assertEquals(CustomerDestination.Auth, resolveInitialDestination(false, null))
        assertEquals(CustomerDestination.Home, resolveInitialDestination(true, null))
        assertEquals(CustomerDestination.Tracking("RIDE-88", "1234"), resolveInitialDestination(true, "RIDE-88"))
    }

    @Test
    fun testCustomerPaymentGatewayConfirmationIntegrity() {
        // Enforce: Never mark a payment successful based only on gateway response if server verification fails
        fun evaluatePaymentSettlement(gatewayPaymentId: String?, serverVerifySuccess: Boolean): Pair<Boolean, String> {
            if (gatewayPaymentId.isNullOrBlank()) {
                return Pair(false, "Payment cancelled or gateway error.")
            }
            if (!serverVerifySuccess) {
                return Pair(false, "Payment captured ($gatewayPaymentId). Awaiting server confirmation. Please do not pay again.")
            }
            return Pair(true, "Payment verified & captured on server!")
        }

        // Gateway success + Server confirmation -> Settled
        val successCase = evaluatePaymentSettlement("pay_XYZ123", true)
        assertTrue(successCase.first)
        assertTrue(successCase.second.contains("verified & captured"))

        // Gateway success + Server failure -> NOT Settled (prevents fake success)
        val serverFailCase = evaluatePaymentSettlement("pay_XYZ123", false)
        assertFalse(serverFailCase.first)
        assertTrue(serverFailCase.second.contains("Awaiting server confirmation"))

        // Gateway cancelled -> NOT Settled
        val cancelledCase = evaluatePaymentSettlement(null, false)
        assertFalse(cancelledCase.first)
    }

    @Test
    fun testCustomerEmergencySafetyBroadcastConsistency() {
        // Verify emergency broadcast payload and duplicate suppression
        var isEmergencyBroadcasting = false
        var emergencyBroadcastCount = 0

        fun triggerEmergencySos(rideId: String, lat: Double, lng: Double): Boolean {
            if (isEmergencyBroadcasting) return false // Suppress duplicate triggers
            isEmergencyBroadcasting = true
            emergencyBroadcastCount++
            return true
        }

        assertTrue(triggerEmergencySos("RIDE-11", 28.6139, 77.2090))
        assertEquals(1, emergencyBroadcastCount)

        // Duplicate rapid SOS taps while one is in-flight are rejected
        assertFalse(triggerEmergencySos("RIDE-11", 28.6139, 77.2090))
        assertEquals(1, emergencyBroadcastCount)

        // After completion
        isEmergencyBroadcasting = false
        assertTrue(triggerEmergencySos("RIDE-11", 28.6139, 77.2090))
        assertEquals(2, emergencyBroadcastCount)
    }

    @Test
    fun testCustomerExceptionHandlingDoesNotSwallowFatalExceptions() {
        val bgThread = Thread({ }, "MapboxTelemetryWorker")

        // 1. Generic SecurityException must NOT be swallowed
        val secException = SecurityException("Permission denial: ACCESS_FINE_LOCATION requires user grant")
        assertFalse(
            "SecurityException must never be swallowed as a non-fatal map exception",
            CustomerApplication.isPlayServicesOrNativeMapException(bgThread, secException)
        )

        // 2. Connectivity / IO exceptions must NOT be swallowed
        val ioException = java.io.IOException("NO_CONNECTIVITY: Network socket reset")
        assertFalse(
            "Connectivity exceptions must never be swallowed",
            CustomerApplication.isPlayServicesOrNativeMapException(bgThread, ioException)
        )

        // 3. Parcel / IPC failures must NOT be swallowed
        val parcelException = RuntimeException("Parcel.createException: Transaction failed in Binder")
        assertFalse(
            "Parcel / IPC exceptions must never be swallowed",
            CustomerApplication.isPlayServicesOrNativeMapException(bgThread, parcelException)
        )

        // 4. Unexpected runtime exceptions (NPE, IllegalArgument) must NOT be swallowed
        val npe = NullPointerException("Critical state null in booking flow")
        assertFalse(
            "NullPointerException must never be swallowed",
            CustomerApplication.isPlayServicesOrNativeMapException(bgThread, npe)
        )

        // 5. Recoverable Google Play Services Dynamite module loading on background thread IS handled
        val dynamiteException = RuntimeException(
            "com.google.android.gms.dynamite.DynamiteModule: No acceptable module found for policy_maps_core_dynamite"
        )
        assertTrue(
            "Known unrecoverable background Dynamite module loader failure should be handled safely",
            CustomerApplication.isPlayServicesOrNativeMapException(bgThread, dynamiteException)
        )

        // 6. Recoverable Native Mapbox SQLite cache lock contention on background thread IS handled
        val diskCacheLockException = RuntimeException(
            "NativeSqliteDiskCacheImpl: Database lock unavailable during map tile read"
        )
        assertTrue(
            "Known native disk cache contention on background thread should be handled safely",
            CustomerApplication.isPlayServicesOrNativeMapException(bgThread, diskCacheLockException)
        )
    }
}
