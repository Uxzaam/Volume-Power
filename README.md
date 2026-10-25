# Volume Power Plus

Rewrite of Volume Power App v1.1 (same idea: a volume key does the Power button's job) with:

- Self-healing accessibility service (re-enabled automatically if Android turns it off)
- Optional device-admin lock
- Wireless-ADB `WRITE_SECURE_SETTINGS` setup screen
- Start on boot

Package: `com.volumepower.plus` (installs next to the original; both use the same key, so disable one).

## First-time setup 

1. Install, open the app, allow notifications.
2. Settings → Accessibility → Volume Power Plus → turn on.
   If it is greyed out ("restricted setting"): App info → ⋮ → *Allow restricted settings*.
3. Grant the special permission once (from PC, or Termux with `pkg install android-tools`):
   `adb pair localhost:<pairing-port>` then `adb connect localhost:<port>` then
   `adb shell pm grant com.volumepower.plus android.permission.WRITE_SECURE_SETTINGS`
4. In the app: disable battery optimisation, optionally make it device admin.

Note: if you switch the service off on purpose, turn off *Auto re-enable* or the main switch first.


## Alternative for step 3
Shizuku setup (recommended, no PC needed)

1. Install Shizuku (https://shizuku.rikka.app/download/).
2. Settings > Developer options > Wireless debugging: on (needs Wi-Fi).
3. Open Shizuku > "Start via Wireless debugging" and follow the pairing steps. After a reboot you may need to start it again.
4. Open Volume Power Plus > tap *Allow this app*, then *Grant all permissions*.
5. Lock the phone and press the trigger key: the screen turns on even if a music/video app is playing.
