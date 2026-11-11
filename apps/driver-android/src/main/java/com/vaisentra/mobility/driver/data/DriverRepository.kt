package com.vaisentra.mobility.driver.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DriverRepository {

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _profile = MutableStateFlow(
        DriverProfileDto(
            id = "drv-75620",
            userId = "1",
            fullName = "Ram Lal (Pilot)",
            phoneNumber = "+91 98112 34567",
            licenseNumber = "DL-0420190082910",
            badgeNumber = "EV-PILOT-829",
            assignedVehiclePlate = "DL-1ER-4921",
            assignedVehicleModel = "Mahindra Treo Electric",
            rating = 4.95,
            completedTrips = 2420,
            acceptanceRate = 96.5
        )
    )
    val profile: StateFlow<DriverProfileDto> = _profile.asStateFlow()

    private val _earnings = MutableStateFlow(DriverEarningsSummaryDto())
    val earnings: StateFlow<DriverEarningsSummaryDto> = _earnings.asStateFlow()

    private val _scorecard = MutableStateFlow(ScorecardDto())
    val scorecard: StateFlow<ScorecardDto> = _scorecard.asStateFlow()

    private val _dailyTarget = MutableStateFlow(DailyIncentiveDto())
    val dailyTarget: StateFlow<DailyIncentiveDto> = _dailyTarget.asStateFlow()

    private val _activeOffer = MutableStateFlow<DispatchOfferDto?>(
        DispatchOfferDto(
            rideId = "ride-offer-8291",
            assignmentId = "asg-01",
            passengerName = "Bhishma Roy",
            passengerPhone = "+91 75620 84184",
            passengerRating = 4.92,
            pickupAddress = "Connaught Place Gate 4, New Delhi",
            destinationAddress = "Sector 62, Cyber Heights, Noida",
            distanceKm = 14.2,
            pickupDistanceMeters = 350,
            estimatedFareRupees = 145.0,
            paymentMode = "UPI",
            timeoutSeconds = 15
        )
    )
    val activeOffer: StateFlow<DispatchOfferDto?> = _activeOffer.asStateFlow()

    fun setDutyStatus(online: Boolean) {
        _isOnline.value = online
    }

    fun dismissOffer() {
        _activeOffer.value = null
    }

    fun requestInstantWithdrawal(amount: Double) {
        val current = _earnings.value
        if (current.walletBalanceRupees >= amount) {
            _earnings.value = current.copy(
                walletBalanceRupees = current.walletBalanceRupees - amount,
                pendingPayoutRupees = current.pendingPayoutRupees + amount
            )
        }
    }
}

