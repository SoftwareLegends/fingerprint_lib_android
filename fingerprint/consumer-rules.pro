
-dontwarn com.fingerprint.FingerprintInitializer
-dontwarn com.fingerprint.manager.FingerprintEvent
-dontwarn com.fingerprint.manager.FingerprintEvent$**
-dontwarn com.fingerprint.manager.FingerprintManager

-keep class com.fingerprint.manager.FingerprintEvent$** { *; }
-keep class com.fingerprint.manager.FingerprintDeviceInfo
-keep class com.fingerprint.manager.FingerprintManager
-keep class com.fingerprint.FingerprintInitializer
