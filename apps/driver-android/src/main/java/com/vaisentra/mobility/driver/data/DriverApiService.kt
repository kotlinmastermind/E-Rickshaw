package com.vaisentra.mobility.driver.data

import retrofit2.Response
import retrofit2.http.*

interface DriverApiService {

    // --- Pilot Authentication ---
    @POST("api/v1/auth/login")
    suspend fun login(@Body request: DriverLoginRequest): Response<Map<String, Any>>

    @GET("api/v1/driver/profile")
    suspend fun getProfile(): Response<DriverProfileDto>

    @GET("api/v1/driver/documents")
    suspend fun getDocuments(): Response<List<DriverDocumentDto>>

    // --- Duty, Availability & Telemetry ---
    @POST("api/v1/driver/status")
    suspend fun updateDutyStatus(@Body request: DutyStatusUpdateRequest): Response<Map<String, Any>>

    @POST("api/v1/dispatch/driver/location")
    suspend fun pingLocation(@Body request: DriverLocationPingRequest): Response<Map<String, Any>>

    @POST("api/v1/driver/shift/start")
    suspend fun startShift(@Body request: OdometerLogRequest): Response<Map<String, Any>>

    @POST("api/v1/driver/shift/end")
    suspend fun endShift(@Body request: OdometerLogRequest): Response<Map<String, Any>>

    // --- Offers & Active Trip Ops ---
    @GET("api/v1/dispatch/offers/current")
    suspend fun getActiveOffer(): Response<DispatchOfferDto>

    @POST("api/v1/dispatch/offers/respond")
    suspend fun respondToOffer(@Body request: OfferResponseRequest): Response<Map<String, Any>>

    @POST("api/v1/driver/rides/{ride_id}/arrived")
    suspend fun markArrived(@Path("ride_id") rideId: String): Response<Map<String, Any>>

    @POST("api/v1/driver/rides/{ride_id}/verify-otp")
    suspend fun verifyStartOtp(@Path("ride_id") rideId: String, @Body request: VerifyOtpRequest): Response<Map<String, Any>>

    @POST("api/v1/driver/rides/{ride_id}/complete")
    suspend fun completeRide(@Path("ride_id") rideId: String): Response<Map<String, Any>>

    @POST("api/v1/driver/rides/{ride_id}/cash-collect")
    suspend fun collectCash(@Path("ride_id") rideId: String, @Body request: CashCollectRequest): Response<Map<String, Any>>

    // --- Financials, Payouts & Goals ---
    @GET("api/v1/driver/earnings")
    suspend fun getEarningsSummary(): Response<DriverEarningsSummaryDto>

    @POST("api/v1/driver/payout/instant")
    suspend fun requestInstantPayout(@Body request: PayoutRequestDto): Response<Map<String, Any>>

    @GET("api/v1/driver/ledger")
    suspend fun getAccountLedger(): Response<List<LedgerItemDto>>

    @GET("api/v1/driver/scorecard")
    suspend fun getPerformanceScorecard(): Response<ScorecardDto>

    @GET("api/v1/driver/targets")
    suspend fun getDailyTargets(): Response<DailyIncentiveDto>
}

