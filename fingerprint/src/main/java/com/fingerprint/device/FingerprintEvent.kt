package com.fingerprint.device


sealed class FingerprintEvent(val message: String) {
    data object Idle : FingerprintEvent("Idle")
    data object ProcessCanceledTheFingerLifted : FingerprintEvent("Process Canceled The Finger Lifted")
    data object Connected : FingerprintEvent("Fingerprint Connected")
    data object ConnectingFailed : FingerprintEvent("Connecting Failed")
    data object DeviceAttached : FingerprintEvent("USB Device Attached")
    data object DeviceDetached : FingerprintEvent("USB Device Detached")
    data object Disconnected : FingerprintEvent("Fingerprint Disconnected")
    data object PlaceFinger : FingerprintEvent("Place Finger")
    data object KeepFinger : FingerprintEvent("Keep Finger")
    data object CapturedSuccessfully : FingerprintEvent("Captured Successfully")
    data object CapturingFailed : FingerprintEvent("Capturing Failed")
    class NewImage(val bitmapArray: ByteArray) : FingerprintEvent("New Image")
}
