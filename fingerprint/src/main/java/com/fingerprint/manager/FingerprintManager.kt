package com.fingerprint.manager

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.SharedFlow


interface FingerprintManager : DefaultLifecycleObserver {
    val eventsFlow: SharedFlow<FingerprintEvent>
    val captures: List<ImageBitmap>
    val bestCapture: ImageBitmap?
    val bestCaptureIndex: Int
    val deviceInfo: FingerprintDeviceInfo
    val progress: Float

    fun connect()
    fun disconnect()
    fun scan(count: Int): Boolean
    fun improveTheBestCapture(isApplyFilters: Boolean = false, isBlue: Boolean = false)

    override fun onResume(owner: LifecycleOwner) = connect()

    override fun onStop(owner: LifecycleOwner) = disconnect()
}
