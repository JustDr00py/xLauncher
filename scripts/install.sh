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
# password and drops the connection.
#
# All or nothing: disabling launcherx while setupwraith stays enabled left an Onn stick in a
# reboot loop with a black screen. So if any stock package can't be disabled safely, none is.
WIFI_DUMP="$(adb shell dumpsys wifi 2>/dev/null | tr -d '\r')"
PACKAGES="$(adb shell pm list packages -U 2>/dev/null | tr -d '\r')"
BLOCKED=()
for p in "${STOCK[@]}"; do
  line="$(grep "^package:$p uid:" <<<"$PACKAGES" || true)"
  [[ -n "$line" ]] || continue
  uid="${line##*uid:}"
  grep -q " cuid=$uid " <<<"$WIFI_DUMP" && BLOCKED+=("$p")
done

if ((${#BLOCKED[@]})); then
  echo "! Not disabling the stock Home apps: ${BLOCKED[*]} owns your saved Wi-Fi network,"
  echo "  and disabling it would erase it. Google TV's launcher stays enabled for now."
  cat <<'MSG'

  To finish: on the TV open Settings › Network & Internet, choose your Wi-Fi network,
  pick "Forget network", then join it again and type the password. That moves the
  saved network to the Settings app. Then run this script again.
MSG
else
  for p in "${STOCK[@]}"; do
    grep -q "^package:$p uid:" <<<"$PACKAGES" || continue
    echo "› Disabling $p"
    adb shell pm disable-user --user 0 "$p" >/dev/null
  done
fi

echo "› Opening Home"
adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null
echo "✓ Done. Press Home on the remote. To undo: scripts/revert.sh $DEVICE"
