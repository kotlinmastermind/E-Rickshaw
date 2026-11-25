package com.vaisentra.mobility.rider.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.concurrent.ConcurrentHashMap

enum class CheckoutFlowType {
    WALLET_TOPUP,
    RIDE_FARE
}

enum class CheckoutSessionState {
    CREATED,
    VERIFYING,
    VERIFIED,
    FAILED,
    CANCELLED
}

data class CheckoutSession(
    val orderId: String,
    val backendPaymentId: String?,
    val checkoutType: CheckoutFlowType,
    val rideId: String?,
    val amountCents: Int,
    val currency: String,
    val idempotencyKey: String,
    val state: CheckoutSessionState = CheckoutSessionState.CREATED,
    val paymentId: String? = null,
    val signature: String? = null,
    val createdAtMs: Long = System.currentTimeMillis()
)

interface CheckoutStorage {
    fun readString(key: String): String?
    fun writeString(key: String, value: String?)
}

class InMemoryCheckoutStorage : CheckoutStorage {
    private val map = ConcurrentHashMap<String, String>()
    override fun readString(key: String): String? = map[key]
    override fun writeString(key: String, value: String?) {
        if (value == null) map.remove(key) else map[key] = value
    }
}

/**
 * Persists Razorpay checkout sessions strictly keyed by Razorpay order_id.
 * Eliminates shared mutable global variables so concurrent flows, Activity recreation,
 * and process restarts never mix Ride Payment and Wallet Top-up callbacks.
 */
class CheckoutSessionStore(private val storage: CheckoutStorage) {

    companion object {
        private const val KEY_SESSIONS_MAP = "razorpay_checkout_sessions_v2"
        private const val KEY_INTENT_KEYS_MAP = "razorpay_intent_keys_v2"
        private const val MAX_STORED_SESSIONS = 25

        private val gson = Gson()
        private val sessionsMapType = object : TypeToken<MutableMap<String, CheckoutSession>>() {}.type
        private val stringMapType = object : TypeToken<MutableMap<String, String>>() {}.type

        fun isValidRazorpayPublicKey(keyId: String?): Boolean {
            val trimmed = keyId?.trim().orEmpty()
            if (trimmed.length < 12) return false
            if (!trimmed.startsWith("rzp_live_") && !trimmed.startsWith("rzp_test_")) return false
            val lower = trimmed.lowercase()
            if (lower.contains("placeholder") || lower.contains("changeme") || lower.contains("yourkey")) return false
            return true
        }
    }

    private val lock = Any()

    fun saveSession(session: CheckoutSession) {
        require(session.orderId.isNotBlank()) { "CheckoutSession requires a non-blank Razorpay orderId" }
        synchronized(lock) {
            val sessions = loadSessionsMap()
            sessions[session.orderId] = session
            pruneOldEntriesLocked(sessions)
            saveSessionsMap(sessions)
        }
    }

    fun getSessionByOrderId(orderId: String?): CheckoutSession? {
        val clean = orderId?.trim().orEmpty()
        if (clean.isEmpty()) return null
        synchronized(lock) {
            return loadSessionsMap()[clean]
        }
    }

    fun hasInFlightVerification(orderId: String?): Boolean {
        val sess = getSessionByOrderId(orderId) ?: return false
        return sess.state == CheckoutSessionState.VERIFYING || sess.state == CheckoutSessionState.VERIFIED
    }

    /**
     * Atomically transitions a CREATED/FAILED session to VERIFYING and stores callback proofs.
     * Returns the updated session if transition is allowed, or null if duplicate/missing/already verified.
     */
    fun tryBeginVerification(orderId: String, paymentId: String, signature: String): CheckoutSession? {
        val cleanOrderId = orderId.trim()
        if (cleanOrderId.isEmpty() || paymentId.isBlank() || signature.isBlank()) return null
        synchronized(lock) {
            val sessions = loadSessionsMap()
            val existing = sessions[cleanOrderId] ?: return null
            if (existing.state == CheckoutSessionState.VERIFYING || existing.state == CheckoutSessionState.VERIFIED) {
                return null
            }
            val updated = existing.copy(
                state = CheckoutSessionState.VERIFYING,
                paymentId = paymentId.trim(),
                signature = signature.trim()
            )
            sessions[cleanOrderId] = updated
            saveSessionsMap(sessions)
            return updated
        }
    }

