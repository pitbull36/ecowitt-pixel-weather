#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

export PATH="$HOME/.local/bin:$PATH"
export JAVA_HOME="${JAVA_HOME:-$HOME/.local/jdk/jdk-21}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
APKSIGNER="${ANDROID_HOME}/build-tools/35.0.0/apksigner"

VERSION_CODE="${1:-5}"
WATCH_VERSION_CODE=$((3000 + VERSION_CODE))
VERSION_NAME="${VERSION_CODE}.0.0"
TAG_NAME="v${VERSION_NAME}"

sed -i "s/versionCode = [0-9]\+/versionCode = ${VERSION_CODE}/g" app/build.gradle.kts
sed -i "s/versionCode = [0-9]\+/versionCode = ${WATCH_VERSION_CODE}/g" wear/build.gradle.kts
sed -i "s/versionName = \"[^\"]\+\"/versionName = \"${VERSION_NAME}\"/g" app/build.gradle.kts wear/build.gradle.kts

cat > app/src/main/res/xml/wearable_app_desc.xml <<EOF
<?xml version="1.0" encoding="utf-8"?>
<wearableApp package="com.dariopittera.ecowittpixel">
    <versionCode>${WATCH_VERSION_CODE}</versionCode>
    <versionName>${VERSION_NAME}</versionName>
    <rawPathResId>wearos_app</rawPathResId>
</wearableApp>
EOF

echo "==> Step 1: Building signed Wear OS Release APK (versionCode=${WATCH_VERSION_CODE}) via Gradle..."
./gradlew :wear:assembleRelease

WATCH_APK_SOURCE="wear/build/outputs/apk/release/wear-release.apk"
WATCH_APK_DEST="wear/build/outputs/apk/release/wearos-watch-v${VERSION_NAME}.apk"

# Force v1 (JAR META-INF) + v2 + v3 signatures with --min-sdk-version 21 so Wear Installer 2,
# Bugjaeger, and Wear OS PackageInstaller all validate the watch APK without "package is not valid".
"$APKSIGNER" sign \
  --min-sdk-version 21 \
  --ks release-keystore.jks \
  --ks-pass pass:pixelota123 \
  --ks-key-alias pixelota \
  --key-pass pass:pixelota123 \
  --v1-signing-enabled true \
  --v2-signing-enabled true \
  --v3-signing-enabled true \
  "$WATCH_APK_SOURCE"

cp "$WATCH_APK_SOURCE" "$WATCH_APK_DEST"
echo "==> Built and v1+v2+v3 signed Wear OS APK: $WATCH_APK_DEST"

# Embed the v1+v2+v3 signed Wear OS APK inside the phone app's res/raw/wearos_app.apk so tools
# like Wear Installer 2 can also extract and install the watch APK directly from the phone app.
mkdir -p app/src/main/res/raw
cp "$WATCH_APK_SOURCE" app/src/main/res/raw/wearos_app.apk

echo "==> Step 2: Building signed Phone Release APK (versionCode=${VERSION_CODE}) via Gradle..."
rm -rf app/build/intermediates/desugar_graph app/build/intermediates/project_dex_archive
./gradlew :app:assembleRelease

# Phone APK starts with 'e' ('ecowitt-...') so it is ALWAYS assets[0] alphabetically on GitHub Releases,
# ensuring phones on older builds (v2.0.0) always download the phone APK and never the watch APK.
PHONE_APK_SOURCE="app/build/outputs/apk/release/app-release.apk"
PHONE_APK_DEST="app/build/outputs/apk/release/ecowitt-pixel-weather-v${VERSION_NAME}.apk"

"$APKSIGNER" sign \
  --min-sdk-version 21 \
  --ks release-keystore.jks \
  --ks-pass pass:pixelota123 \
  --ks-key-alias pixelota \
  --key-pass pass:pixelota123 \
  --v1-signing-enabled true \
  --v2-signing-enabled true \
  --v3-signing-enabled true \
  "$PHONE_APK_SOURCE"

cp "$PHONE_APK_SOURCE" "$PHONE_APK_DEST"
cp app/src/main/res/raw/wearos_app.apk "$WATCH_APK_DEST"
echo "==> Built and v1+v2+v3 signed Android Phone APK: $PHONE_APK_DEST"

if ! command -v gh >/dev/null 2>&1 || ! gh auth status >/dev/null 2>&1; then
  echo "WARNING: GitHub CLI not authenticated; APKs are ready locally:"
  echo "  Phone APK: $PHONE_APK_DEST"
  echo "  Watch APK: $WATCH_APK_DEST"
  exit 0
fi

GH_USER=$(gh api user -q .login)
GITHUB_REPO="${GH_USER}/ecowitt-pixel-weather"

if [ ! -d .git ]; then
  git init -b main
fi

git add .
if ! git diff --cached --quiet; then
  git commit -m "Release ${TAG_NAME}: Force v1+v2+v3 APK signatures and monotonic watch versionCode"
fi

if ! git remote get-url origin >/dev/null 2>&1; then
  if gh repo view "$GITHUB_REPO" >/dev/null 2>&1; then
    git remote add origin "https://github.com/${GITHUB_REPO}.git"
  else
    echo "==> Creating GitHub repository ${GITHUB_REPO}..."
    gh repo create "$GITHUB_REPO" --public --source=. --remote=origin
  fi
fi

git push -u origin HEAD

echo "==> Publishing GitHub Release ${TAG_NAME} with Phone and Wear OS APK assets..."
if gh release view "$TAG_NAME" --repo "$GITHUB_REPO" >/dev/null 2>&1; then
  gh release upload "$TAG_NAME" "$PHONE_APK_DEST" "$WATCH_APK_DEST" --clobber --repo "$GITHUB_REPO"
else
  gh release create "$TAG_NAME" "$PHONE_APK_DEST" "$WATCH_APK_DEST" \
    --repo "$GITHUB_REPO" \
    --title "Release ${TAG_NAME} (Android and Wear OS)" \
    --notes "Signed Android and Wear OS APKs (versionCode=${VERSION_CODE}, watchVersionCode=${WATCH_VERSION_CODE}) for Android phones and any Wear OS smartwatch with v1/v2/v3 signing, monotonic watch versionCode=${WATCH_VERSION_CODE}, embedded Wear OS sub-APK for Wear Installer 2, isolated phone vs watch OTA updates, live GW1200 and Cloud telemetry sync, synced custom room names, bluish humidity Tile palette, and 24h/1w crown history scrubbing."
fi

RELEASE_URL="https://github.com/${GITHUB_REPO}/releases/tag/${TAG_NAME}"
PHONE_DOWNLOAD_URL="https://github.com/${GITHUB_REPO}/releases/download/${TAG_NAME}/ecowitt-pixel-weather-v${VERSION_NAME}.apk"
WATCH_DOWNLOAD_URL="https://github.com/${GITHUB_REPO}/releases/download/${TAG_NAME}/wearos-watch-v${VERSION_NAME}.apk"
echo ""
echo "============================================================"
echo "SUCCESS! Published ${TAG_NAME} to GitHub Releases!"
echo "Release page:          ${RELEASE_URL}"
echo "Phone APK:             ${PHONE_DOWNLOAD_URL}"
echo "Wear OS Watch APK:     ${WATCH_DOWNLOAD_URL}"
echo "============================================================"
