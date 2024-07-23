package com.fingerprint.di

import android.content.Context
import android.hardware.usb.UsbManager
import androidx.core.content.getSystemService
import androidx.lifecycle.Lifecycle
import com.fingerprint.communicator.DefaultUsbDeviceCommunicatorImpl
import com.fingerprint.communicator.UsbDeviceCommunicator
import com.fingerprint.manager.FingerprintManager
import com.fingerprint.manager.FingerprintManagerImpl
import com.fingerprint.scanner.FingerprintScanner
import com.fingerprint.scanner.FutronictechFingerprintScanner
import com.fingerprint.scanner.FutronictechFingerprintScanner.Companion.isFutronicDevice
import com.fingerprint.scanner.HfSecurityFingerprintScanner
import com.fingerprint.scanner.HfSecurityFingerprintScanner.Companion.isHfSecurityDevice
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
): FingerprintScanner? {
    val usbManager = getSystemService<UsbManager>() ?: return null

    return when {
        usbManager.isSupported(::isHfSecurityDevice) -> HfSecurityFingerprintScanner(
            usbDeviceCommunicator = usbDeviceCommunicator
        )

        usbManager.isSupported(::isFutronicDevice) -> FutronictechFingerprintScanner(
            context = this,
            usbDeviceCommunicator = usbDeviceCommunicator
        )

        else -> null
    }

}

private fun UsbManager.isSupported(
    predicate: (
        vendorId: Int,
        productId: Int
    ) -> Boolean
): Boolean = deviceList.values.any { predicate(it.vendorId, it.productId) }
