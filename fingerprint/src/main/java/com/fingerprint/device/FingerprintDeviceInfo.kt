package com.fingerprint.device

import kotlinx.serialization.Serializable


@Serializable
data class FingerprintDeviceInfo(
    val vendorId: Int?,
    val productId: Int?,
    val model: String?,
    val product: String?,
    val manufacturer: String?
)
