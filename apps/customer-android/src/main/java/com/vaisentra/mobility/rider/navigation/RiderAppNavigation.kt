package com.vaisentra.mobility.rider.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaisentra.mobility.rider.components.*
import com.vaisentra.mobility.rider.feature.account.*
import com.vaisentra.mobility.rider.feature.booking.*
import com.vaisentra.mobility.rider.feature.onboarding.*
import com.vaisentra.mobility.rider.feature.pool.*
import com.vaisentra.mobility.rider.feature.posttrip.*
import com.vaisentra.mobility.rider.feature.preferences.*
import com.vaisentra.mobility.rider.feature.safety.*
import com.vaisentra.mobility.rider.feature.support.*
import com.vaisentra.mobility.rider.feature.trip.*
import com.vaisentra.mobility.rider.feature.wallet.*
import com.vaisentra.mobility.rider.theme.*

@Composable
fun RiderAppNavigation() {
    var currentRoute by remember { mutableStateOf("splash") }
    val backStack = remember { mutableStateListOf("splash") }

    fun navigateTo(route: String) {
        backStack.add(route)
        currentRoute = route
    }

    fun navigateBack() {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
            currentRoute = backStack.last()
        } else {
            currentRoute = "map_home"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
    ) {
        when (currentRoute) {
            // Category 1: Onboarding
            "splash" -> BrandSplashScreen { navigateTo(it) }
            "auth_phone" -> PhoneAuthScreen { navigateTo(it) }
            "auth_otp" -> OtpVerifyScreen { navigateTo(it) }
            "auth_reset" -> PasswordResetScreen { navigateTo(it) }
            "auth_kyc" -> CustomerKycScreen { navigateTo(it) }
            "auth_biometric" -> BiometricEnrolScreen { navigateTo(it) }

            // Category 2: Radar & Booking
            "map_home" -> MapHomeScreen { navigateTo(it) }
            "destination_search" -> DestinationSearchScreen { navigateTo(it) }
            "multi_stop" -> MultiStopRouteScreen { navigateTo(it) }
            "fare_matrix" -> VehicleFareMatrixScreen { navigateTo(it) }
            "schedule_ride" -> ScheduleRideScreen { navigateTo(it) }
            "book_guest" -> BookForGuestScreen { navigateTo(it) }

            // Category 3: Preferences
            "cabin_comfort" -> CabinComfortScreen { navigateTo(it) }
            "accessibility_filters" -> AccessibilityFilterScreen { navigateTo(it) }
            "price_lock" -> PriceLockScreen { navigateTo(it) }
            "promo_vouchers" -> PromoVouchersScreen { navigateTo(it) }
            "corporate_profile" -> CorporateProfileScreen { navigateTo(it) }

            // Category 4: Carpool & Shared Transit
            "eco_pool_booking" -> EcoPoolBookingScreen { navigateTo(it) }
            "pool_chat" -> EcoPoolChatScreen { navigateTo(it) }

            // Category 5: Wallet & Pay
            "payment_methods" -> PaymentMethodPickerScreen { navigateTo(it) }
            "wallet_hub" -> CashlessWalletHubScreen { navigateTo(it) }
            "wallet_auto_topup" -> WalletAutoTopupScreen { navigateTo(it) }
            "split_fare" -> SplitFareScreen { navigateTo(it) }

            // Category 6: In-Trip HUD & Realtime
            "dispatch_matching" -> DispatchRadarScreen { navigateTo(it) }
            "pilot_assigned" -> PilotAssignedScreen { navigateTo(it) }
            "driver_rc" -> VehicleRcDocumentScreen { navigateTo(it) }
            "pilot_arrived" -> PilotArrivedScreen { navigateTo(it) }
            "boarding_otp" -> BoardingOtpScreen { navigateTo(it) }
            "telemetry_hud" -> TelemetryHudScreen { navigateTo(it) }
            "in_trip_chat" -> InTripChatScreen { navigateTo(it) }

            // Category 7: Safety, Anomaly & SOS
            "safety_deviation" -> RouteDeviationScreen { navigateTo(it) }
            "safety_audio" -> AudioSafetyScreen { navigateTo(it) }
            "safety_share" -> ShareJourneyScreen { navigateTo(it) }
            "emergency_sos" -> EmergencySosScreen { navigateTo(it) }
            "emergency_contacts" -> EmergencyContactsScreen { navigateTo(it) }

            // Category 8: Post-Trip & Billing
            "ride_receipt" -> RideReceiptScreen { navigateTo(it) }
            "rate_driver" -> DriverRatingScreen { navigateTo(it) }
            "tip_driver" -> DriverTipScreen { navigateTo(it) }
            "past_trips" -> PastTripsHistoryScreen { navigateTo(it) }
            "gst_invoice" -> GstInvoiceScreen { navigateTo(it) }

            // Category 9: Support & Concierge
            "fare_dispute" -> FareDisputeScreen { navigateTo(it) }
            "lost_item" -> LostItemScreen { navigateTo(it) }
            "support_tickets" -> SupportTicketsScreen { navigateTo(it) }
            "support_chat" -> LiveSupportChatScreen { navigateTo(it) }
            "safety_incident" -> SafetyIncidentReportScreen { navigateTo(it) }

            // Category 10: Loyalty & Account
            "refer_earn" -> ReferEarnScreen { navigateTo(it) }
            "green_club" -> GreenClubLoyaltyScreen { navigateTo(it) }
            "notifications" -> NotificationInboxScreen { navigateTo(it) }
            "saved_places" -> SavedPlacesScreen { navigateTo(it) }
            "account_hub" -> AccountSettingsHubScreen { navigateTo(it) }

            // Master 50-Screen Catalog & Popup Test Lab
            "catalog" -> MasterScreenCatalogScreen { route ->
                if (route == "back") navigateBack() else navigateTo(route)
            }

            else -> MapHomeScreen { navigateTo(it) }
        }

        // Global Dialog and Toast Layer
        RiderGoDialogHost()
    }
}

