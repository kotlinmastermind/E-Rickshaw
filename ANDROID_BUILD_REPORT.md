# VAISENTRA MOBILITY — ANDROID BUILD & MOBILE AUDIT REPORT
## Multi-Module Build Architecture, Security Hardening & Unit Test Execution

**Project:** E-Rickshaw / Vaisentra Mobility Enterprise  
**Remediation Branch:** `remediation/master-remediation-100`  
**Toolchain:** Android Gradle Plugin 8.3+ / Gradle 9.3.0 / Kotlin 1.9.22 / JDK 17  
**Build Status:** BUILD SUCCESSFUL (58 Tests Passed, 0 Failures)  
**Date:** November 8, 2026  

---

### Executive Summary

The Vaisentra Mobility mobile ecosystem comprises two native Android applications and a shared foundational core library structured as a clean multi-module Gradle project:
1. `:packages:common-android` — Shared core library (networking, security, models, utilities).
2. `:apps:customer-android` — Luxury Rider client application (Jetpack Compose, Mapbox, Razorpay).
3. `:apps:driver-android` — Enterprise Driver client application (Jetpack Compose, background location, KYC).

During this remediation, mobile token storage was migrated to hardware-backed Android Keystore encryption, TLS certificate pinning was implemented, application manifests were hardened against data extraction and cleartext leakage, debug and release signing configurations were strictly decoupled, root detection was integrated, and consumer ProGuard rules were reinforced.

---

### 1. Multi-Module Project Structure

```
c:\androidAi\
├── packages/
│   └── common-android/               # Core shared library
│       ├── src/main/java/.../core/
│       │   ├── model/                 # Shared domain DTOs
│       │   ├── network/               # CoreNetworkClient, ResilientDns
│       │   ├── security/              # KeystoreTokenEncryptor, BiometricAuthHelper, RootDetector, BaseTokenManager
│       │   └── map/                   # MapboxSharedUtils
│       └── consumer-rules.pro         # Library consumer ProGuard rules
├── apps/
│   ├── customer-android/              # Customer Ride Booking App
│   │   ├── src/main/AndroidManifest.xml
│   │   ├── build.gradle.kts
│   │   └── proguard-rules.pro
│   └── driver-android/                # Driver Fleet App
│       ├── src/main/AndroidManifest.xml
│       ├── build.gradle.kts
│       └── proguard-rules.pro
└── gradle/
```

---

### 2. Mobile Security Hardening Measures

#### 2.1 Android Keystore AES-256-GCM Encryption (`KeystoreTokenEncryptor.kt`)
- **Vulnerability Remediated**: Plaintext storage of JWT tokens and user lock PINs in `SharedPreferences`.
- **Implementation**: Utilizes `AndroidKeyStore` provider to generate and manage a 256-bit AES master key (`VaisentraSecureKeystoreAlias`).
- **Cipher**: `AES/GCM/NoPadding` with 12-byte initialization vector and 128-bit authentication tag.
- **Prefixing**: Encrypted ciphertexts are stored with `ENC:GCM:` prefix for automated transparent decryption.
- **Resilience**: Gracefully handles non-Android JVM environments without crashing.

#### 2.2 Biometric Authentication (`BiometricAuthHelper.kt`)
- **Implementation**: Integrates `androidx.biometric.BiometricManager` with `BIOMETRIC_STRONG` authenticators.
- **Capabilities**: Probes hardware presence (`canAuthenticate`), maps status codes (`SUCCESS`, `NO_HARDWARE`, `HW_UNAVAILABLE`, `NONE_ENROLLED`), and gates sensitive ride payment confirmations.

#### 2.3 TLS Certificate Pinning (`CoreNetworkClient.kt`)
- **Implementation**: Added `createProductionCertificatePinner()` configured with SHA-256 pin digests for `e-rickshaw-z32z.onrender.com`.
- **Network Resilience**: 30-second connect/read/write timeouts, automated connection retries, and Anycast DNS fallback (`ResilientDns`).

#### 2.4 Manifest Hardening (`AndroidManifest.xml`)
- `android:allowBackup="false"`: Disables ADB backup extraction of application data and SQLite caches.
- `android:usesCleartextTraffic="false"`: Enforces HTTPS/WSS across all network connections.
- Explicit `android:exported="false"` flags assigned to all background services and broadcast receivers:
  - `CustomerLocationForegroundService` (exported = false)
  - `DriverLocationForegroundService` (exported = false)
  - `DriverDispatchActionReceiver` (exported = false)
- Only `MainActivity` retains `android:exported="true"` as the application launcher.

#### 2.5 Root & Tamper Protection (`RootDetector.kt`)
- Inspects device build tags for `test-keys`.
- Probes filesystem paths for known `su` binaries (`/system/bin/su`, `/system/xbin/su`, `/sbin/su`, `/system/app/Superuser.apk`).
- Executes `which su` process probes to identify hidden root management frameworks.

#### 2.6 Signing Configuration Decoupling
- Debug builds configure standard Android debug keystores.
- Release signing configurations strictly read credentials from environment variables:
  - `RELEASE_STORE_FILE`
  - `RELEASE_STORE_PASSWORD`
  - `RELEASE_KEY_ALIAS`
  - `RELEASE_KEY_PASSWORD`

---

### 3. ProGuard & R8 Configuration

`packages/common-android/consumer-rules.pro`:
```proguard
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class com.vaisentra.mobility.core.model.** { *; }
-keep class com.vaisentra.mobility.core.security.** { *; }
-keep class com.vaisentra.mobility.core.network.** { *; }
-keep class com.vaisentra.mobility.core.map.** { *; }
```
`apps/customer-android/proguard-rules.pro` & `apps/driver-android/proguard-rules.pro`:
- Preserve Retrofit interfaces and `@retrofit2.http.*` methods.
- Preserve Kotlin Coroutines `Continuation` and internal dispatchers.
- Keep Gson `@SerializedName` annotations and network DTO classes.
- Preserve Google Play Services Location and Mapbox SDK symbols.

---

### 4. Build & Unit Test Verification Evidence

Execution command:
```bash
./gradlew :packages:common-android:testDebugUnitTest :apps:customer-android:testDebugUnitTest :apps:driver-android:testDebugUnitTest
```

#### Test Results Summary

| Module | Test Suite Class | Tests Run | Failures | Errors | Skipped | Time |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: |
| `:packages:common-android` | `CommonSecurityUnitTest` | 4 | 0 | 0 | 0 | 1.33s |
| `:apps:customer-android` | `CustomerAppUnitTest` | 27 | 0 | 0 | 0 | 0.44s |
| `:apps:customer-android` | `CustomerNetworkAndResilienceTest`| 3 | 0 | 0 | 0 | 0.15s |
| `:apps:driver-android` | `DriverAppUnitTest` | 16 | 0 | 0 | 0 | 0.13s |
| `:apps:driver-android` | `DriverLocationTrackingTest` | 6 | 0 | 0 | 0 | 0.04s |
| `:apps:driver-android` | `DriverNetworkAndResilienceTest` | 2 | 0 | 0 | 0 | 0.01s |
| **TOTAL** | **6 Test Suites** | **58** | **0** | **0** | **0** | **2m 25s** |

**Build Result:** `BUILD SUCCESSFUL in 2m 25s (61 actionable tasks: 32 executed, 29 up-to-date)`

**Mobile Engineering Sign-Off:** VERIFIED & READY FOR RELEASE PACKAGING.

