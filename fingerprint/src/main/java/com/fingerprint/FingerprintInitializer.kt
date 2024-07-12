package com.fingerprint

import android.content.Context
import androidx.lifecycle.Lifecycle
import com.fingerprint.device.FingerprintManager
import com.fingerprint.di.FingerprintModule
import kotlinx.coroutines.CoroutineScope


class FingerprintInitializer(
    private val context: Context,
    private val lifecycle: Lifecycle,
    private val scope: CoroutineScope
) {
    private val module: FingerprintModule by lazy {
        FingerprintModule(
            context = context,
            scope = scope,
            lifecycle = lifecycle
        )
    }

    private val instance: FingerprintManager by lazy {
        object : FingerprintManager by module.fingerprintManager {}
    }

    fun create(): FingerprintManager {
        lifecycle.addObserver(instance)
        return instance
    }
}