// Master 50-Screen & 15-Popup Explorer
@Composable
fun MasterScreenCatalogScreen(onNavigate: (String) -> Unit) {
    var activeTab by remember { mutableStateOf(0) } // 0: Screens, 1: Popups

    val screenCatalog = listOf(
        // Onboarding
        Triple("1. Brand Splash & Locale", "splash", "Onboarding"),
        Triple("2. Phone & Cyber Auth", "auth_phone", "Onboarding"),
        Triple("3. SMS OTP Quick Login", "auth_otp", "Onboarding"),
        Triple("4. Password & PIN Reset", "auth_reset", "Onboarding"),
        Triple("5. Customer Profile & KYC", "auth_kyc", "Onboarding"),
        Triple("6. Biometric Passkey Enrol", "auth_biometric", "Onboarding"),

        // Radar & Booking
        Triple("7. Map Home & Live Radar", "map_home", "Radar & Booking"),
        Triple("8. Destination Search & Places", "destination_search", "Radar & Booking"),
        Triple("9. Multi-Stop Route Planner", "multi_stop", "Radar & Booking"),
        Triple("10. Vehicle Tier & Fare Matrix", "fare_matrix", "Radar & Booking"),
        Triple("18. Schedule Later & Airport Sync", "schedule_ride", "Radar & Booking"),
        Triple("19. Book for Family / Guest", "book_guest", "Radar & Booking"),

        // Preferences
        Triple("11. Cabin Comfort & Climate AC", "cabin_comfort", "Preferences"),
        Triple("12. Accessibility & EV Filter", "accessibility_filters", "Preferences"),
        Triple("13. Price Lock & Tariff Breakdown", "price_lock", "Preferences"),
        Triple("14. Promo Codes & Vouchers", "promo_vouchers", "Preferences"),
        Triple("20. Corporate & Business Profile", "corporate_profile", "Preferences"),

        // Carpool
        Triple("21. Eco-Pool Shared Seat Booking", "eco_pool_booking", "Carpool"),
        Triple("22. Shared Pool Group Chat", "pool_chat", "Carpool"),

        // Wallet & Pay
        Triple("15. Payment Method & UPI Picker", "payment_methods", "Wallet & Pay"),
        Triple("16. Cashless Wallet Hub", "wallet_hub", "Wallet & Pay"),
        Triple("17. Wallet Auto-Topup Rules", "wallet_auto_topup", "Wallet & Pay"),
        Triple("35. Split Fare with Co-Riders", "split_fare", "Wallet & Pay"),

        // In-Trip HUD
        Triple("23. Dispatch Radar & Pilot Matching", "dispatch_matching", "In-Trip HUD"),
        Triple("24. Pilot Assigned & ETA Tracker", "pilot_assigned", "In-Trip HUD"),
        Triple("25. Driver En-Route & Vehicle RC", "driver_rc", "In-Trip HUD"),
        Triple("26. Pilot Arrived & Meeting Buzzer", "pilot_arrived", "In-Trip HUD"),
        Triple("27. Boarding 4-Digit Security OTP", "boarding_otp", "In-Trip HUD"),
        Triple("28. Telemetry & Speedometer HUD", "telemetry_hud", "In-Trip HUD"),
        Triple("34. In-Trip Pilot Chat & Translate", "in_trip_chat", "In-Trip HUD"),

        // Safety & SOS
        Triple("29. Route Deviation Watchdog", "safety_deviation", "Safety & SOS"),
        Triple("30. Cabin Safety Audio Recording", "safety_audio", "Safety & SOS"),
        Triple("31. Live Journey Link Sharing", "safety_share", "Safety & SOS"),
        Triple("32. Emergency SOS & Police 112", "emergency_sos", "Safety & SOS"),
        Triple("33. Emergency Contacts Manager", "emergency_contacts", "Safety & SOS"),

        // Post-Trip
        Triple("36. Ride Completed & Receipt", "ride_receipt", "Post-Trip"),
        Triple("37. Driver Rating & Factor CSAT", "rate_driver", "Post-Trip"),
        Triple("38. Driver Tip & Gratuity", "tip_driver", "Post-Trip"),
        Triple("39. Past Trips Activity Ledger", "past_trips", "Post-Trip"),
        Triple("40. GST Tax Invoice PDF", "gst_invoice", "Post-Trip"),

        // Support
        Triple("41. Fare Dispute & Refund Claim", "fare_dispute", "Support"),
        Triple("42. Lost Item Concierge Desk", "lost_item", "Support"),
        Triple("43. Support Ticket Helpdesk", "support_tickets", "Support"),
        Triple("44. Live Support Executive Chat", "support_chat", "Support"),
        Triple("45. Safety Incident Formal Report", "safety_incident", "Support"),

        // Loyalty & Account
        Triple("46. Refer & Earn Electric Cash", "refer_earn", "Loyalty & Account"),
        Triple("47. Green Club Loyalty & CO2", "green_club", "Loyalty & Account"),
        Triple("48. Notification Inbox & Alerts", "notifications", "Loyalty & Account"),
        Triple("49. Saved Places & Favorite Pins", "saved_places", "Loyalty & Account"),
        Triple("50. Account Settings & Privacy", "account_hub", "Loyalty & Account")
    )

    val popupCatalog = listOf(
        Pair("1. Ride Cancellation Confirmation", RiderPopupType.CANCELLATION),
        Pair("2. SOS 112 Emergency Panic Alert", RiderPopupType.SOS_112),
        Pair("3. Promo Voucher Applied (₹65 Off)", RiderPopupType.PROMO_APPLIED),
        Pair("4. Pilot Arrived at Gate Buzzer", RiderPopupType.PILOT_ARRIVED),
        Pair("5. Low Wallet Balance Top-Up", RiderPopupType.LOW_WALLET),
        Pair("6. Split Fare Request to Contacts", RiderPopupType.SPLIT_FARE),
        Pair("7. Audio Recording Privacy Consent", RiderPopupType.AUDIO_CONSENT),
        Pair("8. Lost Item Report Acknowledgment", RiderPopupType.LOST_ITEM),
        Pair("9. Green Club Gold Tier Upgrade", RiderPopupType.TIER_UPGRADE),
        Pair("10. Driver Rating & ₹20 Tip Sheet", RiderPopupType.RATING_TIP),
        Pair("11. High Demand 1.35x Surge Notice", RiderPopupType.SURGE_NOTICE),
        Pair("12. Outside Transit Geofence Warning", RiderPopupType.OUT_OF_GEOFENCE),
        Pair("13. GPS Precision Calibration Prompt", RiderPopupType.GPS_CALIBRATION),
        Pair("14. Biometric Passkey Enrolment", RiderPopupType.BIOMETRIC_PASSKEY),
        Pair("15. Airport Flight Delay Reschedule", RiderPopupType.AIRPORT_DELAY)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(16.dp)
    ) {
        NeoHeader(title = "RiderGo UI Inspector", subtitle = "50 Screens • 15 Popups • Material 3", onBack = { onNavigate("back") })
        Spacer(modifier = Modifier.height(10.dp))

        // Tab Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceElevated)
                .padding(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (activeTab == 0) SapphirePrimary else Color.Transparent)
                    .clickable { activeTab = 0 }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "50 Jetpack Screens", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (activeTab == 1) SapphirePrimary else Color.Transparent)
                    .clickable { activeTab = 1 }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "15 Popups & Toasts", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (activeTab == 0) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(screenCatalog) { (title, route, category) ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceCard)
                            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                            .clickable { onNavigate(route) }
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = title, color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(text = category, color = TextDim, fontSize = 11.sp)
                            }
                            Text(text = "Open →", color = ElectricCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(popupCatalog) { (title, popupType) ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceCard)
                            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                            .clickable { DialogController.show(popupType) }
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = title, color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            NeoBadge(text = "TEST MODAL", backgroundColor = SapphireGlow, textColor = ElectricCyan)
                        }
                    }
                }
            }
        }
    }
}

