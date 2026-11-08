# RideGo Device Inventory & Physical Hardware Audit

**Audit Date:** November 5, 2026  
**Auditor:** Production Readiness & Hardware Verification Team  
**Command:** `adb devices -l`

---

## 1. Connected Devices Status

```
List of devices attached
emulator-5554          device product:sdk_gphone64_x86_64 model:sdk_gphone64_x86_64 device:emu64x transport_id:1
emulator-5556          offline transport_id:2
```

### Physical Hardware Count: 0

- **Physical Android Phones Attached via USB**: 0
- **Physical Android Phones Attached via Wireless ADB**: 0
- **Android Virtual Devices (AVD / Emulators) Attached**: 1 active (`emulator-5554`), 1 offline (`emulator-5556`)

---

## 2. Active Virtual Device Specification (`emulator-5554`)

| Attribute | Specification |
|---|---|
| **Device Model** | `sdk_gphone64_x86_64` (Android Virtual Device) |
| **Android Version** | Android 17 / API 34+ Preview |
| **Architecture** | x86_64 |
| **Resolution / Density** | 1080 x 2400 @ 420 dpi |
| **Customer App Package** | `com.luxury.chauffeur.customer` (Installed & Verified) |
| **Driver App Package** | `com.luxury.chauffeur.driver` (Installed & Verified) |
| **Screen State** | Interactive Jetpack Compose UI Verified via `uiautomator dump` |
| **Mapbox Rendering** | Mapbox GL surface active |

---

## 3. Real Hardware Verification Constraint

Per the Production Go-Live Codex requirements:
> *"Do not treat emulator tests as physical device testing. If physical hardware devices are unavailable, document limitation honestly and mark real-device verification as BLOCKED."*

- **Physical Device Field Testing Status**: **BLOCKED**
- **Reason**: No physical Android smartphone or e-rickshaw mounting hardware is physically connected to the testing host.
- **Impact on Go-Live**: Real-world cellular handover (4G/5G), physical GPS drift/urban canyons, battery thermal throttling, and hardware audio alarms cannot be verified on physical silicon. Controlled staging validation is certified on emulator, but full public rollout is BLOCKED pending physical on-road pilot testing.

