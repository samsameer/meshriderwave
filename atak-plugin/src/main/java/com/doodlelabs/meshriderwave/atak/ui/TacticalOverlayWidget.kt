/*
 * Mesh Rider Wave ATAK Plugin - Tactical Overlay Widget
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Military-Grade Status Widget for ATAK Map
 * Shows: Radio Status, PTT Activity, Team Count, Mesh Health
 */

package com.doodlelabs.meshriderwave.atak.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * Tactical overlay widget that appears on ATAK map
 * Shows real-time status of MeshRider Wave system
 *
 * Layout:
 * ┌─────────────────────────────────┐
 * │ 🟢 MESH RIDER WAVE             │
 * ├─────────────────────────────────┤
 * │ RADIO    │ MESH   │ COMMS      │
 * │ LINKED   │ 5 NODES│ 3 ONLINE   │
 * ├─────────────────────────────────┤
 * │ CH: ALPHA-1  │ -65dBm │ 25dB   │
 * ├─────────────────────────────────┤
 * │ [PTT]  [SOS]  [CHANNELS]       │
 * └─────────────────────────────────┘
 */
class TacticalOverlayWidget(
    private val context: Context,
    private val onPTTClick: () -> Unit,
    private val onSOSClick: () -> Unit,
    private val onChannelsClick: () -> Unit
) {
    // Status colors
    companion object {
        const val COLOR_OPERATIONAL = 0xFF00E676.toInt()  // Green
        const val COLOR_DEGRADED = 0xFFFFAB00.toInt()     // Amber
        const val COLOR_CRITICAL = 0xFFFF1744.toInt()     // Red
        const val COLOR_OFFLINE = 0xFF6B6B6B.toInt()      // Gray
        const val COLOR_INFO = 0xFF00E5FF.toInt()         // Cyan
        const val COLOR_BACKGROUND = 0xE6121218.toInt()   // Dark with alpha
        const val COLOR_BORDER = 0xFF2A2A35.toInt()       // Subtle border
    }

    // State
    private var isRadioConnected = false
    private var radioSignal = -100
    private var radioSnr = 0
    private var meshNodes = 0
    private var peersOnline = 0
    private var currentChannel = "NONE"
    private var isPTTActive = false
    private var hasActiveSOS = false

    // Views
    private lateinit var rootLayout: LinearLayout
    private lateinit var statusIndicator: View
    private lateinit var statusLabel: TextView
    private lateinit var radioValue: TextView
    private lateinit var meshValue: TextView
    private lateinit var commsValue: TextView
    private lateinit var channelLabel: TextView
    private lateinit var signalLabel: TextView
    private lateinit var snrLabel: TextView
    private lateinit var pttButton: TextView
    private lateinit var sosButton: TextView
    private lateinit var channelsButton: TextView

    /**
     * Create the widget view
     */
    fun createView(): View {
        rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(COLOR_BACKGROUND)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            elevation = dp(8).toFloat()
        }

        // Header row
        val headerRow = createHeaderRow()
        rootLayout.addView(headerRow)

        // Divider
        rootLayout.addView(createDivider())

        // Stats row
        val statsRow = createStatsRow()
        rootLayout.addView(statsRow)

        // Divider
        rootLayout.addView(createDivider())

        // Channel/Signal row
        val channelRow = createChannelRow()
        rootLayout.addView(channelRow)

        // Divider
        rootLayout.addView(createDivider())

        // Action buttons row
        val actionsRow = createActionsRow()
        rootLayout.addView(actionsRow)

        return rootLayout
    }

    private fun createHeaderRow(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(8))

            // Status indicator dot
            statusIndicator = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)).apply {
                    marginEnd = dp(8)
                }
                setBackgroundColor(COLOR_OFFLINE)
            }
            addView(statusIndicator)

            // Title
            statusLabel = TextView(context).apply {
                text = "MESH RIDER WAVE"
                setTextColor(Color.WHITE)
                textSize = 12f
                typeface = android.graphics.Typeface.MONOSPACE
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                letterSpacing = 0.1f
            }
            addView(statusLabel)
        }
    }

    private fun createStatsRow(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(8))

            // Radio status
            addView(createStatItem("RADIO", "OFFLINE").also {
                radioValue = it.getChildAt(1) as TextView
            })

            addView(createVerticalDivider())

            // Mesh nodes
            addView(createStatItem("MESH", "0 NODES").also {
                meshValue = it.getChildAt(1) as TextView
            })

            addView(createVerticalDivider())

            // Comms online
            addView(createStatItem("COMMS", "0 ONLINE").also {
                commsValue = it.getChildAt(1) as TextView
            })
        }
    }

    private fun createStatItem(label: String, value: String): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

            // Label
            addView(TextView(context).apply {
                text = label
                setTextColor(COLOR_OFFLINE)
                textSize = 9f
                typeface = android.graphics.Typeface.MONOSPACE
                gravity = Gravity.CENTER
                letterSpacing = 0.05f
            })

            // Value
            addView(TextView(context).apply {
                text = value
                setTextColor(Color.WHITE)
                textSize = 10f
                typeface = android.graphics.Typeface.MONOSPACE
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
            })
        }
    }

    private fun createChannelRow(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(8))

            // Channel
            channelLabel = TextView(context).apply {
                text = "CH: NONE"
                setTextColor(COLOR_INFO)
                textSize = 11f
                typeface = android.graphics.Typeface.MONOSPACE
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            addView(channelLabel)

            // Signal
            signalLabel = TextView(context).apply {
                text = "-- dBm"
                setTextColor(COLOR_OFFLINE)
                textSize = 10f
                typeface = android.graphics.Typeface.MONOSPACE
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                gravity = Gravity.CENTER
            }
            addView(signalLabel)

            // SNR
            snrLabel = TextView(context).apply {
                text = "-- dB"
                setTextColor(COLOR_OFFLINE)
                textSize = 10f
                typeface = android.graphics.Typeface.MONOSPACE
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                gravity = Gravity.END
            }
            addView(snrLabel)
        }
    }

    private fun createActionsRow(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)

            // PTT Button
            pttButton = createActionButton("PTT", COLOR_OPERATIONAL) {
                onPTTClick()
            }
            addView(pttButton)

            addView(createSpacer())

            // SOS Button
            sosButton = createActionButton("SOS", COLOR_CRITICAL) {
                onSOSClick()
            }
            addView(sosButton)

            addView(createSpacer())

            // Channels Button
            channelsButton = createActionButton("CHANS", COLOR_INFO) {
                onChannelsClick()
            }
            addView(channelsButton)
        }
    }

    private fun createActionButton(label: String, color: Int, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            text = label
            setTextColor(color)
            textSize = 10f
            typeface = android.graphics.Typeface.MONOSPACE
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(16), dp(8), dp(16), dp(8))
            setBackgroundColor(Color.argb(40, Color.red(color), Color.green(color), Color.blue(color)))
            gravity = Gravity.CENTER
            setOnClickListener { onClick() }
        }
    }

    private fun createDivider(): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
            )
            setBackgroundColor(COLOR_BORDER)
        }
    }

    private fun createVerticalDivider(): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(1, dp(30)).apply {
                marginStart = dp(8)
                marginEnd = dp(8)
            }
            setBackgroundColor(COLOR_BORDER)
        }
    }

    private fun createSpacer(): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(8), 1)
        }
    }

    private fun dp(value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }

    // ========================================================================
    // PUBLIC UPDATE METHODS
    // ========================================================================

    /**
     * Update radio connection status
     */
    fun updateRadioStatus(connected: Boolean, signal: Int, snr: Int) {
        isRadioConnected = connected
        radioSignal = signal
        radioSnr = snr

        radioValue.text = if (connected) "LINKED" else "OFFLINE"
        radioValue.setTextColor(if (connected) COLOR_OPERATIONAL else COLOR_CRITICAL)

        if (connected) {
            signalLabel.text = "$signal dBm"
            signalLabel.setTextColor(when {
                signal >= -60 -> COLOR_OPERATIONAL
                signal >= -75 -> COLOR_DEGRADED
                else -> COLOR_CRITICAL
            })

            snrLabel.text = "$snr dB"
            snrLabel.setTextColor(when {
                snr >= 20 -> COLOR_OPERATIONAL
                snr >= 10 -> COLOR_DEGRADED
                else -> COLOR_CRITICAL
            })
        } else {
            signalLabel.text = "-- dBm"
            signalLabel.setTextColor(COLOR_OFFLINE)
            snrLabel.text = "-- dB"
            snrLabel.setTextColor(COLOR_OFFLINE)
        }

        updateOverallStatus()
    }

    /**
     * Update mesh node count
     */
    fun updateMeshNodes(count: Int) {
        meshNodes = count
        meshValue.text = "$count NODES"
        meshValue.setTextColor(if (count > 0) COLOR_INFO else COLOR_OFFLINE)
        updateOverallStatus()
    }

    /**
     * Update peers online count
     */
    fun updatePeersOnline(count: Int) {
        peersOnline = count
        commsValue.text = "$count ONLINE"
        commsValue.setTextColor(when {
            count >= 3 -> COLOR_OPERATIONAL
            count >= 1 -> COLOR_DEGRADED
            else -> COLOR_OFFLINE
        })
        updateOverallStatus()
    }

    /**
     * Update current channel
     */
    fun updateChannel(channelName: String) {
        currentChannel = channelName
        channelLabel.text = "CH: ${channelName.uppercase()}"
    }

    /**
     * Update PTT state
     */
    fun updatePTTState(active: Boolean) {
        isPTTActive = active
        pttButton.text = if (active) "TX" else "PTT"
        pttButton.setBackgroundColor(
            if (active) Color.argb(80, 0, 230, 118)
            else Color.argb(40, 0, 230, 118)
        )
    }

    /**
     * Update SOS state
     */
    fun updateSOSState(active: Boolean) {
        hasActiveSOS = active
        sosButton.text = if (active) "SOS!" else "SOS"
        sosButton.setBackgroundColor(
            if (active) Color.argb(80, 255, 23, 68)
            else Color.argb(40, 255, 23, 68)
        )
        updateOverallStatus()
    }

    /**
     * Update overall status indicator
     */
    private fun updateOverallStatus() {
        val color = when {
            hasActiveSOS -> COLOR_CRITICAL
            !isRadioConnected -> COLOR_CRITICAL
            meshNodes == 0 && peersOnline == 0 -> COLOR_DEGRADED
            isRadioConnected && peersOnline > 0 -> COLOR_OPERATIONAL
            else -> COLOR_DEGRADED
        }
        statusIndicator.setBackgroundColor(color)

        statusLabel.text = when {
            hasActiveSOS -> "SOS ACTIVE"
            !isRadioConnected -> "RADIO OFFLINE"
            meshNodes == 0 && peersOnline == 0 -> "SEARCHING..."
            else -> "MESH RIDER WAVE"
        }
    }
}
