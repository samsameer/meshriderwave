/*
 * Mesh Rider Wave - SOS/Emergency Broadcast Manager
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Military-grade emergency broadcast system for tactical mesh networks
 *
 * Features:
 * - One-tap SOS activation
 * - Location beacon with continuous updates
 * - Priority override on all channels
 * - Audio recording capability
 * - Team acknowledgement tracking
 * - Hardware SOS button support
 */

package com.doodlelabs.meshriderwave.core.emergency

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.doodlelabs.meshriderwave.core.crypto.CryptoManager
import com.doodlelabs.meshriderwave.core.location.LocationSharingManager
import com.doodlelabs.meshriderwave.core.location.TrackedLocation
import com.doodlelabs.meshriderwave.data.local.database.AppDatabase
import com.doodlelabs.meshriderwave.data.local.database.SOSAlertDao
import com.doodlelabs.meshriderwave.data.local.database.SOSAlertEntity
import com.doodlelabs.meshriderwave.data.local.database.SOSStatus
import com.doodlelabs.meshriderwave.data.local.database.SOSType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID
import com.doodlelabs.meshriderwave.core.di.IoDispatcher
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages SOS/Emergency broadcasts across the mesh network
 *
 * SOS alerts have highest priority and will:
 * - Override all PTT channels
 * - Interrupt ongoing calls
 * - Send continuous location beacons
 * - Play loud alarm sounds on all receiving devices
 */
