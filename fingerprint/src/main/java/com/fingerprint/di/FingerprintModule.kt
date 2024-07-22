package com.fingerprint.di

import android.content.Context
import android.hardware.usb.UsbManager
import androidx.core.content.getSystemService
import androidx.lifecycle.Lifecycle
import com.fingerprint.communication.DefaultUsbDeviceCommunicatorImpl
import com.fingerprint.communication.UsbDeviceCommunicator
import com.fingerprint.device.FingerprintManager
import com.fingerprint.device.FingerprintManagerImpl
import com.fingerprint.scanner.FingerprintScanner
import com.fingerprint.scanner.FutronictechFingerprintScanner
import com.fingerprint.scanner.FutronictechFingerprintScanner.Companion.isFutronicDevice
import com.fingerprint.scanner.HfSecurityFingerprint
import com.fingerprint.scanner.HfSecurityFingerprint.Companion.isHfSecurityDevice
import kotlinx.coroutines.CoroutineScope


internal class FingerprintModule(
    private val context: Context,
    private val lifecycle: Lifecycle,
    private val scope: CoroutineScope
) {
    private val usbDeviceCommunicator: UsbDeviceCommunicator by lazy {
        DefaultUsbDeviceCommunicatorImpl(context = context)
    }

    val fingerprintManager: FingerprintManager by lazy {
        FingerprintManagerImpl(
            scope = scope,
            context = context,
            lifecycle = lifecycle,
            initializeFingerprintScanner = {
                context.getSupportedFingerprintScanner(usbDeviceCommunicator)
            }
        )
    }
}

private fun Context.getSupportedFingerprintScanner(
    usbDeviceCommunicator: UsbDeviceCommunicator
): FingerprintScanner {
    val usbManager = getSystemService<UsbManager>() ?: error("UsbManager not found")

    return when {
        usbManager.isSupported(::isHfSecurityDevice) -> HfSecurityFingerprint(
            usbDeviceCommunicator = usbDeviceCommunicator
        )

        usbManager.isSupported(::isFutronicDevice) -> FutronictechFingerprintScanner(
            context = this,
            usbDeviceCommunicator = usbDeviceCommunicator
        )

        else -> error("Unsupported fingerprint scanner")
    }

}

private fun UsbManager.isSupported(
    predicate: (
        vendorId: Int,
        productId: Int
    ) -> Boolean
): Boolean = deviceList.values.any { predicate(it.vendorId, it.productId) }
