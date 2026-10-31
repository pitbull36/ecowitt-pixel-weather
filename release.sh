#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

export PATH="$HOME/.local/bin:$PATH"
export JAVA_HOME="${JAVA_HOME:-$HOME/.local/jdk/jdk-21}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"

VERSION_CODE="${1:-1}"
VERSION_NAME="${VERSION_CODE}.0.0"
TAG_NAME="v${VERSION_NAME}"

echo "==> Building signed Release APK via Gradle..."
./gradlew assembleRelease

APK_SOURCE="app/build/outputs/apk/release/app-release.apk"
APK_DEST="app/build/outputs/apk/release/ecowitt-pixel-weather-v${VERSION_NAME}.apk"
cp "$APK_SOURCE" "$APK_DEST"
echo "==> Built signed APK: $APK_DEST"

if ! command -v gh >/dev/null 2>&1 || ! gh auth status >/dev/null 2>&1; then
  echo "WARNING: GitHub CLI not authenticated; APK is ready locally at $APK_DEST"
  exit 0
fi

GH_USER=$(gh api user -q .login)
GITHUB_REPO="${GH_USER}/ecowitt-pixel-weather"

if [ ! -d .git ]; then
  git init -b main
fi

git add .
if ! git diff --cached --quiet; then
  git commit -m "Release ${TAG_NAME}: Ecowitt atmosphere for Pixel 10"
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

echo "==> Publishing GitHub Release ${TAG_NAME} with APK asset..."
if gh release view "$TAG_NAME" --repo "$GITHUB_REPO" >/dev/null 2>&1; then
  gh release upload "$TAG_NAME" "$APK_DEST" --clobber --repo "$GITHUB_REPO"
else
  gh release create "$TAG_NAME" "$APK_DEST" \
    --repo "$GITHUB_REPO" \
    --title "Release ${TAG_NAME} (Ecowitt atmosphere for Pixel 10)" \
    --notes "Signed Android APK for Google Pixel 10 and Ecowitt GW1200 weather station with 3D room dioramas, AGSL shaders, climate analytics, and Jetpack Glance desktop widget."
fi

RELEASE_URL="https://github.com/${GITHUB_REPO}/releases/tag/${TAG_NAME}"
DOWNLOAD_URL="https://github.com/${GITHUB_REPO}/releases/download/${TAG_NAME}/ecowitt-pixel-weather-v${VERSION_NAME}.apk"
echo ""
echo "============================================================"
echo "SUCCESS! Published ${TAG_NAME} to GitHub Releases!"
echo "Release page:   ${RELEASE_URL}"
echo "Direct APK URL: ${DOWNLOAD_URL}"
echo "============================================================"
