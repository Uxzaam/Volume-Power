# Volume Power Plus

**Version:** 4.1  
**Package:** `com.volumepower.plus`

**Volume Power Plus** remaps your Volume Up or Volume Down button to act as the Power button—built for devices with a broken, worn-out, or unresponsive physical power key.

> **Note:** This package (`com.volumepower.plus`) it is never as replacement of power key Rather it is a temporary Solution.
And Made With Claude and Gemini 🥸
---

## Features

- **Self-Healing Accessibility Service:** Automatically re-enables the service if Android turns it off in the background.
- **Media-Aware Screen Wake:** Wakes the screen with the volume trigger key even while music or video apps are playing.
- **Optional Device Admin Lock:** Locks the screen using Android Device Administrator privileges.
- **Wireless ADB Setup Screen:** Built-in setup flow for granting `WRITE_SECURE_SETTINGS`.
- **Start on Boot:** Automatically starts when the phone restarts.

---

## First-Time Setup
Allow All Permission for better stability .

### 1. Basic Permissions
1. Install and open **Volume Power Plus**, then allow notifications when prompted.
2. Go to **Settings → Accessibility → Volume Power Plus** and turn it on.
   - *If the toggle is greyed out ("Restricted setting"):* Go to **Settings → Apps → Volume Power Plus**, tap the **⋮** menu in the top-right corner, and select **Allow restricted settings**. Then return to Accessibility and turn the service on.

### 2. Grant `WRITE_SECURE_SETTINGS` (One-Time Setup)
This permission allows the app to keep its Accessibility Service active automatically. Choose **Option A (Shizuku)** or **Option B (ADB)**.

#### Option A: Shizuku Setup (Recommended — No PC Needed)
1. Install [Shizuku](https://shizuku.rikka.app/download/).
2. Connect to Wi-Fi, then go to **Settings → Developer options → Wireless debugging** and turn it on.
3. Open **Shizuku**, tap **Start via Wireless debugging**, and follow the on-screen pairing steps. *(You may need to restart Shizuku after rebooting your phone.)*
4. Open **Volume Power Plus**, tap **Allow this app**, and tap **Grant all permissions**.

#### Option B: ADB Setup (via PC or Termux)
If you are using Termux on your phone, install `android-tools` first (`pkg install android-tools`). Pair with Wireless Debugging and run:

```bash
adb pair localhost:<pairing-port>
adb connect localhost:<port>
adb shell pm grant com.volumepower.plus android.permission.WRITE_SECURE_SETTINGS
