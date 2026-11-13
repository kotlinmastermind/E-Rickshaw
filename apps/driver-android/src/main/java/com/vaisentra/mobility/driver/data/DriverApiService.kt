package com.vaisentra.mobility.driver.data

import retrofit2.Response
import retrofit2.http.*

/**
 * DriverGo Retrofit API Service connecting to FastAPI + Supabase Backend
 */
interface DriverApiService {

    // --- Pilot Authentication ---
    @POST("api/v1/auth/login")
    suspend fun login(@Body request: DriverLoginRequest): Response<Map<String, Any>>

    @GET("api/v1/drivers/profile")
    suspend fun getProfile(): Response<DriverProfileDto>

    @GET("api/v1/drivers/documents")
    suspend fun getDocuments(): Response<List<DriverDocumentDto>>

    // --- Duty, Availability & Telemetry ---
    @POST("api/v1/drivers/availability")
    suspend fun toggleAvailability(@Body payload: Map<String, Boolean>): Response<Map<String, Any>>

    @POST("api/v1/drivers/location")
    suspend fun pingLocation(@Body request: DriverLocationPingRequest): Response<Map<String, Any>>

    @POST("api/v1/drivers/financials/shift/start")
    suspend fun startShift(@Body request: OdometerLogRequest): Response<Map<String, Any>>

    @POST("api/v1/drivers/financials/shift/end")
    suspend fun endShift(@Body request: OdometerLogRequest): Response<Map<String, Any>>

    // --- Dispatch Offers & Active Trip Ops (Rapido Lifecycle) ---
    @GET("api/v1/drivers/dispatch/offers")
    suspend fun getActiveOffers(): Response<List<DispatchOfferDto>>

    @POST("api/v1/rides/{ride_id}/accept")
    suspend fun acceptRide(@Path("ride_id") rideId: String): Response<Map<String, Any>>

    @POST("api/v1/drivers/dispatch/offers/{ride_id}/reject")
    suspend fun rejectOffer(@Path("ride_id") rideId: String, @Body body: Map<String, String>): Response<Map<String, Any>>

    @POST("api/v1/rides/{ride_id}/arrived")
    suspend fun markArrived(@Path("ride_id") rideId: String): Response<Map<String, Any>>

    @POST("api/v1/rides/{ride_id}/start")
    suspend fun startTripWithOtp(@Path("ride_id") rideId: String, @Body body: Map<String, String>): Response<Map<String, Any>>

    @POST("api/v1/rides/{ride_id}/finish")
    suspend fun finishTrip(@Path("ride_id") rideId: String, @Body body: Map<String, String> = emptyMap()): Response<Map<String, Any>>

    @POST("api/v1/payments/cash-collect")
    suspend fun collectCash(@Body request: CashCollectRequest): Response<Map<String, Any>>

    // --- Financials, Payouts & Goals ---
    @GET("api/v1/drivers/earnings")
    suspend fun getEarningsSummary(): Response<DriverEarningsSummaryDto>

    @POST("api/v1/drivers/payouts/request")
    suspend fun requestInstantPayout(@Body request: PayoutRequestDto): Response<Map<String, Any>>

    @GET("api/v1/drivers/financials/ledger")
    suspend fun getAccountLedger(): Response<List<LedgerItemDto>>

    @GET("api/v1/drivers/scorecard")
    suspend fun getPerformanceScorecard(): Response<ScorecardDto>

    @GET("api/v1/drivers/daily-target")
    suspend fun getDailyTargets(): Response<DailyIncentiveDto>

    // --- Heatmap, Waiting, Battery Swap, Inspection, Payout Accounts, Fatigue, Anomaly & UPI QR ---
    @GET("api/v1/drivers/heat-map")
    suspend fun getHeatMap(): Response<Map<String, Any>>

    @POST("api/v1/rides/{ride_id}/waiting/start")
    suspend fun startWaiting(@Path("ride_id") rideId: String): Response<Map<String, Any>>

    @POST("api/v1/rides/{ride_id}/waiting/stop")
    suspend fun stopWaiting(@Path("ride_id") rideId: String): Response<Map<String, Any>>

    @POST("api/v1/drivers/financials/payout-accounts")
    suspend fun addPayoutAccount(@Body body: Map<String, Any>): Response<Map<String, Any>>

    @GET("api/v1/drivers/financials/payout-accounts")
    suspend fun getPayoutAccounts(): Response<List<Map<String, Any>>>

    @POST("api/v1/drivers/safety/fatigue")
    suspend fun logFatigue(@Body body: Map<String, Any>): Response<Map<String, Any>>

    @POST("api/v1/safety/anomaly/route")
    suspend fun reportRouteAnomaly(@Body body: Map<String, Any>): Response<Map<String, Any>>

    @POST("api/v1/payments/upi-checkout")
    suspend fun generateUpiCheckout(@Body body: Map<String, Any>): Response<Map<String, Any>>

    // --- Safety & Onboarding Ext ---
    @POST("api/v1/drivers/safety/fatigue-check")
    suspend fun submitFatigueCheck(@Body body: Map<String, Any>): Response<Map<String, Any>>

    @POST("api/v1/driver-onboarding/vehicle-inspection")
    suspend fun submitVehicleInspection(@Body body: Map<String, Any>): Response<Map<String, Any>>

    @POST("api/v1/rides/{ride_id}/lost-item")
    suspend fun reportFoundItem(@Path("ride_id") rideId: String, @Body body: Map<String, String>): Response<Map<String, Any>>
}


