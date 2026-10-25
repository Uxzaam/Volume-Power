# Volume Power Plus

**Use your Volume key as the Power button on Android.**
Fix a broken, worn-out, or unresponsive power key by waking and locking your phone with Volume Up or Volume Down.

[![Release](https://img.shields.io/github/v/release/Uxzaam/Volume-Power-Plus?label=release)](https://github.com/Uxzaam/Volume-Power-Plus/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/Uxzaam/Volume-Power-Plus/total)](https://github.com/Uxzaam/Volume-Power-Plus/releases)
![Platform](https://img.shields.io/badge/platform-Android-3DDC84)


### [⬇️ Download the latest APK](https://github.com/Uxzaam/Volume-Power-Plus/releases/latest)

**Package:** `com.volumepower.plus` · **Version:** 4.1

> **Note:** Volume Power Plus is a temporary workaround, not a permanent replacement for your power key. If your power button is broken, get it repaired when you can.

---

## Who is this for?

- Your **power button is broken, stuck, or not working**
- You want to **save wear** on a power key that still works
- You need to **wake or lock the screen** without touching the power button

---

## Features

- **Volume key as Power button:** Choose Volume Up or Volume Down to act as Power.
- **Self-healing Accessibility Service:** Automatically re-enables the service if Android turns it off in the background.
- **Media-aware screen wake:** Wakes the screen with the volume trigger key even while music or video apps are playing.
- **Optional Device Admin lock:** Locks the screen using Android Device Administrator privileges.
- **Wireless ADB setup screen:** Built-in setup flow for granting `WRITE_SECURE_SETTINGS`.
- **Start on boot:** Automatically starts when the phone restarts.

---

## Requirements

- Android phone (tested devices: _Poco M4 pro 4g , Pixel 6a_)
- Accessibility Service permission
- One-time `WRITE_SECURE_SETTINGS` grant via Shizuku or ADB (see below)

---

## Installation

1. Download the APK from the [latest release](https://github.com/Uxzaam/Volume-Power-Plus/releases/latest).
2. Open the APK and install it. You may need to allow installs from your browser or file manager.
3. Follow the first-time setup below.

---

## First-time setup

Allow all permissions for better stability.

### 1. Basic permissions

1. Open **Volume Power Plus** and allow notifications when prompted.
2. Go to **Settings → Accessibility → Volume Power Plus** and turn it on.

> **If the toggle is greyed out ("Restricted setting"):** Go to **Settings → Apps → Volume Power Plus**, tap the **⋮** menu in the top-right corner, and select **Allow restricted settings**. Then return to Accessibility and turn the service on.

### 2. Grant `WRITE_SECURE_SETTINGS` (one-time)

This permission lets the app keep its Accessibility Service active automatically. Choose **Option A (Shizuku)** or **Option B (ADB)**.

#### Option A: Shizuku (recommended, no PC needed)

1. Install [Shizuku](https://shizuku.rikka.app/download/).
2. Connect to Wi-Fi, then go to **Settings → Developer options → Wireless debugging** and turn it on.
3. Open **Shizuku**, tap **Start via Wireless debugging**, and follow the on-screen pairing steps. _(You may need to restart Shizuku after rebooting your phone.)_
4. Open **Volume Power Plus**, tap **Allow this app**, and tap **Grant all permissions**.

#### Option B: ADB (via PC or Termux)

If you use Termux on your phone, install `android-tools` first:

```
pkg install android-tools
```

Pair with Wireless Debugging, then run:

```
adb shell pm grant com.volumepower.plus android.permission.WRITE_SECURE_SETTINGS
```

---

## Permissions explained

| Permission | Why it is needed |
|---|---|
| Accessibility Service | Lets the app react to your Volume key press |
| `WRITE_SECURE_SETTINGS` | Lets the app turn its Accessibility Service back on if Android disables it |
| Device Administrator (optional) | Lets the app lock the screen |
| Notifications | Shows the service status notification |
| Start on boot | Starts the app after a restart |

---

## FAQ

**Can this fully replace my power button?**
No. It is a temporary workaround. Some actions, like holding the power key for the power menu, are not covered.

**The Accessibility toggle is greyed out. What do I do?**
Allow restricted settings for the app. See the setup steps above.

**The service stops working after I restart my phone.**
If you use Shizuku, start it again after rebooting, then open Volume Power Plus.

**Does it work while music or video is playing?**
Yes. Screen wake is media-aware.

---

## Build from source

This is a Kotlin Gradle project. Clone the repo and open it in Android Studio, or build from the command line:

```
git clone https://github.com/Uxzaam/Volume-Power-Plus.git
cd Volume-Power-Plus
./gradlew assembleDebug
```

---

## Contributing and feedback

Found a bug or have an idea? [Open an issue](https://github.com/Uxzaam/Volume-Power-Plus/issues) or message me on Telegram. If this app saved your phone, a ⭐ on the repo helps others find it.

---

## Credits

Created by **Gulam Azam Faridi🇧🇩**
Telegram: [t.me/GulamAzamFaridi](https://t.me/GulamAzamFaridi)

Made with Claude and Gemini.

---
