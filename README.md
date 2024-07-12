[![](https://jitpack.io/v/SoftwareLegends/fingerprint_lib_android.svg)](https://jitpack.io/#SoftwareLegends/fingerprint_lib_android)

# Android Fingerprint Library 🖨️

This library provides a simple and efficient way to integrate fingerprint scanners into your Android applications. It offers support for various fingerprint scanner models and streamlines the process of capturing and processing fingerprint images.

<br/>

## Features

- **Easy Integration:** Get started quickly with a straightforward setup process.
- **Real-time Events:** Respond to fingerprint events like device connection, capture success, and more.
- **Image Handling:** Conveniently access and utilize captured fingerprint images.
- **Multiple Scanner Support:**  Compatible with fingerprint scanners from different manufacturers including HF Security and Futronic.

<br/>

## Preview

<table>
  <tr>
    <th>Before</th>
    <th>After</th>
  </tr>
  <tr>
    <td width="50%"> <video src="x"/> </td>
    <td width="50%"> <video src="x"/> </td>
  </tr>
</table>

<br/>

## Getting Started

### 1. Add JitPack Repository

Add the JitPack repository to your root `build.gradle` file:

```gradle
allprojects {
    repositories {
        ...
        maven { url = "https://jitpack.io" }
    }
}
```

### 2. Add the Dependency

Include the library dependency in your module-level `build.gradle` file:

```gradle
dependencies {
    implementation("com.github.SoftwareLegends:fingerprint_lib_android:<LATEST_VERSION>")
}
```

Replace `<LATEST_VERSION>` with the latest version from the badge at the top of this README.

<br/>

## Usage

### Initialization

Initialize the fingerprint manager in your activity or fragment:

```kotlin
import com.fingerprint.FingerprintInitializer

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ... 

        val fingerprintManager = FingerprintInitializer(
            context = this,
            lifecycle = lifecycle, 
            scope = CoroutineScope(Dispatchers.IO) 
        ).create()

        // ... Use fingerprintManager
    }
}
```

### Method 1: Handling Fingerprint Events

You can collect fingerprint events using a flow:

```kotlin
lifecycleScope.launch {
    fingerprintManager.eventsFlow.collect { event ->
        when (event) {
            FingerprintEvent.CapturingFailed -> { /* Handle capture failure */ }
            FingerprintEvent.Connected -> { /* Handle successful connection */ }
            FingerprintEvent.ConnectingFailed -> { /* Handle connection failure */ }
            // ... Handle other events
            is FingerprintEvent.NewImage -> { 
                val bitmap = event.bitmapArray // Access the captured fingerprint image
                // ... Do something with the bitmap
            }
        }
    }
}
```

**Or using `collectAsState()` in compose**
```kotlin
val event = fingerprintManager.eventsFlow.collectAsState()

LaunchedEffect(key1 = event) {
    println(event.message)
    when (event) {
        FingerprintEvent.CapturingFailed -> { /* Handle capture failure */ }
        FingerprintEvent.Connected -> { /* Handle successful connection */ }
        FingerprintEvent.ConnectingFailed -> { /* Handle connection failure */ }
        // ... Handle other events
        is FingerprintEvent.NewImage -> { 
        val bitmap = event.bitmapArray // Access the captured fingerprint image
        // ... Do something with the bitmap
        }
    }
}
```

### Method 2: Accessing Captured Images

Retrieve a list of captured fingerprint images:

```kotlin
val capturedImages: List<ImageBitmap> = fingerprintManager.captures 
```

<br/>

## Supported Fingerprint Scanners

- **[HF Security](https://hfsecurity.cn/)**
    - [HF4000](https://hfsecurity.cn/hf4000-optical-android-fingerprint-scanner/) `(Tested and verified)` ✅
    - Other models may also be compatible.
- **[Futronic](https://www.futronic-tech.com/)**
    - [FS80H](https://www.futronic-tech.com/pro-detail.php?pro_id=1543) `(Work in progress)` ⏳

<br/>

## Sample Application

A sample application demonstrating the library's functionality is available in the [`app`](/app) directory.

<br/>

## Contributing

Contributions are welcome! If you find any issues or have suggestions for improvement, please feel free to open an issue or submit a pull request.

<br/>

## License

This project is licensed under the [MIT License](LICENSE).

<br/>