    fun markRetryableFailure(orderId: String): CheckoutSession? {
        val cleanOrderId = orderId.trim()
        if (cleanOrderId.isEmpty()) return null
        synchronized(lock) {
            val sessions = loadSessionsMap()
            val existing = sessions[cleanOrderId] ?: return null
            if (existing.state == CheckoutSessionState.VERIFIED) return existing
            val updated = existing.copy(state = CheckoutSessionState.FAILED)
            sessions[cleanOrderId] = updated
            saveSessionsMap(sessions)
            return updated
        }
    }

    fun markVerifiedAndClearIntentKey(orderId: String, intentScopeKey: String) {
        val cleanOrderId = orderId.trim()
        synchronized(lock) {
            val sessions = loadSessionsMap()
            val existing = sessions[cleanOrderId]
            if (existing != null) {
                sessions[cleanOrderId] = existing.copy(state = CheckoutSessionState.VERIFIED)
                saveSessionsMap(sessions)
            }
            clearIntentKeyLocked(intentScopeKey)
        }
    }

    fun markTerminalAndClearIntentKey(orderId: String?, intentScopeKey: String, finalState: CheckoutSessionState) {
        val cleanOrderId = orderId?.trim().orEmpty()
        synchronized(lock) {
            if (cleanOrderId.isNotEmpty()) {
                val sessions = loadSessionsMap()
                val existing = sessions[cleanOrderId]
                if (existing != null && existing.state != CheckoutSessionState.VERIFIED) {
                    sessions[cleanOrderId] = existing.copy(state = finalState)
                    saveSessionsMap(sessions)
                }
            }
            clearIntentKeyLocked(intentScopeKey)
        }
    }

    fun getRetryableSession(flowType: CheckoutFlowType): CheckoutSession? {
        synchronized(lock) {
            return loadSessionsMap().values
                .filter {
                    it.checkoutType == flowType &&
                        it.state == CheckoutSessionState.FAILED &&
                        !it.paymentId.isNullOrBlank() &&
                        !it.signature.isNullOrBlank()
                }
                .maxByOrNull { it.createdAtMs }
        }
    }

    fun getOrCreateScopedIdempotencyKey(intentScopeKey: String, prefix: String): String {
        synchronized(lock) {
            val intents = loadIntentMap()
            val existing = intents[intentScopeKey]?.trim().orEmpty()
            if (existing.isNotEmpty()) return existing
            val generated = "${prefix}_${java.util.UUID.randomUUID()}"
            intents[intentScopeKey] = generated
            saveIntentMap(intents)
            return generated
        }
    }

    fun clearScopedIdempotencyKey(intentScopeKey: String) {
        synchronized(lock) {
            clearIntentKeyLocked(intentScopeKey)
        }
    }

    private fun clearIntentKeyLocked(intentScopeKey: String) {
        val intents = loadIntentMap()
        if (intents.remove(intentScopeKey) != null) {
            saveIntentMap(intents)
        }
    }

    private fun loadSessionsMap(): MutableMap<String, CheckoutSession> {
        val raw = storage.readString(KEY_SESSIONS_MAP) ?: return mutableMapOf()
        return try {
            gson.fromJson<MutableMap<String, CheckoutSession>>(raw, sessionsMapType) ?: mutableMapOf()
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    private fun saveSessionsMap(map: MutableMap<String, CheckoutSession>) {
        storage.writeString(KEY_SESSIONS_MAP, gson.toJson(map))
    }

    private fun loadIntentMap(): MutableMap<String, String> {
        val raw = storage.readString(KEY_INTENT_KEYS_MAP) ?: return mutableMapOf()
        return try {
            gson.fromJson<MutableMap<String, String>>(raw, stringMapType) ?: mutableMapOf()
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    private fun saveIntentMap(map: MutableMap<String, String>) {
        storage.writeString(KEY_INTENT_KEYS_MAP, gson.toJson(map))
    }

    private fun pruneOldEntriesLocked(sessions: MutableMap<String, CheckoutSession>) {
        if (sessions.size <= MAX_STORED_SESSIONS) return
        val oldestKeys = sessions.entries
            .sortedBy { it.value.createdAtMs }
            .take(sessions.size - MAX_STORED_SESSIONS)
            .map { it.key }
        oldestKeys.forEach { sessions.remove(it) }
    }
}
