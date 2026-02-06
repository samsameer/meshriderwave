/*
 * Mesh Rider Wave - Call Action Receiver
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * BroadcastReceiver for handling call notification button actions.
 *
 * Follows developer.android.com/guide/topics/ui/notifiers/notifications
 * - Handles ACTION_ANSWER: Launch CallActivity with answer action
 * - Handles ACTION_DECLINE: Send decline response via MeshNetworkManager
 * - Handles ACTION_HANGUP: End ongoing call
 *
 * References:
 * - https://developer.android.com/develop/ui/views/notifications/build-notification#action
 * - https://developer.android.com/guide/components/broadcasts
 */

package com.doodlelabs.meshriderwave.core.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.doodlelabs.meshriderwave.core.network.MeshNetworkManager
import com.doodlelabs.meshriderwave.core.util.Logger
import com.doodlelabs.meshriderwave.core.util.logD
import com.doodlelabs.meshriderwave.core.util.logE
import com.doodlelabs.meshriderwave.core.util.logI
import com.doodlelabs.meshriderwave.core.util.logW
import com.doodlelabs.meshriderwave.presentation.ui.screens.call.CallActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * BroadcastReceiver for call notification actions.
 *
 * Per Android 2026 best practices:
 * - Use explicit intents with setPackage() for security
 * - Handle actions quickly (offload to service/manager)
 * - Register in manifest with proper intent filters
 * - Use Hilt injection for dependency access
 */
@AndroidEntryPoint
class CallActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var meshNetworkManager: MeshNetworkManager

    @Inject
    lateinit var callNotificationManager: CallNotificationManager

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        logI("CallActionReceiver received action: $action")

        when (action) {
            CallNotificationManager.ACTION_ANSWER -> handleAnswer(context, intent)
            CallNotificationManager.ACTION_DECLINE -> handleDecline(context)
            CallNotificationManager.ACTION_HANGUP -> handleHangup(context)
            else -> logW("Unknown action: $action")
        }
    }

    /**
     * Handle answer action - launch CallActivity with answer intent.
     *
     * Per developer.android.com, notifications should launch activity
     * with FLAG_ACTIVITY_NEW_TASK for proper back stack handling.
     */
    private fun handleAnswer(context: Context, intent: Intent) {
        logD("handleAnswer: launching CallActivity")

        // Cancel incoming notification since user is answering
        callNotificationManager.cancelIncomingNotification()

        // Extract call details from original intent
        val remoteAddress = intent.getStringExtra(EXTRA_REMOTE_ADDRESS)
        val offer = intent.getStringExtra(EXTRA_OFFER)
        val senderKeyBase64 = intent.getStringExtra(EXTRA_SENDER_KEY)

        // Launch CallActivity with answer action
        val answerIntent = Intent(context, CallActivity::class.java).apply {
            this.action = CallNotificationManager.ACTION_ANSWER
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(CallActivity.EXTRA_IS_OUTGOING, false)
            putExtra(CallActivity.EXTRA_REMOTE_ADDRESS, remoteAddress)
            putExtra(CallActivity.EXTRA_OFFER, offer)
            putExtra(CallActivity.EXTRA_SENDER_KEY, senderKeyBase64)
        }

        context.startActivity(answerIntent)
    }

    /**
     * Handle decline action - reject incoming call
     * RACE CONDITION FIX Feb 2026: Use atomic get-and-clear
     *
     * Uses the pending call socket stored in MeshService to send
     * the encrypted decline message back to the caller.
     */
    private fun handleDecline(context: Context) {
        logD("handleDecline: sending decline response")

        callNotificationManager.cancelIncomingNotification()

        // Send decline via MeshNetworkManager using stored pending call info
        receiverScope.launch {
            try {
                // RACE CONDITION FIX: Use atomic get-and-clear
                val pendingCall = MeshNetworkManager.PendingCallStore.getAndClearPendingCall()
                if (pendingCall != null) {
                    meshNetworkManager.sendCallResponse(
                        pendingCall.socket,
                        pendingCall.senderPublicKey,
                        null // null = declined
                    )
                    logI("handleDecline: decline sent successfully")
                } else {
                    logW("handleDecline: no pending call found")
                }
            } catch (e: Exception) {
                logE("handleDecline: error sending decline", e)
            }
        }
    }

    /**
     * Handle hangup action - end ongoing call.
     *
     * Sends hangup broadcast that will be picked up by CallActivity
     * or sends signal via MeshNetworkManager to remote peer.
     */
    private fun handleHangup(context: Context) {
        logD("handleHangup: ending ongoing call")

        callNotificationManager.cancelOngoingNotification()

        // Broadcast hangup intent that CallActivity will receive
        val hangupIntent = Intent(ACTION_HANGUP_BROADCAST).apply {
            setPackage(context.packageName)
        }
        context.sendBroadcast(hangupIntent)
    }

    private fun logW(message: String) {
        Logger.w(this, "CallActionReceiver: $message")
    }

    companion object {
        const val EXTRA_REMOTE_ADDRESS = "remote_address"
        const val EXTRA_OFFER = "offer"
        const val EXTRA_SENDER_KEY = "sender_key"
        const val ACTION_HANGUP_BROADCAST = "com.doodlelabs.meshriderwave.HANGUP_BROADCAST"
    }
}
