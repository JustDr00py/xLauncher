# xLauncher

A light, fast home screen for **Onn 4K Stick / Onn 4K Box & Pro** (Google TV, Android 12+), built around xFIN.

- **Featured card**: xFIN by default. OK opens it; hold OK for options.
- **Favorites row**: filled with your installed streaming apps on first run. Reorder it from the hold-OK menu.
- **Apps grid**: every installed app, alphabetical.
- **Settings** (the sliders icon, top right):
  - Featured app, plus an option to open it at startup (once per boot)
  - Hidden apps
  - Accent color (4), background (4, including blurred featured art), clock 12h/24h/hidden, app names on/off
  - Shortcuts to Android's own settings pages

Release APK is about 2 MB. No accounts, no analytics, no tracking. The only network traffic is the update check against this repo's GitHub Releases: at most once a day, plus whenever you press **Settings › About**.

## Build

```bash
# The system JDK on this machine is a runtime only; use the JDK 21 under ~/.jdks.
export JAVA_HOME=$HOME/.jdks/jdk-21.0.12.1+1
./gradlew :app:assembleRelease      # → app/build/outputs/apk/release/app-release.apk
```

Without a `keystore.properties` file, the release build is signed with the debug key. That's fine for sideloading, but keep using the same key or updates will need an uninstall first. To sign with your own key, add `keystore.properties` with `storeFile`, `storePassword`, `keyAlias` and `keyPassword`, the same format as xFIN.

## Install on the Onn

### New device checklist

1. **Finish Google TV setup** and connect to Wi-Fi.
2. **Re-join Wi-Fi from Settings:** go to **Settings → Network & Internet**, pick your network, *Forget*, then join it again and enter the password. This moves the saved network from the setup app to the system, so step 6 can disable the setup app without losing Wi-Fi (see *Wi-Fi safety* below).
3. **Unlock Developer options:** go to **Settings → System → About** and press OK on *Android TV OS build* 7 times.
4. **Turn on Wireless debugging** in **Settings → System → Developer options**, then open it. It shows the stick's `IP:port`. That port changes every time Wireless debugging is turned on, and Wireless debugging turns itself off after a reboot.
5. **Pair once per computer:** choose *Pair device with pairing code* on the TV, then run:

   ```bash
   adb pair 192.168.0.100:41234 123456     # pairing IP:port and code shown on the TV
   ```

6. **Get the APK and install it.** Either download `xLauncher-<version>.apk` from [Releases](https://github.com/JustDr00py/xLauncher/releases) or build it (see *Build*), then run the script with the `IP:port` from step 4:

   ```bash
   scripts/install.sh 192.168.0.100:37155 ~/Downloads/xLauncher-1.0.3.apk
   # or, after building locally:
   scripts/install.sh 192.168.0.100:37155
   ```

   If no port is given, the script uses 5555, which only works with classic network debugging.
7. **Speed up the first launch** (optional, see *Speed tips*):

   ```bash
   adb -s 192.168.0.100:37155 shell cmd package compile -m speed-profile -f io.github.justdr00py.xlauncher
   ```

8. **Install xFIN:**
   - Press Home and open xLauncher's settings (the gear). Under **About**, press OK on **xFIN (not installed)** to check GitHub.
   - Press OK again on **Install xFIN \<version\>**.
   - Android asks once to allow "Install unknown apps" for xLauncher.
9. **Optional:** in xLauncher's settings, turn on **Open featured app at startup** so xFIN opens after every reboot.

Wireless debugging can stay off afterwards. Updates for both apps come through xLauncher's settings.

### What install.sh does

The script installs the APK, sets xLauncher as the Home activity and disables the stock Google TV launcher (`launcherx`) plus `setupwraith`, the Onn fallback home screen. Without that last step, Google TV takes Home back.

**Wi-Fi safety:** Android erases the Wi-Fi networks an app saved when that app is disabled. On a freshly set-up Onn, your Wi-Fi was saved by the setup app (`setupwraith`), so the script skips any package that owns a saved network and tells you to re-join Wi-Fi from Settings first. Re-joining moves the saved network to the system Settings app.

**What you give up:** Google TV's own home screen (its recommendations, profiles and "For you" rows). Google Assistant, voice search, casting, the Play Store and every app keep working.

### Undo

```bash
scripts/revert.sh 192.168.0.100              # stock Home back, xLauncher kept
scripts/revert.sh 192.168.0.100 --uninstall  # …and remove xLauncher
```

A factory reset also restores everything.

## Updates

xLauncher updates itself from this repo's [Releases](https://github.com/JustDr00py/xLauncher/releases):

- **Settings › About** shows the version. Press it to check now.
- When a newer release exists, the settings button on Home gets a dot, and the update moves to the top of Settings.
- Pressing **Update** downloads the APK and opens Android's install prompt. The first time, Android asks you to allow "Install unknown apps" for xLauncher.
- Before installing, xLauncher checks that the APK is xLauncher, is newer than what's installed, and is signed with the same key. Anything else is refused.

### Publishing a release

```bash
scripts/release.sh 1.1.0 "What changed"
```

The script bumps the version, builds and checks the signature against `keystore.properties`, then commits, tags, pushes and creates the GitHub release with the APK attached.

**Back up `~/.android/xlauncher-release.jks` and `keystore.properties`.** Without them you can't publish updates that installed copies will accept.

## Speed tips

The first launch after installing runs before Android has compiled the app's startup profile. To compile it right away:

```bash
adb shell cmd package compile -m speed-profile -f io.github.justdr00py.xlauncher
```

On the Onn 4K Stick this took cold start from about 1.5 s to about 0.6 s.