@Singleton
class SOSManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cryptoManager: CryptoManager,
    private val locationManager: LocationSharingManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    companion object {
        private const val TAG = "MeshRider:SOS"

        // SOS Configuration
        const val BEACON_INTERVAL_MS = 10_000L      // Location update every 10 seconds
        const val AUDIO_RECORD_DURATION_MS = 30_000L  // 30 second voice clip
        val SOS_VIBRATION_PATTERN = longArrayOf(0, 100, 100, 100, 100, 100, 500)

        // Message types
        const val MSG_TYPE_SOS_ALERT: Byte = 0x20
        const val MSG_TYPE_SOS_BEACON: Byte = 0x21
        const val MSG_TYPE_SOS_AUDIO: Byte = 0x22
        const val MSG_TYPE_SOS_ACK: Byte = 0x23
        const val MSG_TYPE_SOS_CANCEL: Byte = 0x24

        // Audio configuration
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    private val database: AppDatabase by lazy { AppDatabase.getInstance(context) }
    private val sosDao: SOSAlertDao by lazy { database.sosAlertDao() }

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                .defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    // Current SOS state
    private val _sosState = MutableStateFlow<SOSState>(SOSState.Inactive)
    val sosState: StateFlow<SOSState> = _sosState.asStateFlow()

    // Active SOS alerts from others
    private val _activeAlerts = MutableStateFlow<List<SOSAlert>>(emptyList())
    val activeAlerts: StateFlow<List<SOSAlert>> = _activeAlerts.asStateFlow()

    // Outgoing SOS broadcasts
    private val _outgoingBroadcasts = MutableSharedFlow<SOSBroadcast>(extraBufferCapacity = 20)
    val outgoingBroadcasts: SharedFlow<SOSBroadcast> = _outgoingBroadcasts.asSharedFlow()

    // Alert counts from database
    val activeAlertCount: Flow<Int> = sosDao.getActiveAlertCount()

    // Active jobs
    private var beaconJob: Job? = null
    private var audioRecordJob: Job? = null
    private var currentAlertId: String? = null

    // ========== SOS Activation ==========

    /**
     * Activate SOS alert
     *
     * @param type Type of emergency
     * @param message Optional text message
     * @param recordAudio Whether to record voice clip
     */
    suspend fun activateSOS(
        myPublicKey: ByteArray,
        myName: String,
        type: SOSType = SOSType.GENERAL,
        message: String? = null,
        recordAudio: Boolean = true
    ): Result<String> {
        if (_sosState.value is SOSState.Active) {
            return Result.failure(IllegalStateException("SOS already active"))
        }

        try {
            val alertId = UUID.randomUUID().toString()
            currentAlertId = alertId

            // Get current location
            val location = locationManager.myLocation.value

            // Create alert entity
            val alertEntity = SOSAlertEntity(
                id = alertId,
                senderKey = myPublicKey.toBase64(),
                senderName = myName,
                alertType = type.ordinal,
                message = message,
                latitude = location?.latitude,
                longitude = location?.longitude,
                altitude = location?.altitude,
                timestamp = System.currentTimeMillis(),
                status = SOSStatus.ACTIVE.ordinal
            )

            // Save to database
            sosDao.insert(alertEntity)

            // Update state
            _sosState.value = SOSState.Active(
                alertId = alertId,
                type = type,
                startTime = System.currentTimeMillis(),
                location = location,
                acknowledgements = emptyList()
            )

            // Vibrate device
            vibrateSOSPattern()

            // Broadcast initial alert
            broadcastSOSAlert(alertId, myPublicKey, myName, type, message, location)

            // Start location beacon
            startLocationBeacon(alertId, myPublicKey, myName)

            // Optionally record audio
            if (recordAudio) {
                startAudioRecording(alertId)
            }

            Log.i(TAG, "SOS activated: $alertId ($type)")
            return Result.success(alertId)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to activate SOS", e)
            _sosState.value = SOSState.Inactive
            return Result.failure(e)
        }
    }

    /**
     * Cancel active SOS
     */
    suspend fun cancelSOS(myPublicKey: ByteArray): Result<Unit> {
        val state = _sosState.value
        if (state !is SOSState.Active) {
            return Result.failure(IllegalStateException("No active SOS"))
        }

        try {
            // Stop beacon
            beaconJob?.cancel()
            beaconJob = null

            // Stop audio recording
            audioRecordJob?.cancel()
            audioRecordJob = null

            // Update database
            sosDao.cancel(state.alertId)

            // Broadcast cancellation
            scope.launch {
                _outgoingBroadcasts.emit(SOSBroadcast(
                    type = BroadcastType.CANCEL,
                    alertId = state.alertId,
                    senderKey = myPublicKey,
                    data = ByteArray(0)
                ))
            }

            // Reset state
            _sosState.value = SOSState.Inactive
            currentAlertId = null

            Log.i(TAG, "SOS cancelled: ${state.alertId}")
            return Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel SOS", e)
            return Result.failure(e)
        }
    }

    // ========== Receiving SOS Alerts ==========

    /**
     * Process received SOS alert from network
     */
    suspend fun processReceivedSOS(
        alertId: String,
        senderKey: ByteArray,
        senderName: String,
        type: SOSType,
        message: String?,
        location: TrackedLocation?
    ) {
        try {
            // Save to database
            val entity = SOSAlertEntity(
                id = alertId,
                senderKey = senderKey.toBase64(),
                senderName = senderName,
                alertType = type.ordinal,
                message = message,
                latitude = location?.latitude,
                longitude = location?.longitude,
                altitude = location?.altitude,
                timestamp = System.currentTimeMillis(),
                status = SOSStatus.ACTIVE.ordinal
            )
            sosDao.insert(entity)

            // Add to active alerts
            val alert = SOSAlert(
                id = alertId,
                senderKey = senderKey,
                senderName = senderName,
                type = type,
                message = message,
                location = location,
                timestamp = System.currentTimeMillis()
            )
            _activeAlerts.value = _activeAlerts.value + alert

            // Vibrate to alert user
            vibrateSOSPattern()

            Log.i(TAG, "Received SOS from $senderName: $type")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to process received SOS", e)
        }
    }

    /**
     * Process location beacon update
     */
    suspend fun processLocationBeacon(
        alertId: String,
        location: TrackedLocation
    ) {
        try {
            // Update alert in database
            val alert = sosDao.getAlertById(alertId) ?: return
            sosDao.update(alert.copy(
                latitude = location.latitude,
                longitude = location.longitude,
                altitude = location.altitude
            ))

            // Update active alerts
            _activeAlerts.value = _activeAlerts.value.map { existing ->
                if (existing.id == alertId) {
                    existing.copy(location = location)
                } else existing
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to process beacon", e)
        }
    }

    /**
     * Acknowledge an SOS alert
     */
    suspend fun acknowledgeAlert(
        alertId: String,
        myPublicKey: ByteArray,
        myName: String
    ): Result<Unit> {
        try {
            // Update database
            sosDao.acknowledge(alertId, myPublicKey.toBase64())

            // Broadcast acknowledgement
            scope.launch {
                _outgoingBroadcasts.emit(SOSBroadcast(
                    type = BroadcastType.ACKNOWLEDGE,
                    alertId = alertId,
                    senderKey = myPublicKey,
                    data = myName.toByteArray()
                ))
            }

            Log.i(TAG, "Acknowledged SOS: $alertId")
            return Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to acknowledge SOS", e)
            return Result.failure(e)
        }
    }

    /**
     * Process received acknowledgement
     */
    suspend fun processAcknowledgement(
        alertId: String,
        acknowledgerKey: ByteArray,
        acknowledgerName: String
    ) {
        val state = _sosState.value
        if (state is SOSState.Active && state.alertId == alertId) {
            val ack = Acknowledgement(
                publicKey = acknowledgerKey,
                name = acknowledgerName,
                timestamp = System.currentTimeMillis()
            )
            _sosState.value = state.copy(
                acknowledgements = state.acknowledgements + ack
            )

            // Vibrate to confirm acknowledgement received
            vibrateBrief()

            Log.i(TAG, "SOS acknowledged by $acknowledgerName")
        }
    }

    /**
     * Mark alert as resolved
     */
    suspend fun resolveAlert(alertId: String): Result<Unit> {
        try {
            sosDao.resolve(alertId)
            _activeAlerts.value = _activeAlerts.value.filter { it.id != alertId }
            Log.i(TAG, "Resolved SOS: $alertId")
            return Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve alert", e)
            return Result.failure(e)
        }
    }

    // ========== Internal Operations ==========

    private fun broadcastSOSAlert(
        alertId: String,
        senderKey: ByteArray,
        senderName: String,
        type: SOSType,
        message: String?,
        location: TrackedLocation?
    ) {
        scope.launch {
            val data = serializeSOSAlert(alertId, type, senderName, message, location)
            _outgoingBroadcasts.emit(SOSBroadcast(
                type = BroadcastType.ALERT,
                alertId = alertId,
                senderKey = senderKey,
                data = data
            ))
        }
    }

    private fun startLocationBeacon(
        alertId: String,
        senderKey: ByteArray,
        senderName: String
    ) {
        beaconJob?.cancel()
        beaconJob = scope.launch {
            while (isActive) {
                delay(BEACON_INTERVAL_MS)

                val location = locationManager.myLocation.value
                if (location != null) {
                    val data = serializeLocationBeacon(alertId, location)
                    _outgoingBroadcasts.emit(SOSBroadcast(
                        type = BroadcastType.BEACON,
                        alertId = alertId,
                        senderKey = senderKey,
                        data = data
                    ))
                }
            }
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun startAudioRecording(alertId: String) {
        audioRecordJob?.cancel()
        audioRecordJob = scope.launch {
            try {
                val bufferSize = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT
                )

                val audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )

                val audioData = ByteArrayOutputStream()
                val buffer = ByteArray(bufferSize)

                audioRecord.startRecording()
                val startTime = System.currentTimeMillis()

                while (isActive && System.currentTimeMillis() - startTime < AUDIO_RECORD_DURATION_MS) {
                    val read = audioRecord.read(buffer, 0, bufferSize)
                    if (read > 0) {
                        audioData.write(buffer, 0, read)
                    }
                }

                audioRecord.stop()
                audioRecord.release()

                // Save audio clip
                val audioBytes = audioData.toByteArray()
                if (audioBytes.isNotEmpty()) {
                    saveAudioClip(alertId, audioBytes)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Audio recording failed", e)
            }
        }
    }

    private fun saveAudioClip(alertId: String, audioData: ByteArray) {
        try {
            val audioDir = File(context.filesDir, "sos_audio")
            if (!audioDir.exists()) audioDir.mkdirs()

            val audioFile = File(audioDir, "$alertId.pcm")
            audioFile.writeBytes(audioData)

            // Update database with audio path
            scope.launch {
                val alert = sosDao.getAlertById(alertId)
                alert?.let {
                    sosDao.update(it.copy(audioPath = audioFile.absolutePath))
                }
            }

            Log.d(TAG, "Saved audio clip: ${audioFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save audio clip", e)
        }
    }

    // ========== Serialization ==========

    private fun serializeSOSAlert(
        alertId: String,
        type: SOSType,
        senderName: String,
        message: String?,
        location: TrackedLocation?
    ): ByteArray {
        val alertIdBytes = alertId.toByteArray()
        val nameBytes = senderName.toByteArray()
        val messageBytes = message?.toByteArray() ?: ByteArray(0)

        val buffer = ByteBuffer.allocate(
            1 +  // type
            1 +  // alert ID length
            alertIdBytes.size +
            1 +  // name length
            nameBytes.size +
            2 +  // message length
            messageBytes.size +
            1 +  // has location flag
            (if (location != null) 24 else 0)  // lat(8) + lon(8) + alt(4) + accuracy(4)
        )

        buffer.put(type.ordinal.toByte())
        buffer.put(alertIdBytes.size.toByte())
        buffer.put(alertIdBytes)
        buffer.put(nameBytes.size.toByte())
        buffer.put(nameBytes)
        buffer.putShort(messageBytes.size.toShort())
        buffer.put(messageBytes)

        if (location != null) {
            buffer.put(1.toByte())
            buffer.putDouble(location.latitude)
            buffer.putDouble(location.longitude)
            buffer.putFloat(location.altitude)
            buffer.putFloat(location.accuracy)
        } else {
            buffer.put(0.toByte())
        }

        return buffer.array()
    }

    private fun serializeLocationBeacon(alertId: String, location: TrackedLocation): ByteArray {
        val alertIdBytes = alertId.toByteArray()
        val buffer = ByteBuffer.allocate(1 + alertIdBytes.size + 24)

        buffer.put(alertIdBytes.size.toByte())
        buffer.put(alertIdBytes)
        buffer.putDouble(location.latitude)
        buffer.putDouble(location.longitude)
        buffer.putFloat(location.altitude)
        buffer.putFloat(location.accuracy)

        return buffer.array()
    }

    // ========== Vibration ==========

    private fun vibrateSOSPattern() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createWaveform(SOS_VIBRATION_PATTERN, -1)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(SOS_VIBRATION_PATTERN, -1)
        }
    }

    private fun vibrateBrief() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(100)
        }
    }

    // ========== Cleanup ==========

    // Track if cleanup has been called (FIXED Jan 2026)
    @Volatile
    private var isCleanedUp = false

    /**
     * Stop all SOS operations
     * FIXED Jan 2026: Made idempotent to prevent double cleanup issues
     */
    fun cleanup() {
        if (isCleanedUp) {
            Log.d(TAG, "SOSManager already cleaned up, skipping")
            return
        }
        isCleanedUp = true

        beaconJob?.cancel()
        audioRecordJob?.cancel()
        beaconJob = null
        audioRecordJob = null
        _sosState.value = SOSState.Inactive
        _activeAlerts.value = emptyList()
        currentAlertId = null
        // Note: scope not cancelled as this is a singleton that may be reused
    }

    // ========== Helpers ==========

    private fun ByteArray.toBase64(): String =
        android.util.Base64.encodeToString(this, android.util.Base64.NO_WRAP)
}

