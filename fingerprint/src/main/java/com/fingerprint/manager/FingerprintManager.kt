package com.fingerprint.manager

import android.util.Log
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
    val isConnected: Boolean
    val isScanning: Boolean

    fun connect()
    fun disconnect()
    fun scan(count: Int): Boolean
    fun improveTheBestCapture(isApplyFilters: Boolean = false, isBlue: Boolean = false)

    override fun onResume(owner: LifecycleOwner) = runIfNotScanning(::connect)

    override fun onStop(owner: LifecycleOwner) = runIfNotScanning(::disconnect)
}

private fun FingerprintManager.runIfNotScanning(block: () -> Unit) {
    if (isScanning)
        Log.i("DEBUGGING ->", "FingerprintManager is scanning")
    else
        block()
}
