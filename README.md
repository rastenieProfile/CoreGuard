🇬🇧 **English** | [🇨🇳 中文](README.zh.md) | [🇷🇺 Русский](README.md)

# CoreGuard

**CoreGuard** — an Android application for protecting corporate networks from untrusted applications on user devices. Uses the standard Android Work Profile for isolation, with trigger-based automation and a network Kill Switch. A feature-rich alternative to Shelter.

---

## Core Concept

The main problem: employees install applications from unverified sources on work devices. These apps can scan the corporate network, collect connection data, and transmit information to third parties.

**Solution:** place untrusted applications into an isolated Android Work Profile. Two independent spaces on one device:

```
┌─────────────────────────┐   ┌─────────────────────────┐
│   Main Profile          │   │   Work Profile           │
│   Corporate resources   │   │   Untrusted              │
│   Protected connection  │   │   applications           │
│   Work tools            │   │   ← isolated from network│
└─────────────────────────┘   └─────────────────────────┘
         ↑ different uid, different data, different network stack
```

Apps in different profiles exist in different Linux uid namespaces. They cannot see each other's data and network states. An app in the Work Profile **has no access** to corporate resources and connections of the main profile.

CoreGuard automates management: when a trigger fires, untrusted apps are frozen and completely cut off from the network.

---

## Protection: App Isolation in Work Profile

### Main rule: untrusted apps — only in the Work Profile

For protection to work, follow one rule: **install untrusted apps in the Work Profile** — via the Work Profile's Play Store. This creates kernel-level isolation.

Both profiles run **simultaneously and in parallel**. Work Profile apps cannot see the connections and data of the main profile.

### What an app sees from inside the Work Profile

The Work Profile is a separate Linux uid container. `ConnectivityManager` filters networks by userId — WP traffic is completely isolated from the main profile.

Key protection — **process suspension**: when a trigger fires, all untrusted apps are set to `setPackagesSuspended` — a dead process cannot check or transmit anything.

| What an app checks in WP | Result | Comment |
|---|:---:|---|
| `ConnectivityManager` — network type | ✅ isolated | Sees only its own network |
| `NetworkCapabilities` — transport | ✅ isolated | No data about main profile connections |
| DNS servers | ✅ isolated | Own DNS, not corporate |
| Network interfaces | ✅ when frozen | App is **suspended** when trigger fires |
| IP address | ✅ isolated | Direct IP, no access to corporate tunnel |
| `System.getProperty("http.proxyHost")` | ✅ | `null` |

### Triggers — Automatic Control

CoreGuard implements a **Zero Trust** approach: untrusted apps must not operate when the device has access to the corporate network. The combined trigger system reacts to network state changes:

- VPN, Wi-Fi, mobile network, operator — 4 trigger types
- Corporate Wi-Fi connection — automatic Kill Switch (maximum protection)
- Any trigger → immediate freeze of untrusted apps

---

## Why not just Shelter?

Shelter can create a Work Profile and clone apps into it. Freezing an app can only be done **manually**. No automation, no Kill Switch.

CoreGuard does the same, **plus**:

| Feature | Shelter | CoreGuard |
|---|:---:|:---:|
| Work Profile (app isolation) | ✅ | ✅ |
| Manual freeze/unfreeze | ✅ | ✅ |
| **Auto-freeze by triggers** | ❌ | ✅ |
| **Network Kill Switch (full network block)** | ❌ | ✅ |
| **Corporate Wi-Fi → automatic Kill Switch** | ❌ | ✅ |
| **Trigger: virtual/corporate network** | ❌ | ✅ |
| **Trigger: Wi-Fi SSID** | ❌ | ✅ |
| **Trigger: mobile internet** | ❌ | ✅ |
| **Trigger: mobile operator** | ❌ | ✅ |
| Clipboard sharing between profiles | ❌ | ✅ |
| Contact sharing between profiles | ❌ | ✅ |
| **App permission control (location, camera, mic…)** | ❌ | ✅ |
| **Per-app permission settings** | ❌ | ✅ |
| **Block app uninstall from Work Profile** | ❌ | ✅ |

---

## How It Works

