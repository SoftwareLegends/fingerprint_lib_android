package com.fingerprint.manager

import kotlinx.serialization.Serializable


@Serializable
data class FingerprintDeviceInfo(
    val vendorId: Int? = null,
    val productId: Int? = null,
    val model: String? = null,
    val product: String? = null,
    val manufacturer: String? = null
) {
    companion object {
        val Unknown = FingerprintDeviceInfo()
    }
}
