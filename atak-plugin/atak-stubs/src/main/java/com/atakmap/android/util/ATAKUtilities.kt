/**
 * ATAK Utilities Stub
 *
 * Common utility functions provided by ATAK SDK.
 */
package com.atakmap.android.util

import android.content.Context
import android.graphics.drawable.Drawable

object ATAKUtilities {

    @JvmStatic
    fun getIconDrawable(context: Context, iconUri: String): Drawable? = null

    @JvmStatic
    fun scaleToFit(
        context: Context,
        drawable: Drawable,
        width: Int,
        height: Int
    ): Drawable = drawable

    @JvmStatic
    fun getPackageVersion(context: Context): String = "0.0.0"
}
