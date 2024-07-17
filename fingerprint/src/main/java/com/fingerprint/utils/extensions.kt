package com.fingerprint.utils

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Suppress("UnusedReceiverParameter")
fun Any.returnUnit() = Unit

inline fun<reified T> T.toJson(): String = Json.encodeToString(this)
