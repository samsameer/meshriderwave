/*
 * Mesh Rider Wave - Local Crash Handler
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Military-grade offline crash reporting
 * - No internet required
 * - Stores crash logs locally
 * - Exportable via ADB or mesh sync
 */

package com.doodlelabs.meshriderwave.core.crash

import android.content.Context
import android.os.Build
import com.doodlelabs.meshriderwave.core.util.logE
import com.doodlelabs.meshriderwave.core.util.logI
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local crash handler for offline military-grade systems.
 *
 * Features:
 * - Catches uncaught exceptions
 * - Stores crash logs to local storage
 * - No internet or cloud services required
 * - Exportable via ADB: adb pull /sdcard/Android/data/com.doodlelabs.meshriderwave/files/crashes/
 *
 * Usage:
 * ```
 * LocalCrashHandler.install(context)
 * ```
 */
@Singleton
class LocalCrashHandler @Inject constructor() : Thread.UncaughtExceptionHandler {

    private var context: Context? = null
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null

    /**
     * Install the crash handler
     */
    fun install(appContext: Context) {
        this.context = appContext.applicationContext
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
        logI("LocalCrashHandler installed - offline crash reporting enabled")
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            // Generate crash report
            val crashReport = generateCrashReport(thread, throwable)

            // Save to local file
            saveCrashReport(crashReport)

            logE("CRASH CAPTURED: ${throwable.message}")

        } catch (e: Exception) {
            logE("Failed to save crash report: ${e.message}")
        } finally {
            // Call default handler to show system crash dialog
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun generateCrashReport(thread: Thread, throwable: Throwable): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val stackTrace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()

        return buildString {
            appendLine("=" .repeat(60))
            appendLine("MESH RIDER WAVE CRASH REPORT")
            appendLine("=" .repeat(60))
            appendLine()
            appendLine("Timestamp: $timestamp")
            appendLine("Thread: ${thread.name} (ID: ${thread.id})")
            appendLine()
            appendLine("-".repeat(40))
            appendLine("DEVICE INFO")
            appendLine("-".repeat(40))
            appendLine("Manufacturer: ${Build.MANUFACTURER}")
            appendLine("Model: ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.DEVICE}")
            appendLine("Product: ${Build.PRODUCT}")
            appendLine()
            appendLine("-".repeat(40))
            appendLine("APP INFO")
            appendLine("-".repeat(40))
            context?.let { ctx ->
                try {
                    val packageInfo = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
                    appendLine("Package: ${ctx.packageName}")
                    appendLine("Version: ${packageInfo.versionName}")
                    appendLine("Version Code: ${packageInfo.longVersionCode}")
                } catch (e: Exception) {
                    appendLine("Package info unavailable")
                }
            }
            appendLine()
            appendLine("-".repeat(40))
            appendLine("EXCEPTION")
            appendLine("-".repeat(40))
            appendLine("Type: ${throwable.javaClass.name}")
            appendLine("Message: ${throwable.message}")
            appendLine()
            appendLine("-".repeat(40))
            appendLine("STACK TRACE")
            appendLine("-".repeat(40))
            appendLine(stackTrace)
            appendLine()
            appendLine("=" .repeat(60))
            appendLine("END OF CRASH REPORT")
            appendLine("=" .repeat(60))
        }
    }

    private fun saveCrashReport(report: String) {
        val ctx = context ?: return

        // Create crashes directory
        val crashDir = File(ctx.getExternalFilesDir(null), "crashes")
        if (!crashDir.exists()) {
            crashDir.mkdirs()
        }

        // Generate filename with timestamp
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val crashFile = File(crashDir, "crash_$timestamp.txt")

        // Write report
        crashFile.writeText(report)

        logI("Crash report saved: ${crashFile.absolutePath}")

        // Clean up old crash reports (keep last 20)
        cleanupOldCrashReports(crashDir, 20)
    }

    private fun cleanupOldCrashReports(crashDir: File, keepCount: Int) {
        val crashFiles = crashDir.listFiles { file ->
            file.name.startsWith("crash_") && file.name.endsWith(".txt")
        }?.sortedByDescending { it.lastModified() } ?: return

        if (crashFiles.size > keepCount) {
            crashFiles.drop(keepCount).forEach { file ->
                file.delete()
            }
        }
    }

    companion object {
        /**
         * Get all crash reports for export
         */
        fun getCrashReports(context: Context): List<File> {
            val crashDir = File(context.getExternalFilesDir(null), "crashes")
            return crashDir.listFiles { file ->
                file.name.startsWith("crash_") && file.name.endsWith(".txt")
            }?.sortedByDescending { it.lastModified() } ?: emptyList()
        }

        /**
         * Get latest crash report content
         */
        fun getLatestCrashReport(context: Context): String? {
            return getCrashReports(context).firstOrNull()?.readText()
        }

        /**
         * Clear all crash reports
         */
        fun clearCrashReports(context: Context) {
            getCrashReports(context).forEach { it.delete() }
        }

        /**
         * Export crash reports path for ADB
         */
        fun getCrashReportsPath(context: Context): String {
            return File(context.getExternalFilesDir(null), "crashes").absolutePath
        }
    }
}
