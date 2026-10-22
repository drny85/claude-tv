package com.claudetv.hub.platform

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.os.Build
import android.view.Display
import com.claudetv.hub.data.VideoFormat

/** What this TV (and any connected soundbar/receiver) reports it can play. */
data class DeviceCapabilities(
    val maxWidth: Int,
    val maxHeight: Int,
    val maxRefreshRate: Float,
    val formats: Set<VideoFormat>,
) {
    val resolutionLabel: String
        get() = when {
            maxHeight >= 2160 || maxWidth >= 3840 -> "4K Ultra HD"
            maxHeight >= 1080 -> "1080p HD"
            else -> "${maxWidth}×$maxHeight"
        }
}

object DisplayCapabilities {

    @Suppress("DEPRECATION")
    fun detect(context: Context): DeviceCapabilities {
        val dm = context.getSystemService(DisplayManager::class.java)
        val display = dm?.getDisplay(Display.DEFAULT_DISPLAY)
        val modes = display?.supportedModes.orEmpty()
        val best = modes.maxByOrNull { it.physicalWidth.toLong() * it.physicalHeight }
        val formats = mutableSetOf<VideoFormat>()

        if (best != null && (best.physicalHeight >= 2160 || best.physicalWidth >= 3840)) formats += VideoFormat.UHD_4K

        val hdrTypes: IntArray = when {
            display == null -> IntArray(0)
            Build.VERSION.SDK_INT >= 34 -> display.mode.supportedHdrTypes
            else -> display.hdrCapabilities?.supportedHdrTypes ?: IntArray(0)
        }
        hdrTypes.forEach {
            when (it) {
                Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> formats += VideoFormat.DOLBY_VISION
                Display.HdrCapabilities.HDR_TYPE_HDR10 -> formats += VideoFormat.HDR10
                Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> formats += VideoFormat.HDR10_PLUS
            }
        }

        val audio = context.getSystemService(AudioManager::class.java)
        val atmos = audio?.getDevices(AudioManager.GET_DEVICES_OUTPUTS).orEmpty().any { dev: AudioDeviceInfo ->
            dev.encodings.any { it == AudioFormat.ENCODING_E_AC3_JOC || it == AudioFormat.ENCODING_DOLBY_TRUEHD }
        }
        if (atmos) formats += VideoFormat.DOLBY_ATMOS

        return DeviceCapabilities(
            maxWidth = best?.physicalWidth ?: 0,
            maxHeight = best?.physicalHeight ?: 0,
            maxRefreshRate = modes.maxOfOrNull { it.refreshRate } ?: 60f,
            formats = formats,
        )
    }
}
