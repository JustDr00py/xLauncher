#!/usr/bin/env bash
# Restores the stock Google TV Home screen. xLauncher stays installed unless you pass --uninstall.
# Usage: scripts/revert.sh <device-ip[:port]> [--uninstall]
set -euo pipefail

DEVICE="${1:?usage: $0 <device-ip[:port]> [--uninstall]}"
[[ "$DEVICE" == *:* ]] || DEVICE="$DEVICE:5555"
STOCK=(com.google.android.apps.tv.launcherx com.google.android.tungsten.setupwraith)

adb connect "$DEVICE" >/dev/null
adb() { command adb -s "$DEVICE" "$@"; }

for p in "${STOCK[@]}"; do
  echo "› Enabling $p"
  adb shell pm enable "$p" >/dev/null 2>&1 || true
done
adb shell cmd package set-home-activity com.google.android.apps.tv.launcherx/.home.HomeActivity >/dev/null 2>&1 || true

if [[ "${2:-}" == "--uninstall" ]]; then
  echo "› Uninstalling xLauncher"
  adb uninstall io.github.justdr00py.xlauncher || true
fi

adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null
echo "✓ Stock Google TV Home restored."
