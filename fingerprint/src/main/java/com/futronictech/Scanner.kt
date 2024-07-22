@file:Suppress("unused")
package com.futronictech

import com.fingerprint.scanner.FutronictechFingerprintScanner
import java.io.File


@Suppress("PrivatePropertyName")
internal class Scanner(cacheDir: File) {
    private var m_hDevice: Long = 0
    private var m_ErrorCode = FTR_ERROR_NO_ERROR
    private var m_NFIQ = 0
    private var m_ImageHeight = 0
    private var m_ImageWidth = 0
    var isSyncDirInitialized = false

    init {
        isSyncDirInitialized = setGlobalSyncDir(cacheDir.absolutePath)
    }

    @JvmName("OpenDeviceCtx")
    external fun openDeviceOnInterfaceUsbHost(obj: FutronictechFingerprintScanner): Boolean

    @JvmName("CloseDevice")
    external fun closeDevice(): Boolean

    @JvmName("GetDiodesStatus")
    external fun getDiodesStatus(bytes: ByteArray?): Boolean

    @JvmName("GetFrame")
    external fun getFrame(bytes: ByteArray?): Boolean

    @JvmName("GetImage")
    external fun getImage(i: Int, bytes: ByteArray?): Boolean

    @JvmName("GetImage2")
    external fun getImage2(i: Int, bytes: ByteArray?): Boolean

    @JvmName("GetImageByVariableDose")
    external fun getImageByVariableDose(i: Int, bytes: ByteArray?): Boolean

    @JvmName("GetImageSize")
    external fun getImageSize(): Boolean

    @JvmName("GetInterfaces")
    external fun getInterfaces(bytes: ByteArray?): Boolean

    @JvmName("GetNfiqFromImage")
    external fun getNfiqFromImage(bytes: ByteArray?, i: Int, i2: Int): Boolean

    @JvmName("GetSerialNumber")
    external fun getSerialNumber(): String?

    @JvmName("GetVersionInfo")
    external fun getVersionInfo(): String?

    @JvmName("IsFingerPresent")
    external fun isFingerPresent(): Boolean

    @JvmName("OpenDevice")
    external fun openDevice(): Boolean

    @JvmName("OpenDeviceOnInterface")
    external fun openDeviceOnInterface(i: Int): Boolean

    @JvmName("Restore7Bytes")
    external fun restore7Bytes(bytes: ByteArray?): Boolean

    @JvmName("RestoreSecret7Bytes")
    external fun restoreSecret7Bytes(inBytes: ByteArray?, outBytes: ByteArray?): Boolean

    @JvmName("Save7Bytes")
    external fun save7Bytes(bytes: ByteArray?): Boolean

    @JvmName("SaveSecret7Bytes")
    external fun saveSecret7Bytes(inBytes: ByteArray?, outBytes: ByteArray?): Boolean

    @JvmName("SetDiodesStatus")
    external fun setDiodesStatus(i: Int, i2: Int): Boolean

    @JvmName("SetGlobalSyncDir")
    external fun setGlobalSyncDir(str: String?): Boolean

    @JvmName("SetLogOptions")
    external fun setLogOptions(i: Int, i2: Int, str: String?): Boolean

    @JvmName("SetNewAuthorizationCode")
    external fun setNewAuthorizationCode(bytes: ByteArray?): Boolean

    @JvmName("SetOptions")
    external fun setOptions(i: Int, i2: Int): Boolean

    val imageWidth: Int
        get() = m_ImageWidth

    val imageHeight: Int
        get() = m_ImageHeight

    val errorCode: Int
        get() = m_ErrorCode

    val nifqValue: Int
        get() = m_NFIQ

    val errorMessage: String
        get() = when (this.errorCode) {
            FTR_ERROR_NO_ERROR -> "OK"
            FTR_ERROR_WRITE_PROTECT -> "Write Protect"
            FTR_ERROR_EMPTY_FRAME -> "Empty Frame"
            FTR_ERROR_MOVABLE_FINGER -> "Moveable Finger"
            FTR_ERROR_NO_FRAME -> "Fake Finger"
            FTR_ERROR_HARDWARE_INCOMPATIBLE -> "Hardware Incompatible"
            FTR_ERROR_FIRMWARE_INCOMPATIBLE -> "Firmware Incompatible"
            FTR_ERROR_INVALID_AUTHORIZATION_CODE -> "Invalid Authorization Code"
            FTR_ERROR_LIB_USB_ERROR -> "System libUsb error!"
            else -> "Error code is $errorCode"
        }

    val deviceHandle: Long
        get() = m_hDevice

companion object {
        const val FTR_SCAN_INTERFACE_STATUS_CONNECTED: Byte = 0
        const val FTR_SCAN_INTERFACE_STATUS_DISCONNECTED: Byte = 1
        const val FTR_MAX_INTERFACE_NUMBER: Int = 128
        const val FTR_ERROR_NO_ERROR: Int = 0
        const val FTR_OPTIONS_CHECK_FAKE_REPLICA: Int = 1
        const val FTR_OPTIONS_DETECT_FAKE_FINGER: Int = 1
        const val FTR_OPTIONS_IMPROVE_IMAGE: Int = 32
        const val FTR_OPTIONS_INVERT_IMAGE: Int = 64
        const val FTR_ERROR_EMPTY_FRAME: Int = 4306
        const val FTR_ERROR_MOVABLE_FINGER: Int = 536870913
        const val FTR_ERROR_NO_FRAME: Int = 536870914
        const val FTR_ERROR_HARDWARE_INCOMPATIBLE: Int = 536870916
        const val FTR_ERROR_FIRMWARE_INCOMPATIBLE: Int = 536870917
        const val FTR_ERROR_INVALID_AUTHORIZATION_CODE: Int = 536870918
        const val FTR_ERROR_LIB_USB_ERROR: Int = 536870929
        const val FTR_ERROR_WRITE_PROTECT: Int = 19
        const val FTR_ERROR_NOT_READY: Int = 21
        const val FTR_ERROR_NOT_ENOUGH_MEMORY: Int = 8
        const val FTR_LOG_MASK_OFF: Int = 0
        const val FTR_LOG_MASK_TO_FILE: Int = 1
        const val FTR_LOG_MASK_TO_AUX: Int = 2
        const val FTR_LOG_MASK_TIMESTAMP: Int = 4
        const val FTR_LOG_MASK_THREAD_ID: Int = 8
        const val FTR_LOG_MASK_PROCESS_ID: Int = 16
        const val FTR_LOG_LEVEL_MIN: Int = 0
        const val FTR_LOG_LEVEL_OPTIMAL: Int = 1
        const val FTR_LOG_LEVEL_FULL: Int = 2

        init {
            System.loadLibrary("usb-1.0")
            System.loadLibrary("ftrScanAPI")
            System.loadLibrary("ftrMathAPIAndroid")
            System.loadLibrary("ftrScanApiAndroidJni")
        }
    }
}
