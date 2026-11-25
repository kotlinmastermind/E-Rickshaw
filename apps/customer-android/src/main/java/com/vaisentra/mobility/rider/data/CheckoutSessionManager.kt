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
    ORDER_CREATED,
    CHECKOUT_OPEN,
    PAYMENT_AUTHORIZED,
    VERIFICATION_PENDING,
    VERIFICATION_RETRYABLE,
    VERIFIED,
    TERMINAL_FAILURE,
    CANCELLED,
    EXPIRED,
    RECONCILIATION_REQUIRED
}

data class CheckoutSession(
    val orderId: String,
    val backendPaymentId: String?,
    val checkoutType: CheckoutFlowType,
    val rideId: String?,
    val userId: String? = null,
    val amountCents: Int,
    val currency: String,
    val idempotencyKey: String,
    val state: CheckoutSessionState = CheckoutSessionState.ORDER_CREATED,
    val paymentId: String? = null,
    val signature: String? = null,
    val failureReason: String? = null,
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = createdAtMs
)

sealed class CallbackResolutionResult {
    data class BeginVerification(val session: CheckoutSession) : CallbackResolutionResult()
    data class DuplicateIgnored(val existingState: CheckoutSessionState) : CallbackResolutionResult()
    object MissingIdentity : CallbackResolutionResult()
    object UnknownOrder : CallbackResolutionResult()
    data class OldOrderSuperseded(val orderId: String) : CallbackResolutionResult()
    data class OwnershipMismatch(val orderId: String) : CallbackResolutionResult()
    data class TerminalState(val state: CheckoutSessionState, val reason: String?) : CallbackResolutionResult()
}

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
 * Persists Razorpay checkout sessions strictly keyed by Razorpay order_id and scoped by userId.
 * Separates retryable network failures (VERIFICATION_RETRYABLE) from definitive terminal failures
 * (TERMINAL_FAILURE, CANCELLED, EXPIRED) and reconciliation-required ambiguity.
 */
class CheckoutSessionStore(private val storage: CheckoutStorage) {

