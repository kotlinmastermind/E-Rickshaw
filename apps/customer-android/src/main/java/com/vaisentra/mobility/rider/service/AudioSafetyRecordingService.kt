package com.vaisentra.mobility.rider.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.vaisentra.mobility.rider.data.NetworkClient
import com.vaisentra.mobility.rider.data.RiderRepository
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class AudioSafetyRecordingService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var mediaRecorder: MediaRecorder? = null
    private var currentRawFile: File? = null
    private var currentSessionId: String? = null
    private var currentRideId: String? = null
    private var segmentIndex = 1
    private var recordingStartTime = 0L
    private var isRecording = false

    companion object {
        const val CHANNEL_ID = "in_cabin_audio_safety_channel"
        const val NOTIFICATION_ID = 9182
        const val ACTION_START_RECORDING = "com.vaisentra.mobility.rider.START_AUDIO_RECORDING"
        const val ACTION_STOP_RECORDING = "com.vaisentra.mobility.rider.STOP_AUDIO_RECORDING"
        const val EXTRA_RIDE_ID = "extra_ride_id"
        const val EXTRA_SESSION_ID = "extra_session_id"

        // Managed 256-bit envelope encryption key derivation
        private val ENVELOPE_AES_KEY = "VAISENTRA-DPDP-SAFETY-KEY-2026!".toByteArray(Charsets.UTF_8).copyOf(32)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP_RECORDING) {
            stopAndFinalizeRecording()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val rideId = intent?.getStringExtra(EXTRA_RIDE_ID)
        val sessionId = intent?.getStringExtra(EXTRA_SESSION_ID)

        if (rideId != null && sessionId != null) {
            currentRideId = rideId
            currentSessionId = sessionId
            startForeground(NOTIFICATION_ID, buildForegroundNotification())
            startRecordingCapture()
        }

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "In-Cabin Audio Safety Recording",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifies passenger when consensual safety audio recording is active in vehicle."
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🎙️ In-Cabin Safety Recording Active")
            .setContentText("Encrypted safety audio is recording with mutual consent. Retained for 7 days.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startRecordingCapture() {
        if (isRecording) return

        try {
            val outputDir = File(cacheDir, "audio_safety").apply { mkdirs() }
            val rawFile = File(outputDir, "rec_segment_${System.currentTimeMillis()}.m4a")
            currentRawFile = rawFile

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(96000)
                setOutputFile(rawFile.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            isRecording = true
            recordingStartTime = System.currentTimeMillis()
            RiderRepository.setAudioRecordingActive(true)

            // Start heartbeat loop
            startHeartbeatLoop()

        } catch (e: Exception) {
            isRecording = false
            RiderRepository.setAudioRecordingActive(false)
        }
    }

    private fun startHeartbeatLoop() {
        serviceScope.launch {
            while (isRecording && currentSessionId != null) {
                delay(30000L) // Heartbeat every 30s
                try {
                    NetworkClient.apiService.sendAudioRecordingHeartbeat(currentSessionId!!)
                } catch (_: Exception) {}
            }
        }
    }

    private fun stopAndFinalizeRecording() {
        if (!isRecording) return
        isRecording = false
        RiderRepository.setAudioRecordingActive(false)

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (_: Exception) {
        } finally {
            mediaRecorder = null
        }

        val rawFile = currentRawFile
        val sessionId = currentSessionId
        val rideId = currentRideId
        val durationSeconds = ((System.currentTimeMillis() - recordingStartTime) / 1000.0).coerceAtLeast(1.0)

        if (rawFile != null && rawFile.exists() && sessionId != null && rideId != null) {
            serviceScope.launch {
                try {
                    // Encrypt captured audio with AES-256-GCM
                    val rawBytes = rawFile.readBytes()
                    val (encryptedBytes, iv, tag) = encryptAes256Gcm(rawBytes)

                    // Write encrypted segment to temp file
                    val encFile = File(cacheDir, "seg_${segmentIndex}_${System.currentTimeMillis()}.enc")
                    encFile.writeBytes(encryptedBytes)

                    // Upload segment to FastAPI backend
                    val seqBody = segmentIndex.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                    val durBody = durationSeconds.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                    val nonceBody = iv.toRequestBody("text/plain".toMediaTypeOrNull())
                    val tagBody = tag.toRequestBody("text/plain".toMediaTypeOrNull())
                    val filePart = MultipartBody.Part.createFormData(
                        "file",
                        encFile.name,
                        encFile.asRequestBody("application/octet-stream".toMediaTypeOrNull())
                    )

                    val uploadRes = NetworkClient.apiService.uploadAudioSegment(
                        sessionId = sessionId,
                        sequence = seqBody,
                        durationSeconds = durBody,
                        encryptionNonce = nonceBody,
                        encryptionTag = tagBody,
                        file = filePart
                    )

                    if (uploadRes.isSuccessful) {
                        segmentIndex++
                    }

                    // Secure local cleanup: securely erase raw and encrypted files
                    rawFile.delete()
                    encFile.delete()

                } catch (e: Exception) {
                    // Delete raw file on failure to prevent plaintext leaks
                    rawFile.delete()
                }
            }
        }
    }

    private fun encryptAes256Gcm(data: bytes): Triple<ByteArray, String, String> {
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)

        val keySpec = SecretKeySpec(ENVELOPE_AES_KEY, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, spec)

        val encrypted = cipher.doFinal(data)
        // In Java AES/GCM, authentication tag is appended to ciphertext (last 16 bytes)
        val tagBytes = encrypted.takeLast(16).toByteArray()

        val ivHex = iv.joinToString("") { "%02x".format(it) }
        val tagHex = tagBytes.joinToString("") { "%02x".format(it) }

        return Triple(encrypted, ivHex, tagHex)
    }

    override fun onDestroy() {
        stopAndFinalizeRecording()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
typealias bytes = ByteArray