### Work Profile — Two Independent Spaces

Work Profile is created via the standard Android system dialog. No ADB, factory reset, or root required. After creation, two completely isolated containers appear on the device:

- **Main Profile** — corporate resources, protected connections, work tools.
- **Work Profile** — separate uid space for untrusted apps.

Data, accounts, cache, SharedPreferences — everything is separated at kernel level.

### Triggers — Automatic Switching

The user configures trigger conditions. When any trigger fires:

- **0 ms** — Kill Switch: local TUN black hole on Work Profile drops all traffic → not a single byte from untrusted apps leaves
- **100 ms** — `setPackagesSuspended()`: all WP apps are frozen (gray icons, no CPU / RAM / network / push notifications)

When no trigger is active — everything restores automatically.

### Kill Switch — Local Firewall

Kill Switch is a local TUN interface with a `0.0.0.0/0` route that reads all packets and drops them. Not a single byte leaves the device. Works within the Work Profile, does not affect the main profile.

Kill Switch is **optional** — enabled separately in settings. Off by default.

---

## Triggers

| Trigger | Description | Default |
|---|---|:---:|
| 🔒 **Virtual Network** | Virtual/corporate network active on device | ✅ On |
| 📶 **Wi-Fi SSID** | Connected to a network from user's list | ❌ Off |
| 🏢 **Corporate Wi-Fi → Kill Switch** | Auto-enable Kill Switch on Wi-Fi match | ❌ Off |
| 📱 **Mobile Internet** | Any connection via mobile data | ✅ On |
| 📡 **Mobile Operator** | Connected to a specific operator | ❌ Off |

Logic is always **OR** — one triggered condition is enough.

### Network Policies

Different connection types can activate different protection levels:

- **Corporate Wi-Fi** (SSID from list + Kill Switch flag) → **maximum protection**: Kill Switch blocks all network, all marked apps are frozen
- **VPN / mobile internet** → **standard protection**: freeze marked apps, others work directly
- **Mobile operator** → **configurable protection**: react to a specific operator (e.g., corporate SIM)

---

## Settings

### Cross-Profile Sharing

| Parameter | Default |
|---|:---:|
| Clipboard (copy/paste) | ✅ On |
| Contacts | ❌ Off |
| Files / Share | ❌ Off |
| Install apps from unknown sources | ✅ On |

### Work Profile Privacy

| Parameter | Default |
|---|:---:|
| Hide app icon from Work Profile launcher | ✅ On |
| Block screenshots | ❌ Off |
| Block IMEI and phone number access | ❌ Off |
| Block location | ❌ Off |
| Block camera | ❌ Off |
| Block microphone | ❌ Off |
| Block contacts access | ❌ Off |
| Block SMS access | ❌ Off |
| Block call log access | ❌ Off |
| Block account enumeration | ❌ Off |
| Block Bluetooth scanning | ❌ Off |
| Block Bluetooth (general) | ❌ Off |
| Block NFC | ❌ Off |
| Block app uninstall | ❌ Off |
| Per-app permissions | — (separate screen) |

---

## Requirements

- Android 8.0+ (API 26)
- Device with Work Profile support (99% of devices)
- For Wi-Fi SSID trigger: location permission (required by Android)

**For development:** JDK 17, Android SDK (API 26+), ADB.

---

## Build

```powershell
# From project root
.\gradlew.bat assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

**Install on device (work profile):**
```powershell
adb install --user all -r app\build\outputs\apk\debug\app-debug.apk
```
> The `--user all` flag is mandatory — otherwise only the main profile (user 0) will be updated.

---

## Download

Ready APK: see the **Releases** section of this repository.

## License

MIT — see [LICENSE](LICENSE) file.

> *If you fork or redistribute this project — please keep the donation/support section. It's the only way to support development. Thank you!*
>
> *Если вы форкаете или распространяете этот проект — пожалуйста, сохраняйте раздел с донатами/поддержкой. Это единственный способ поддержать разработку. Спасибо!*
>
> *如果您 fork 或分发此项目，请保留捐赠/支持部分。这是支持开发的唯一方式。谢谢！*
