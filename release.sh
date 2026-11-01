#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

export PATH="$HOME/.local/bin:$PATH"
export JAVA_HOME="${JAVA_HOME:-$HOME/.local/jdk/jdk-21}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"

VERSION_CODE="${1:-4}"
VERSION_NAME="${VERSION_CODE}.0.0"
TAG_NAME="v${VERSION_NAME}"

sed -i "s/versionCode = [0-9]\+/versionCode = ${VERSION_CODE}/g" app/build.gradle.kts wear/build.gradle.kts
sed -i "s/versionName = \"[^\"]\+\"/versionName = \"${VERSION_NAME}\"/g" app/build.gradle.kts wear/build.gradle.kts

echo "==> Building signed Release APKs (Pixel 10 phone + Pixel Watch) via Gradle..."
./gradlew :app:assembleRelease :wear:assembleRelease

# Phone APK starts with 'e' ('ecowitt-...') so it is ALWAYS assets[0] alphabetically on GitHub Releases,
# ensuring phones on older builds (v2.0.0) always download the phone APK and never the watch APK.
PHONE_APK_SOURCE="app/build/outputs/apk/release/app-release.apk"
PHONE_APK_DEST="app/build/outputs/apk/release/ecowitt-pixel-weather-v${VERSION_NAME}.apk"
cp "$PHONE_APK_SOURCE" "$PHONE_APK_DEST"
echo "==> Built signed Pixel 10 APK: $PHONE_APK_DEST"

# Watch APK starts with 'w' ('wearos-...') so it sorts after the phone APK and is targeted exclusively by watch OTAs.
WATCH_APK_SOURCE="wear/build/outputs/apk/release/wear-release.apk"
WATCH_APK_DEST="wear/build/outputs/apk/release/wearos-pixel-watch-v${VERSION_NAME}.apk"
cp "$WATCH_APK_SOURCE" "$WATCH_APK_DEST"
echo "==> Built signed Pixel Watch APK: $WATCH_APK_DEST"

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
  git commit -m "Release ${TAG_NAME}: Ecowitt atmosphere for Pixel 10 and Pixel Watch"
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

echo "==> Publishing GitHub Release ${TAG_NAME} with Phone and Watch APK assets..."
if gh release view "$TAG_NAME" --repo "$GITHUB_REPO" >/dev/null 2>&1; then
  gh release upload "$TAG_NAME" "$PHONE_APK_DEST" "$WATCH_APK_DEST" --clobber --repo "$GITHUB_REPO"
else
  gh release create "$TAG_NAME" "$PHONE_APK_DEST" "$WATCH_APK_DEST" \
    --repo "$GITHUB_REPO" \
    --title "Release ${TAG_NAME} (Pixel 10 and Pixel Watch)" \
    --notes "Signed Android and Wear OS APKs (versionCode=${VERSION_CODE}) for Google Pixel 10 and Pixel Watch with isolated phone vs watch OTA updates, live GW1200 and Cloud telemetry sync, synced custom room names, bluish humidity Tile palette, and 24h crown history scrubbing."
fi

RELEASE_URL="https://github.com/${GITHUB_REPO}/releases/tag/${TAG_NAME}"
PHONE_DOWNLOAD_URL="https://github.com/${GITHUB_REPO}/releases/download/${TAG_NAME}/ecowitt-pixel-weather-v${VERSION_NAME}.apk"
WATCH_DOWNLOAD_URL="https://github.com/${GITHUB_REPO}/releases/download/${TAG_NAME}/wearos-pixel-watch-v${VERSION_NAME}.apk"
echo ""
echo "============================================================"
echo "SUCCESS! Published ${TAG_NAME} to GitHub Releases!"
echo "Release page:          ${RELEASE_URL}"
echo "Pixel 10 Phone APK:    ${PHONE_DOWNLOAD_URL}"
echo "Pixel Watch Wear APK:  ${WATCH_DOWNLOAD_URL}"
echo "============================================================"
