#!/usr/bin/env bash
# Publishes a new xLauncher version to GitHub Releases, where the in-app updater finds it.
# Usage: scripts/release.sh 1.1.0 "What changed"
#
# It bumps versionName and versionCode in app/build.gradle.kts, builds a release APK signed with
# the key in keystore.properties, commits, tags v<version>, pushes, and creates the release with
# the APK attached. Needs: gh (logged in), a clean git tree, keystore.properties.
set -euo pipefail
cd "$(dirname "$0")/.."

VERSION="${1:?usage: $0 <version> [notes]}"
NOTES="${2:-xLauncher $VERSION}"
GRADLE_FILE=app/build.gradle.kts
export JAVA_HOME="${JAVA_HOME:-$HOME/.jdks/jdk-21.0.12.1+1}"
export PATH="$JAVA_HOME/bin:$PATH"

[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "Version must look like 1.2.3"; exit 1; }
[[ -f keystore.properties ]] || { echo "keystore.properties missing: releases must use the release key"; exit 1; }
[[ -z "$(git status --porcelain)" ]] || { echo "Commit or stash your changes first"; exit 1; }
git rev-parse "v$VERSION" >/dev/null 2>&1 && { echo "Tag v$VERSION already exists"; exit 1; }

CODE=$(grep -oP 'versionCode = \K[0-9]+' "$GRADLE_FILE")
NEXT=$((CODE + 1))
sed -i -E "s/versionCode = [0-9]+/versionCode = $NEXT/; s/versionName = \"[^\"]+\"/versionName = \"$VERSION\"/" "$GRADLE_FILE"
echo "› Version $VERSION (code $NEXT)"

./gradlew -q :app:assembleRelease
mkdir -p build
APK="build/xLauncher-$VERSION.apk"
cp app/build/outputs/apk/release/app-release.apk "$APK"

# Refuse to publish anything not signed with the release key: devices would reject it.
EXPECTED=$(keytool -list -keystore "$(grep -oP 'storeFile=\K.*' keystore.properties)" \
  -storepass "$(grep -oP 'storePassword=\K.*' keystore.properties)" -rfc 2>/dev/null \
  | openssl x509 -noout -fingerprint -sha256 | cut -d= -f2 | tr -d ':' | tr 'A-F' 'a-f')
ACTUAL=$("$(ls -d "${ANDROID_HOME:-$HOME/Android/Sdk}"/build-tools/* | sort -V | tail -1)/apksigner" \
  verify --print-certs "$APK" 2>/dev/null | grep -oP 'SHA-256 digest: \K[0-9a-f]+' | head -1)
[[ -n "$EXPECTED" && "$EXPECTED" == "$ACTUAL" ]] || { echo "APK isn't signed with the release key"; git checkout -- "$GRADLE_FILE"; exit 1; }

git commit -qam "Release $VERSION"
git tag "v$VERSION"
git push -q origin HEAD "v$VERSION"
gh release create "v$VERSION" "$APK" --title "xLauncher $VERSION" --notes "$NOTES"
echo "✓ Released $VERSION. Sticks pick it up within a day, or right away from Settings › About."
