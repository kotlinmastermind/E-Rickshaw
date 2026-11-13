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

            else -> MapHomeScreen { navigateTo(it) }
        }

        // Persistent Rapido-Style Bottom Navigation Bar
        val showBottomBar = currentRoute in listOf(
            "map_home", "wallet_hub", "pilot_assigned", "telemetry_hud", "emergency_sos", "account_hub", "past_trips"
        )
        if (showBottomBar) {
            RiderBottomBar(
                currentRoute = currentRoute,
                onNavigate = { navigateTo(it) },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // Global Dialog and Toast Layer
        RiderGoDialogHost()
    }
}

@Composable
fun RiderBottomBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(SurfaceElevated)
            .border(1.dp, BorderSubtle, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem("🗺️", "Explore", "map_home", currentRoute, onNavigate)
            BottomNavItem("💳", "Wallet", "wallet_hub", currentRoute, onNavigate)
            BottomNavItem("🚘", "Active", "pilot_assigned", currentRoute, onNavigate)
            BottomNavItem("🚨", "Safety", "emergency_sos", currentRoute, onNavigate, isWarning = true)
            BottomNavItem("👤", "Account", "account_hub", currentRoute, onNavigate)
        }
    }
}

@Composable
private fun BottomNavItem(
    icon: String,
    label: String,
    route: String,
    currentRoute: String,
    onNavigate: (String) -> Unit,
    isWarning: Boolean = false
) {
    val isSelected = currentRoute == route
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onNavigate(route) }
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text = icon, fontSize = 18.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) {
                if (isWarning) CrimsonRed else ElectricCyan
            } else TextMuted
        )
    }
}

