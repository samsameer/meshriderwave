/**
 * MR Wave Plugin Lifecycle Provider
 *
 * ContentProvider that ATAK uses to discover and load the plugin.
 * This is the entry point for the ATAK plugin system.
 *
 * Copyright (C) 2024-2026 DoodleLabs. All Rights Reserved.
 */
package com.doodlelabs.meshriderwave.atak

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.util.Log

/**
 * ContentProvider that serves as the entry point for ATAK plugin loading.
 *
 * ATAK discovers plugins by querying ContentProviders with specific metadata.
 * The plugin-lifecycle metadata points to the main plugin class.
 */
class MRWavePluginLifecycleProvider : ContentProvider() {

    companion object {
        private const val TAG = "MRWavePluginLifecycle"
    }

    override fun onCreate(): Boolean {
        Log.i(TAG, "onCreate: MeshRider Wave ATAK Plugin provider created")
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        Log.d(TAG, "query: $uri")
        return null
    }

    override fun getType(uri: Uri): String? {
        return null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        return null
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        return 0
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        return 0
    }
}
