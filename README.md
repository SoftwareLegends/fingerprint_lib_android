[![](https://jitpack.io/v/SoftwareLegends/fingerprint_lib_android.svg)](https://jitpack.io/#SoftwareLegends/fingerprint_lib_android)

# Fingerprint Library

To get a Git project into your build:

Step 1. Add the JitPack repository to your build file
Add it in your root `build.gradle` at the end of repositories:

```gradle
allprojects {
    repositories {
        ...
        maven { url 'https://jitpack.io' }
    }
}
```

Step 2. Add the dependency

```gradle
dependencies {
	  implementation("com.github.SoftwareLegends:fingerprint_lib_android:<VERSION>")
}
```

## Usage

**Initialize the library**
```kotlin
...
import com.fingerprint.FingerprintInitializer

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        ...
	
        val fingerprintManager = FingerprintInitializer(
            context = this,
            lifecycle = lifecycle,
            scope = CoroutineScope(Dispatchers.IO)
        ).create()
    }
}
```

**Method 1: Collect the fingerprint events**
```kotlin
val events by fingerprintManager.eventsFlow.collectAsState()

LaunchedEffect(key1 = events) {
    println(events.message)
    when (val event = events) {
        FingerprintEvent.CapturingFailed -> TODO()
        FingerprintEvent.Connected -> TODO()
        FingerprintEvent.ConnectingFailed -> TODO()
        FingerprintEvent.DeviceAttached -> TODO()
        FingerprintEvent.DeviceDetached -> TODO()
        FingerprintEvent.Disconnected -> TODO()
        FingerprintEvent.Idle -> TODO()
        FingerprintEvent.KeepFinger -> TODO()
        FingerprintEvent.PlaceFinger -> TODO()
        FingerprintEvent.Timeout -> TODO()
        FingerprintEvent.CapturedSuccessfully -> TODO()
        is FingerprintEvent.NewImage -> println(event.bitmapArray)
        FingerprintEvent.ProcessCanceledTheFingerLifted -> TODO()
    }
}
```

**Method 2: Use `captures: List<ImageBitmap>` property which is suitable for compose and already mapped.**

```kotlin
fingerprintManager.captures
```
