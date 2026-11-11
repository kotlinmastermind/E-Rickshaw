package com.vaisentra.mobility.core

import com.vaisentra.mobility.core.database.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LocalDatabaseUnitTest {

    @Test
    fun testCachedUserProfileEntity() {
        val profile = CachedUserProfile(
            id = "usr-test-1",
            phoneNumber = "+919876543210",
            fullName = "Ananya Sharma",
            email = "ananya@example.com",
            role = "customer",
            isActive = true,
            isVerified = true,
            rating = 4.95,
            totalRides = 42
        )
        assertEquals("usr-test-1", profile.id)
        assertEquals("+919876543210", profile.phoneNumber)
        assertEquals("Ananya Sharma", profile.fullName)
        assertTrue(profile.isActive)
        assertTrue(profile.isVerified)
        assertEquals(4.95, profile.rating!!, 0.01)
        assertEquals(42, profile.totalRides)
    }

    @Test
    fun testCachedActiveRideEntity() {
        val ride = CachedActiveRide(
            rideId = "ride-777",
            customerId = "cust-123",
            driverId = "drv-456",
            status = "IN_TRANSIT",
            pickupAddress = "Patna Junction, Platform 1",
            destinationAddress = "Gandhi Maidan, Gate 2",
            pickupLat = 25.602,
            pickupLng = 85.137,
            destLat = 25.618,
            destLng = 85.141,
            farePaise = 3000L,
            startOtp = "4821",
            estimatedMinutes = 12
        )
        assertEquals("ride-777", ride.rideId)
        assertEquals("cust-123", ride.customerId)
        assertEquals("drv-456", ride.driverId)
        assertEquals("IN_TRANSIT", ride.status)
        assertEquals(3000L, ride.farePaise)
        assertEquals("4821", ride.startOtp)
        assertEquals(12, ride.estimatedMinutes)
    }

    @Test
    fun testCachedRideHistoryEntity() {
        val history = CachedRideHistory(
            rideId = "hist-101",
            userId = "cust-123",
            pickupAddress = "Boring Road",
            destinationAddress = "Bailey Road",
            farePaise = 2500L,
            status = "COMPLETED",
            vehicleCategory = "E_RICKSHAW_STANDARD",
            completedAt = 1700000000000L,
            rating = 5
        )
        assertEquals("hist-101", history.rideId)
        assertEquals("COMPLETED", history.status)
        assertEquals(5, history.rating)
    }

    @Test
    fun testCachedVehicleCategoryEntity() {
        val cat = CachedVehicleCategory(
            id = "cat-standard",
            code = "E_RICKSHAW_STANDARD",
            name = "Standard E-Rickshaw",
            description = "Eco-friendly 3-wheeler",
            iconUrl = "https://example.com/icon.png",
            capacity = 4,
            sortOrder = 1,
            isElectric = true,
            baseFarePaise = 2000L,
            perKmPaise = 1000L
        )
        assertEquals("E_RICKSHAW_STANDARD", cat.code)
        assertTrue(cat.isElectric)
        assertEquals(2000L, cat.baseFarePaise)
        assertEquals(1000L, cat.perKmPaise)
    }

    @Test
    fun testMockUserProfileDaoContract() = runBlocking {
        val store = mutableMapOf<String, CachedUserProfile>()
        val dao = object : UserProfileDao {
            override suspend fun insertOrUpdate(profile: CachedUserProfile) {
                store[profile.id] = profile
            }

            override suspend fun getProfile(userId: String): CachedUserProfile? {
                return store[userId]
            }

            override suspend fun clearProfile(userId: String) {
                store.remove(userId)
            }

            override suspend fun clearAll() {
                store.clear()
            }
        }

        val testUser = CachedUserProfile(
            id = "u-99",
            phoneNumber = "+919999988888",
            fullName = "Test User",
            email = null,
            role = "customer"
        )
        dao.insertOrUpdate(testUser)
        val retrieved = dao.getProfile("u-99")
        assertNotNull(retrieved)
        assertEquals("Test User", retrieved?.fullName)

        dao.clearProfile("u-99")
        assertNull(dao.getProfile("u-99"))
    }
}