// ========== Data Classes ==========

/**
 * Current SOS state
 */
sealed class SOSState {
    data object Inactive : SOSState()

    data class Active(
        val alertId: String,
        val type: SOSType,
        val startTime: Long,
        val location: TrackedLocation?,
        val acknowledgements: List<Acknowledgement>
    ) : SOSState() {
        val duration: Long
            get() = System.currentTimeMillis() - startTime

        val acknowledgedBy: Int
            get() = acknowledgements.size
    }
}

/**
 * Received SOS alert
 */
data class SOSAlert(
    val id: String,
    val senderKey: ByteArray,
    val senderName: String,
    val type: SOSType,
    val message: String?,
    val location: TrackedLocation?,
    val timestamp: Long
) {
    val age: Long
        get() = System.currentTimeMillis() - timestamp

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SOSAlert) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

/**
 * SOS acknowledgement
 */
data class Acknowledgement(
    val publicKey: ByteArray,
    val name: String,
    val timestamp: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Acknowledgement) return false
        return publicKey.contentEquals(other.publicKey)
    }

    override fun hashCode(): Int = publicKey.contentHashCode()
}

/**
 * Outgoing SOS broadcast for network transmission
 */
data class SOSBroadcast(
    val type: BroadcastType,
    val alertId: String,
    val senderKey: ByteArray,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SOSBroadcast) return false
        return alertId == other.alertId && type == other.type
    }

    override fun hashCode(): Int = 31 * alertId.hashCode() + type.hashCode()
}

enum class BroadcastType {
    ALERT,          // Initial SOS alert
    BEACON,         // Location update beacon
    AUDIO,          // Voice clip
    ACKNOWLEDGE,    // Acknowledgement
    CANCEL          // SOS cancelled
}
