package com.fingerprint.device

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.StateFlow


interface FingerprintManager : DefaultLifecycleObserver {
    val eventsFlow: StateFlow<FingerprintEvent>
    val captures: List<ImageBitmap>
    val bestCapture: ImageBitmap?
    val bestCaptureIndex: Int
    val deviceInfo: FingerprintDeviceInfo
    val progress: Float

    fun connect()
    fun disconnect()
    fun scan(count: Int): Boolean

    override fun onResume(owner: LifecycleOwner) = connect()

    override fun onStop(owner: LifecycleOwner) = disconnect()
}