    companion object {
        private const val KEY_SESSIONS_MAP = "razorpay_checkout_sessions_v2"
        private const val KEY_INTENT_KEYS_MAP = "razorpay_intent_keys_v2"
        private const val MAX_STORED_SESSIONS = 40
        const val ORDER_STALE_EXPIRY_MS = 30L * 60L * 1000L

        private val gson = Gson()
        private val sessionsMapType = object : TypeToken<MutableMap<String, CheckoutSession>>() {}.type
        private val stringMapType = object : TypeToken<MutableMap<String, String>>() {}.type

        private val VERIFIABLE_START_STATES = setOf(
            CheckoutSessionState.CREATED,
            CheckoutSessionState.ORDER_CREATED,
            CheckoutSessionState.CHECKOUT_OPEN,
            CheckoutSessionState.VERIFICATION_RETRYABLE,
            CheckoutSessionState.PAYMENT_AUTHORIZED
        )

        private val RESTORABLE_STATES = setOf(
            CheckoutSessionState.VERIFICATION_RETRYABLE,
            CheckoutSessionState.VERIFICATION_PENDING,
            CheckoutSessionState.PAYMENT_AUTHORIZED,
            CheckoutSessionState.RECONCILIATION_REQUIRED
        )

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

    fun saveSession(session: CheckoutSession, nowMs: Long = System.currentTimeMillis()) {
        val cleanOrderId = session.orderId.trim()
        require(cleanOrderId.isNotEmpty()) { "CheckoutSession requires a non-blank Razorpay orderId" }
        if (session.checkoutType == CheckoutFlowType.RIDE_FARE) {
            require(!session.rideId.isNullOrBlank()) { "RIDE_FARE session requires a non-blank rideId" }
        }
        synchronized(lock) {
            val sessions = loadSessionsMap()
            expireStaleSessionsLocked(sessions, nowMs)
            // Supersede older open unverified orders belonging to the exact same flow target & user
            sessions.entries.forEach { (k, existing) ->
                val sameUser = existing.userId.isNullOrBlank() || session.userId.isNullOrBlank() || existing.userId == session.userId
                val sameTarget = existing.checkoutType == session.checkoutType && existing.rideId == session.rideId
                if (k != cleanOrderId && sameUser && sameTarget &&
                    existing.state in setOf(CheckoutSessionState.CREATED, CheckoutSessionState.ORDER_CREATED, CheckoutSessionState.CHECKOUT_OPEN)
                ) {
                    sessions[k] = existing.copy(
                        state = CheckoutSessionState.EXPIRED,
                        failureReason = "Superseded by newer checkout order $cleanOrderId",
                        updatedAtMs = nowMs
                    )
                }
            }
            sessions[cleanOrderId] = session.copy(
                orderId = cleanOrderId,
                updatedAtMs = nowMs
            )
            pruneOldEntriesLocked(sessions)
            saveSessionsMap(sessions)
        }
    }

    fun markCheckoutOpen(orderId: String, nowMs: Long = System.currentTimeMillis()): CheckoutSession? {
        val cleanOrderId = orderId.trim()
        if (cleanOrderId.isEmpty()) return null
        synchronized(lock) {
            val sessions = loadSessionsMap()
            val existing = sessions[cleanOrderId] ?: return null
            if (existing.state != CheckoutSessionState.CREATED && existing.state != CheckoutSessionState.ORDER_CREATED) {
                return existing
            }
            val updated = existing.copy(state = CheckoutSessionState.CHECKOUT_OPEN, updatedAtMs = nowMs)
            sessions[cleanOrderId] = updated
            saveSessionsMap(sessions)
            return updated
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
        return sess.state == CheckoutSessionState.VERIFICATION_PENDING || sess.state == CheckoutSessionState.VERIFIED
    }

    fun resolveSuccessCallback(
        orderId: String,
        paymentId: String,
        signature: String,
        currentUserId: String? = null,
        nowMs: Long = System.currentTimeMillis()
    ): CallbackResolutionResult {
        val cleanOrderId = orderId.trim()
        val cleanPaymentId = paymentId.trim()
        val cleanSignature = signature.trim()
        if (cleanOrderId.isEmpty() || cleanPaymentId.isEmpty() || cleanSignature.isEmpty()) {
            return CallbackResolutionResult.MissingIdentity
        }
        synchronized(lock) {
            val sessions = loadSessionsMap()
            val existing = sessions[cleanOrderId] ?: return CallbackResolutionResult.UnknownOrder

            if (!existing.userId.isNullOrBlank() && !currentUserId.isNullOrBlank() && existing.userId != currentUserId) {
                val failed = existing.copy(
                    state = CheckoutSessionState.TERMINAL_FAILURE,
                    failureReason = "Authenticated user does not match checkout session owner.",
                    updatedAtMs = nowMs
                )
                sessions[cleanOrderId] = failed
                saveSessionsMap(sessions)
                return CallbackResolutionResult.OwnershipMismatch(cleanOrderId)
            }

            if (existing.state == CheckoutSessionState.EXPIRED) {
                return CallbackResolutionResult.OldOrderSuperseded(cleanOrderId)
            }
            if (existing.state == CheckoutSessionState.VERIFICATION_PENDING || existing.state == CheckoutSessionState.VERIFIED) {
                return CallbackResolutionResult.DuplicateIgnored(existing.state)
            }
            if (existing.state in setOf(
                    CheckoutSessionState.TERMINAL_FAILURE,
                    CheckoutSessionState.CANCELLED,
                    CheckoutSessionState.RECONCILIATION_REQUIRED
                )
            ) {
                return CallbackResolutionResult.TerminalState(existing.state, existing.failureReason)
            }
            if (existing.state !in VERIFIABLE_START_STATES) {
                return CallbackResolutionResult.TerminalState(existing.state, existing.failureReason)
            }

            val updated = existing.copy(
                state = CheckoutSessionState.VERIFICATION_PENDING,
                paymentId = cleanPaymentId,
                signature = cleanSignature,
                updatedAtMs = nowMs
            )
            sessions[cleanOrderId] = updated
            saveSessionsMap(sessions)
            return CallbackResolutionResult.BeginVerification(updated)
        }
    }

    /**
     * Convenience transition helper returning the locked VERIFICATION_PENDING session if valid, else null.
     */
    fun tryBeginVerification(
        orderId: String,
        paymentId: String,
        signature: String,
        currentUserId: String? = null,
        nowMs: Long = System.currentTimeMillis()
    ): CheckoutSession? {
        return when (val res = resolveSuccessCallback(orderId, paymentId, signature, currentUserId, nowMs)) {
            is CallbackResolutionResult.BeginVerification -> res.session
            else -> null
        }
    }

    fun markRetryableFailure(orderId: String, reason: String? = null, nowMs: Long = System.currentTimeMillis()): CheckoutSession? {
        val cleanOrderId = orderId.trim()
        if (cleanOrderId.isEmpty()) return null
        synchronized(lock) {
            val sessions = loadSessionsMap()
            val existing = sessions[cleanOrderId] ?: return null
            if (existing.state in setOf(CheckoutSessionState.VERIFIED, CheckoutSessionState.TERMINAL_FAILURE, CheckoutSessionState.CANCELLED)) {
                return existing
            }
            val updated = existing.copy(
                state = CheckoutSessionState.VERIFICATION_RETRYABLE,
                failureReason = reason ?: existing.failureReason,
                updatedAtMs = nowMs
            )
            sessions[cleanOrderId] = updated
            saveSessionsMap(sessions)
            return updated
        }
    }

    fun markPaymentAuthorized(orderId: String, reason: String? = null, nowMs: Long = System.currentTimeMillis()): CheckoutSession? {
        val cleanOrderId = orderId.trim()
        if (cleanOrderId.isEmpty()) return null
        synchronized(lock) {
            val sessions = loadSessionsMap()
            val existing = sessions[cleanOrderId] ?: return null
            if (existing.state == CheckoutSessionState.VERIFIED) return existing
            val updated = existing.copy(
                state = CheckoutSessionState.PAYMENT_AUTHORIZED,
                failureReason = reason,
                updatedAtMs = nowMs
            )
            sessions[cleanOrderId] = updated
            saveSessionsMap(sessions)
            return updated
        }
    }

    fun markVerifiedAndClearIntentKey(orderId: String, intentScopeKey: String, nowMs: Long = System.currentTimeMillis()) {
        val cleanOrderId = orderId.trim()
        synchronized(lock) {
            val sessions = loadSessionsMap()
            val existing = sessions[cleanOrderId]
            if (existing != null) {
                sessions[cleanOrderId] = existing.copy(
                    state = CheckoutSessionState.VERIFIED,
                    failureReason = null,
                    updatedAtMs = nowMs
                )
                saveSessionsMap(sessions)
            }
            clearIntentKeyLocked(intentScopeKey)
        }
    }

    fun markTerminalAndClearIntentKey(
        orderId: String?,
        intentScopeKey: String,
        finalState: CheckoutSessionState = CheckoutSessionState.TERMINAL_FAILURE,
        reason: String? = null,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val cleanOrderId = orderId?.trim().orEmpty()
        synchronized(lock) {
            if (cleanOrderId.isNotEmpty()) {
                val sessions = loadSessionsMap()
                val existing = sessions[cleanOrderId]
                if (existing != null && existing.state != CheckoutSessionState.VERIFIED) {
                    sessions[cleanOrderId] = existing.copy(
                        state = finalState,
                        failureReason = reason ?: existing.failureReason,
                        updatedAtMs = nowMs
                    )
                    saveSessionsMap(sessions)
                }
            }
            clearIntentKeyLocked(intentScopeKey)
        }
    }

    fun markReconciliationRequired(
        orderId: String?,
        intentScopeKey: String? = null,
        reason: String,
        nowMs: Long = System.currentTimeMillis()
    ): CheckoutSession? {
        val cleanOrderId = orderId?.trim().orEmpty()
        if (cleanOrderId.isEmpty()) return null
        synchronized(lock) {
            val sessions = loadSessionsMap()
            val existing = sessions[cleanOrderId] ?: return null
            if (existing.state == CheckoutSessionState.VERIFIED) return existing
            val updated = existing.copy(
                state = CheckoutSessionState.RECONCILIATION_REQUIRED,
                failureReason = reason,
                updatedAtMs = nowMs
            )
            sessions[cleanOrderId] = updated
            saveSessionsMap(sessions)
            if (!intentScopeKey.isNullOrBlank()) clearIntentKeyLocked(intentScopeKey)
            return updated
        }
    }

    /**
     * Marks strictly CHECKOUT_OPEN sessions as RECONCILIATION_REQUIRED when a callback omits orderId,
     * never guessing or falling back across Ride vs Wallet flows.
     */
    fun markOpenCheckoutReconciliationRequired(
        reason: String,
        currentUserId: String? = null,
        nowMs: Long = System.currentTimeMillis()
    ): List<CheckoutSession> {
        synchronized(lock) {
            val sessions = loadSessionsMap()
            val updatedList = mutableListOf<CheckoutSession>()
            sessions.entries.forEach { (k, existing) ->
                val sameUser = existing.userId.isNullOrBlank() || currentUserId.isNullOrBlank() || existing.userId == currentUserId
                if (sameUser && existing.state == CheckoutSessionState.CHECKOUT_OPEN) {
                    val updated = existing.copy(
                        state = CheckoutSessionState.RECONCILIATION_REQUIRED,
                        failureReason = reason,
                        updatedAtMs = nowMs
                    )
                    sessions[k] = updated
                    updatedList.add(updated)
                }
            }
            if (updatedList.isNotEmpty()) {
                saveSessionsMap(sessions)
            }
            return updatedList
        }
    }

    fun getRetryableSession(flowType: CheckoutFlowType, currentUserId: String? = null): CheckoutSession? {
        synchronized(lock) {
            val nowMs = System.currentTimeMillis()
            val sessions = loadSessionsMap()
            if (expireStaleSessionsLocked(sessions, nowMs)) {
                saveSessionsMap(sessions)
            }
            return sessions.values
                .filter {
                    val sameUser = it.userId.isNullOrBlank() || currentUserId.isNullOrBlank() || it.userId == currentUserId
                    sameUser &&
                        it.checkoutType == flowType &&
                        it.state == CheckoutSessionState.VERIFICATION_RETRYABLE &&
                        !it.paymentId.isNullOrBlank() &&
                        !it.signature.isNullOrBlank()
                }
                .maxByOrNull { it.updatedAtMs }
        }
    }

    fun getLatestRestorableSession(flowType: CheckoutFlowType, currentUserId: String? = null): CheckoutSession? {
        synchronized(lock) {
            val nowMs = System.currentTimeMillis()
            val sessions = loadSessionsMap()
            if (expireStaleSessionsLocked(sessions, nowMs)) {
                saveSessionsMap(sessions)
            }
            return sessions.values
                .filter {
                    val sameUser = it.userId.isNullOrBlank() || currentUserId.isNullOrBlank() || it.userId == currentUserId
                    sameUser && it.checkoutType == flowType && it.state in RESTORABLE_STATES
                }
                .maxByOrNull { it.updatedAtMs }
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

    private fun expireStaleSessionsLocked(sessions: MutableMap<String, CheckoutSession>, nowMs: Long): Boolean {
        var changed = false
        sessions.entries.forEach { (k, sess) ->
            if (sess.state in setOf(CheckoutSessionState.CREATED, CheckoutSessionState.ORDER_CREATED, CheckoutSessionState.CHECKOUT_OPEN) &&
                (nowMs - sess.createdAtMs) > ORDER_STALE_EXPIRY_MS
            ) {
                sessions[k] = sess.copy(
                    state = CheckoutSessionState.EXPIRED,
                    failureReason = "Checkout session expired before gateway verification.",
                    updatedAtMs = nowMs
                )
                changed = true
            }
        }
        return changed
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
            .sortedBy { it.value.updatedAtMs }
            .take(sessions.size - MAX_STORED_SESSIONS)
            .map { it.key }
        oldestKeys.forEach { sessions.remove(it) }
    }
}
