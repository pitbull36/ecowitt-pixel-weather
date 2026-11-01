#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

export PATH="$HOME/.local/bin:$PATH"
export JAVA_HOME="${JAVA_HOME:-$HOME/.local/jdk/jdk-21}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"

VERSION_CODE="${1:-3}"
VERSION_NAME="${VERSION_CODE}.0.0"
TAG_NAME="v${VERSION_NAME}"

echo "==> Building signed Release APKs (Pixel 10 phone + Pixel Watch) via Gradle..."
./gradlew :app:assembleRelease :wear:assembleRelease

PHONE_APK_SOURCE="app/build/outputs/apk/release/app-release.apk"
PHONE_APK_DEST="app/build/outputs/apk/release/ecowitt-pixel-weather-v${VERSION_NAME}.apk"
cp "$PHONE_APK_SOURCE" "$PHONE_APK_DEST"
echo "==> Built signed Pixel 10 APK: $PHONE_APK_DEST"

WATCH_APK_SOURCE="wear/build/outputs/apk/release/wear-release.apk"
WATCH_APK_DEST="wear/build/outputs/apk/release/ecowitt-pixel-watch-v${VERSION_NAME}.apk"
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
    --notes "Signed Android and Wear OS APKs (versionCode=${VERSION_CODE}) for Google Pixel 10 and Pixel Watch with circular bezel temperature and humidity arcs, two-level rotary crown 3D room navigation, Always-On Display wireframe mode, Wear OS Tile, configurable Watch Face Complications, and Wearable Data Layer sync."
fi

RELEASE_URL="https://github.com/${GITHUB_REPO}/releases/tag/${TAG_NAME}"
PHONE_DOWNLOAD_URL="https://github.com/${GITHUB_REPO}/releases/download/${TAG_NAME}/ecowitt-pixel-weather-v${VERSION_NAME}.apk"
WATCH_DOWNLOAD_URL="https://github.com/${GITHUB_REPO}/releases/download/${TAG_NAME}/ecowitt-pixel-watch-v${VERSION_NAME}.apk"
echo ""
echo "============================================================"
echo "SUCCESS! Published ${TAG_NAME} to GitHub Releases!"
echo "Release page:          ${RELEASE_URL}"
echo "Pixel 10 Phone APK:    ${PHONE_DOWNLOAD_URL}"
echo "Pixel Watch Wear APK:  ${WATCH_DOWNLOAD_URL}"
echo "============================================================"
