#!/usr/bin/env bash
# Installs xLauncher on an Onn 4K Stick / Box over ADB and makes it the Home screen.
# Usage: scripts/install.sh <device-ip[:port]> [path/to/apk]
# Undo everything with scripts/revert.sh.
set -euo pipefail

DEVICE="${1:?usage: $0 <device-ip[:port]> [apk]}"
[[ "$DEVICE" == *:* ]] || DEVICE="$DEVICE:5555"
APK="${2:-$(dirname "$0")/../app/build/outputs/apk/release/app-release.apk}"
PKG="io.github.justdr00py.xlauncher"
HOME_ACTIVITY="$PKG/.HomeActivity"
# Google TV's own launcher, and the setup app that acts as a fallback Home screen on Onn builds.
STOCK=(com.google.android.apps.tv.launcherx com.google.android.tungsten.setupwraith)

adb connect "$DEVICE" >/dev/null
adb() { command adb -s "$DEVICE" "$@"; }

echo "› Installing $(basename "$APK")"
adb install -r "$APK"

echo "› Setting xLauncher as the Home app"
adb shell cmd package set-home-activity "$HOME_ACTIVITY"

# Android deletes the Wi-Fi networks an app saved when that app is disabled. On a freshly set-up
# Onn, the home Wi-Fi was saved by the setup app (setupwraith), so disabling it blindly wipes the
# password and drops the connection. Skip any package that owns a saved network.
WIFI_DUMP="$(adb shell dumpsys wifi 2>/dev/null | tr -d '\r')"
SKIPPED=()
for p in "${STOCK[@]}"; do
  line="$(adb shell pm list packages -U 2>/dev/null | tr -d '\r' | grep "^package:$p uid:" || true)"
  [[ -n "$line" ]] || continue
  uid="${line##*uid:}"
  if grep -q " cuid=$uid " <<<"$WIFI_DUMP"; then
    echo "! Skipping $p: it owns a saved Wi-Fi network and disabling it would erase it."
    SKIPPED+=("$p")
    continue
  fi
  echo "› Disabling $p"
  adb shell pm disable-user --user 0 "$p" >/dev/null
done

if ((${#SKIPPED[@]})); then
  cat <<'MSG'

  To finish: on the TV open Settings › Network & Internet, choose your Wi-Fi network,
  pick "Forget", then join it again and type the password. That moves the
  saved network to the Settings app. Then run this script again.
MSG
fi

echo "› Opening Home"
adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null
echo "✓ Done. Press Home on the remote. To undo: scripts/revert.sh $DEVICE"
