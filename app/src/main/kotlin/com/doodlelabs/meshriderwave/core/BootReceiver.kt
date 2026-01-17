/*
 * Mesh Rider Wave - Boot Receiver
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Starts MeshService on device boot
 */

package com.doodlelabs.meshriderwave.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.doodlelabs.meshriderwave.core.network.MeshService
import com.doodlelabs.meshriderwave.core.util.logI

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            logI("Boot completed, starting MeshService")
            startMeshService(context)
        }
    }

    private fun startMeshService(context: Context) {
        val serviceIntent = Intent(context, MeshService::class.java).apply {
            action = MeshService.ACTION_START
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
