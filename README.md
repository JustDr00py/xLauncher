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

Release APK is about 2 MB. There's no network access, no accounts and no tracking.

## Build

```bash
# The system JDK on this machine is a runtime only; use the JDK 21 under ~/.jdks.
export JAVA_HOME=$HOME/.jdks/jdk-21.0.12.1+1
./gradlew :app:assembleRelease      # → app/build/outputs/apk/release/app-release.apk
```

Without a `keystore.properties` file, the release build is signed with the debug key. That's fine for sideloading, but keep using the same key or updates will need an uninstall first. To sign with your own key, add `keystore.properties` with `storeFile`, `storePassword`, `keyAlias` and `keyPassword`, the same format as xFIN.

## Install on the Onn

1. On the TV, go to **Settings → System → About** and press OK on *Android TV OS build* 7 times to unlock Developer options.
2. In **Developer options**, turn on *USB debugging* / *Wireless debugging*.
3. Run:

```bash
scripts/install.sh 192.168.0.100        # your stick's IP
```

The script installs the APK, sets xLauncher as the Home activity and disables the stock Google TV launcher (`launcherx`) plus `setupwraith`, the Onn fallback home screen. Without that last step, Google TV takes Home back.

**Wi-Fi safety:** Android erases the Wi-Fi networks an app saved when that app is disabled. On a freshly set-up Onn, your Wi-Fi was saved by the setup app (`setupwraith`), so the script skips any package that owns a saved network and tells you to re-join Wi-Fi from Settings first. Re-joining moves the saved network to the system Settings app.

**What you give up:** Google TV's own home screen (its recommendations, profiles and "For you" rows). Google Assistant, voice search, casting, the Play Store and every app keep working.

### Undo

```bash
scripts/revert.sh 192.168.0.100              # stock Home back, xLauncher kept
scripts/revert.sh 192.168.0.100 --uninstall  # …and remove xLauncher
```

A factory reset also restores everything.

## Speed tips

The first launch after installing runs before Android has compiled the app's startup profile. To compile it right away:

```bash
adb shell cmd package compile -m speed-profile -f io.github.justdr00py.xlauncher
```

On the Onn 4K Stick this took cold start from about 1.5 s to about 0.6 s.
