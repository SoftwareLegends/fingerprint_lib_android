package com.fingerprint.di

import android.content.Context
import androidx.lifecycle.Lifecycle
import com.fingerprint.communication.DefaultUsbDeviceCommunicatorImpl
import com.fingerprint.communication.UsbDeviceCommunicator
import com.fingerprint.device.FingerprintManager
import com.fingerprint.device.FingerprintManagerImpl
import com.fingerprint.scanner.FingerprintScanner
import com.fingerprint.scanner.HfSecurityFingerprint
import kotlinx.coroutines.CoroutineScope


internal class FingerprintModule(
    private val context: Context,
    private val lifecycle: Lifecycle,
    private val scope: CoroutineScope
) {
    private val usbDeviceCommunicator: UsbDeviceCommunicator by lazy {
        DefaultUsbDeviceCommunicatorImpl(context = context)
    }

    private val hfSecurityFingerprintScanner: FingerprintScanner by lazy {
        HfSecurityFingerprint(usbDeviceCommunicator = usbDeviceCommunicator)
    }

    val fingerprintManager: FingerprintManager by lazy {
        FingerprintManagerImpl(
            scope = scope,
            context = context,
            lifecycle = lifecycle,
            fingerprintScanner = hfSecurityFingerprintScanner
        )
    }
}
