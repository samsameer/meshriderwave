/*
 * Mesh Rider Wave - Call Notification Manager
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Uses NotificationCompat.CallStyle for incoming/ongoing call notifications.
 * When used with Core-Telecom CallsManager.addCall(), the SDK automatically
 * grants foreground service delegation — no foregroundServiceType="phoneCall" needed.
 *
 * Two separate channels per developer.android.com:
 * - Incoming: IMPORTANCE_HIGH with ringtone
 * - Ongoing: IMPORTANCE_DEFAULT (silent)
 *
 * References:
 * - https://developer.android.com/develop/ui/views/notifications/call-style
 * - https://developer.android.com/develop/connectivity/telecom/voip-app/notifications
 */

package com.doodlelabs.meshriderwave.core.telecom

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.doodlelabs.meshriderwave.MeshRiderApp
import com.doodlelabs.meshriderwave.R
import com.doodlelabs.meshriderwave.core.util.logD
import com.doodlelabs.meshriderwave.core.util.logE
import com.doodlelabs.meshriderwave.core.util.logI
import com.doodlelabs.meshriderwave.presentation.ui.screens.call.CallActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val NOTIFICATION_ID_INCOMING = 2001
        const val NOTIFICATION_ID_ONGOING = 2002

        const val ACTION_ANSWER = "com.doodlelabs.meshriderwave.ACTION_ANSWER_CALL"
        const val ACTION_DECLINE = "com.doodlelabs.meshriderwave.ACTION_DECLINE_CALL"
        const val ACTION_HANGUP = "com.doodlelabs.meshriderwave.ACTION_HANGUP_CALL"
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /**
     * Show incoming call notification using NotificationCompat.CallStyle.forIncomingCall().
     * Must be posted within 5 seconds of CallsManager.addCall().
     */
    fun showIncomingCallNotification(
        callerName: String,
        remoteAddress: String?,
        offer: String?,
        senderKeyBase64: String?
    ) {
        logI("showIncomingCallNotification: caller=$callerName")

        val caller = Person.Builder()
            .setName(callerName)
            .setImportant(true)
            .build()

        // Full-screen intent for locked device
        val fullScreenIntent = Intent(context, CallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(CallActivity.EXTRA_IS_OUTGOING, false)
            putExtra(CallActivity.EXTRA_REMOTE_ADDRESS, remoteAddress)
            putExtra(CallActivity.EXTRA_OFFER, offer)
            putExtra(CallActivity.EXTRA_SENDER_KEY, senderKeyBase64)
        }
        val fullScreenPI = PendingIntent.getActivity(
            context, 0, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Answer action — launches CallActivity with answer action
        val answerIntent = Intent(context, CallActivity::class.java).apply {
            action = ACTION_ANSWER
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(CallActivity.EXTRA_IS_OUTGOING, false)
            putExtra(CallActivity.EXTRA_REMOTE_ADDRESS, remoteAddress)
            putExtra(CallActivity.EXTRA_OFFER, offer)
            putExtra(CallActivity.EXTRA_SENDER_KEY, senderKeyBase64)
        }
        val answerPI = PendingIntent.getActivity(
            context, 1, answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Decline action — broadcast to CallActionReceiver
        val declineIntent = Intent(ACTION_DECLINE).apply {
            setPackage(context.packageName)
            // Pass call details so receiver can decline properly
            putExtra(CallActionReceiver.EXTRA_REMOTE_ADDRESS, remoteAddress)
            putExtra(CallActionReceiver.EXTRA_OFFER, offer)
            putExtra(CallActionReceiver.EXTRA_SENDER_KEY, senderKeyBase64)
        }
        val declinePI = PendingIntent.getBroadcast(
            context, 2, declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Use CallStyle.forIncomingCall — system provides Answer/Decline buttons
        val callStyle = NotificationCompat.CallStyle.forIncomingCall(
            caller, declinePI, answerPI
        )

        // Per developer.android.com 2026:
        // - PRIORITY_HIGH for heads-up display on Android < 8.1 (channel handles 8.1+)
        // - DO NOT set ongoing(true) for incoming calls (blocks swipe-to-dismiss)
        // - setAutoCancel(false) keeps notification until call is answered/declined
        // - FLAG_MUTABLE for pending intents on Android 12+
        // - SAMSUNG FIX: GroupAlertBehavior ensures notification appears even in bundled groups
        // - SAMSUNG FIX: TimeoutAfter prevents notification from being auto-dismissed
        val notification = NotificationCompat.Builder(context, MeshRiderApp.CHANNEL_CALLS_INCOMING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Incoming Call")
            .setContentText(callerName)
            .setContentIntent(fullScreenPI)
            .setFullScreenIntent(fullScreenPI, true)
            .setStyle(callStyle)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Heads-up display
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false) // Don't auto-cancel on tap (use buttons)
            .setOngoing(false) // Allow swipe-to-dismiss (user can still decline)
            .addPerson(caller)
            // SAMSUNG FIX: Ensure notification alerts even when grouped
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            // SAMSUNG FIX: No timeout - keep showing until explicitly cancelled
            .setTimeoutAfter(0) // 0 = no timeout (important for lock screen)
            // SAMSUNG FIX: Show on lock screen even if DND is on (call category)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID_INCOMING, notification)
            logD("showIncomingCallNotification: posted with CallStyle")
        } catch (e: Exception) {
            logE("Failed to post incoming call notification: ${e.message}")
            // Fallback: post without CallStyle if it fails (shouldn't with Core-Telecom)
            postFallbackIncomingNotification(callerName, fullScreenPI, answerPI, declinePI)
        }
    }

    /**
     * Fallback incoming notification without CallStyle for edge cases.
     * PRIORITY_HIGH ensures heads-up display on compatible devices.
     * SAMSUNG FIX: Added GroupAlertBehavior and timeout handling.
     */
    private fun postFallbackIncomingNotification(
        callerName: String,
        fullScreenPI: PendingIntent,
        answerPI: PendingIntent,
        declinePI: PendingIntent
    ) {
        val notification = NotificationCompat.Builder(context, MeshRiderApp.CHANNEL_CALLS_INCOMING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Incoming Call")
            .setContentText(callerName)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Heads-up display
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPI, true)
            .setOngoing(false) // Allow swipe-to-dismiss
            .setAutoCancel(false) // Don't auto-cancel on tap
            .addAction(R.drawable.ic_stop, "Decline", declinePI)
            .addAction(R.drawable.ic_notification, "Answer", answerPI)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // SAMSUNG FIX: Ensure notification appears in bundled groups
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setTimeoutAfter(0) // No timeout for lock screen
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID_INCOMING, notification)
            logD("showIncomingCallNotification: posted fallback (no CallStyle)")
        } catch (e: Exception) {
            logE("Fallback notification also failed: ${e.message}")
        }
    }

    /**
     * Show ongoing call notification using NotificationCompat.CallStyle.forOngoingCall().
     */
    fun showOngoingCallNotification(callerName: String) {
        logI("showOngoingCallNotification: caller=$callerName")

        val caller = Person.Builder()
            .setName(callerName)
            .setImportant(true)
            .build()

        val contentIntent = Intent(context, CallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        val contentPI = PendingIntent.getActivity(
            context, 3, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val hangupIntent = Intent(ACTION_HANGUP).apply {
            setPackage(context.packageName)
        }
        val hangupPI = PendingIntent.getBroadcast(
            context, 4, hangupIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val callStyle = NotificationCompat.CallStyle.forOngoingCall(
            caller, hangupPI
        )

        // SAMSUNG FIX: Added GroupAlertBehavior for ongoing call visibility
        val notification = NotificationCompat.Builder(context, MeshRiderApp.CHANNEL_CALLS_ONGOING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(contentPI)
            .setStyle(callStyle)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setUsesChronometer(true)
            .addPerson(caller)
            // SAMSUNG FIX: Ensure ongoing call notification is visible in bundled groups
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            // SAMSUNG FIX: High priority for ongoing call visibility
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID_ONGOING, notification)
        } catch (e: Exception) {
            logE("Failed to post ongoing call notification: ${e.message}")
            // Fallback without CallStyle
            postFallbackOngoingNotification(callerName, contentPI, hangupPI)
        }
    }

    /**
     * Fallback ongoing notification without CallStyle.
     */
    private fun postFallbackOngoingNotification(
        callerName: String,
        contentPI: PendingIntent,
        hangupPI: PendingIntent
    ) {
        val notification = NotificationCompat.Builder(context, MeshRiderApp.CHANNEL_CALLS_ONGOING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Ongoing Call")
            .setContentText(callerName)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(contentPI)
            .setOngoing(true)
            .addAction(R.drawable.ic_stop, "Hang Up", hangupPI)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setUsesChronometer(true)
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID_ONGOING, notification)
        } catch (e: Exception) {
            logE("Fallback ongoing notification also failed: ${e.message}")
        }
    }

    /**
     * Cancel incoming call notification.
     */
    fun cancelIncomingNotification() {
        notificationManager.cancel(NOTIFICATION_ID_INCOMING)
        logD("cancelIncomingNotification")
    }

    /**
     * Cancel ongoing call notification.
     */
    fun cancelOngoingNotification() {
        notificationManager.cancel(NOTIFICATION_ID_ONGOING)
        logD("cancelOngoingNotification")
    }

    /**
     * Cancel all call notifications.
     */
    fun cancelAll() {
        notificationManager.cancel(NOTIFICATION_ID_INCOMING)
        notificationManager.cancel(NOTIFICATION_ID_ONGOING)
    }
}
