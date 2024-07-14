[![](https://jitpack.io/v/SoftwareLegends/fingerprint_lib_android.svg)](https://jitpack.io/#SoftwareLegends/fingerprint_lib_android)

# Android Fingerprint Library 🖨️

This library provides a simple and efficient way to integrate fingerprint scanners into your Android applications. It offers support for various fingerprint scanner models and streamlines the process of capturing and processing fingerprint images.

<br/>

## ✨ Key Features

- **Effortless Integration:** Get started quickly with a simple setup.
- **Real-time Feedback:** Respond to fingerprint events such as device connection, capture success, and errors.
- **Image Handling:**  Conveniently access and use captured fingerprint images.
- **Multiple Scanner Support:**  Works with fingerprint scanners from different manufacturers including HF Security and Futronic.


<br/>

## 🎬 Preview

<table>
  <tr>
    <th>Screenshoot</th>
    <th>Video</th>
  </tr>
  <tr align="center">
    <td width="50%"> <img src="https://github.com/user-attachments/assets/9c5c7467-f6d6-475a-a90b-4539e64cbcc4" width="65%"/> </td>
    <td width="50%"> <video src="https://github.com/user-attachments/assets/779f1bb5-38f8-40a5-8d58-70842ab2c399" width="65%"/> </td>
  </tr>
</table>

<br/>

## 🚀 Getting Started

### 1. Include JitPack Repository

In your project's root `build.gradle` file, add the JitPack repository:

```gradle
allprojects {
    repositories {
        ...
        maven { url 'https://jitpack.io' } 
    }
}
```

### 2. Add the Library Dependency

Include the library in your module-level `build.gradle`:

```gradle
dependencies {
    implementation("com.github.SoftwareLegends:fingerprint_lib_android:<LATEST_VERSION>") 
}
```

Replace `<LATEST_VERSION>` with the latest version from the badge at the top of this README.

<br/>

## 💻 Usage

### 1. Initialize the Fingerprint Manager

Initialize the fingerprint manager within your activity or fragment:

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

### 2. Collect Fingerprint Events

Use a Flow to collect and respond to fingerprint events:

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

Alternatively, you can use `collectAsState()` within Compose:

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

### 3. Accessing Captured Images

> Note: This is updated in real-time you can use it instead of `FingerprintEvent.NewImage` event

Retrieve a list of captured images: 

```kotlin
val capturedImages: List<ImageBitmap> = fingerprintManager.captures 
```

### 4. Initiate Fingerprint Scanning

Use the `scan()` method to start capturing fingerprints:

```kotlin
val isScanning = fingerprintManager.scan(count = 5) // 'count' is the number of desired captures

if (isScanning.not()) { 
    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show() 
}
```

**Important Notes:**

- The connection and disconnection lifecycle are automatically handled, simplifying your code.
- Access the best quality capture image with `bestCapture` and its index with `bestCaptureIndex`.

The `FingerprintManager` interface exposes convenient methods and properties for seamless integration:

```kotlin
interface FingerprintManager : DefaultLifecycleObserver {
    val eventsFlow: StateFlow<FingerprintEvent>
    val captures: List<ImageBitmap>
    val bestCapture: ImageBitmap?
    val bestCaptureIndex: Int

    fun connect()
    fun disconnect()
    fun scan(count: Int): Boolean

    override fun onResume(owner: LifecycleOwner) = connect()
    override fun onStop(owner: LifecycleOwner) = disconnect()
}
```

<br/>

## 🤝  Supported Fingerprint Scanners

- **[HF Security](https://hfsecurity.cn/)**
    - [HF4000](https://hfsecurity.cn/hf4000-optical-android-fingerprint-scanner/) `(Tested and verified)` ✅
    - Other models may also be compatible.
- **[Futronic](https://www.futronic-tech.com/)**
    - [FS80H](https://www.futronic-tech.com/pro-detail.php?pro_id=1543) `(Work in progress)` ⏳

<br/>

## 🗃️  Sample Application

A sample application showcasing the library's functionalities is available in the [`app`](/app) directory.

<br/>

## 🙌  Contributing

Contributions are always welcome! If you find any issues or have suggestions for improvement, feel free to open an issue or submit a pull request.

<br/>

## 📄  License

This project is licensed under the [MIT License](LICENSE).

<br/>
