#!/usr/bin/env bash
# Publishes a new RDM Eq version to GitHub Releases, where the in-app updater finds it.
#
#   scripts/release.sh 1.2.0 notes.md            stable release
#   scripts/release.sh 1.3.0-beta1 notes.md --beta   pre-release (only offered to "Include beta versions")
#   scripts/release.sh 1.2.0 notes.md --dry-run  build and check everything, publish nothing
#
# Steps: bump versionCode/versionName → unit tests → signed release build → verify package name,
# version and signing certificate → SHA-256 → rdm-eq-update.json → commit + tag + push → GitHub Release.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

VERSION_NAME="${1:-}"; NOTES="${2:-}"; shift $(( $# > 2 ? 2 : $# )) || true
BETA=false; DRY=false
for a in "$@"; do case "$a" in --beta) BETA=true ;; --dry-run) DRY=true ;; *) echo "Unknown option $a"; exit 1 ;; esac; done

die() { echo "✗ $*" >&2; exit 1; }
[[ -n "$VERSION_NAME" && -n "$NOTES" ]] || die "Usage: scripts/release.sh <versionName> <notes.md> [--beta] [--dry-run]"
[[ "$VERSION_NAME" =~ ^[0-9]+\.[0-9]+\.[0-9]+([-.][0-9A-Za-z.]+)?$ ]] || die "versionName must look like 1.2.0 or 1.3.0-beta1"
[[ -f "$NOTES" ]] || die "Release notes file not found: $NOTES"
[[ -f keystore.properties ]] || die "keystore.properties is missing: releases must be signed with the RDM Eq key"

SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
BT="$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)"
GH="${GH:-$(command -v gh || echo "$ROOT/../tools/gh")}"
TAG="v$VERSION_NAME"
APP_ID="com.rdm.eq"
EXPECTED_CERT="$(tr -d '[:space:]' < release-signing-cert.sha256)"

if ! $DRY; then
  "$GH" auth status >/dev/null 2>&1 || die "GitHub CLI is not signed in (run: $GH auth login)"
  [[ -z "$(git status --porcelain)" ]] || die "Commit or discard local changes first (git status)"
  git rev-parse "$TAG" >/dev/null 2>&1 && die "Tag $TAG already exists"
fi

# 1. Version bump
OLD_CODE=$(grep '^VERSION_CODE=' version.properties | cut -d= -f2)
NEW_CODE=$((OLD_CODE + 1))
echo "→ Version $VERSION_NAME (versionCode $OLD_CODE → $NEW_CODE)"
cp version.properties /tmp/rdm-version.bak
sed -i '' -e "s/^VERSION_CODE=.*/VERSION_CODE=$NEW_CODE/" -e "s/^VERSION_NAME=.*/VERSION_NAME=$VERSION_NAME/" version.properties
# Undo the bump if anything fails before the release commit (and always on --dry-run).
COMMITTED=0
restore() { [[ $COMMITTED == 1 ]] || cp /tmp/rdm-version.bak version.properties; }
trap restore EXIT

# 2. Tests + signed build
./gradlew --console=plain -q :app:testDebugUnitTest :app:assembleRelease
APK_SRC=app/build/outputs/apk/release/app-release.apk
[[ -f "$APK_SRC" ]] || die "Release APK was not produced"

# 3. Validate
"$BT/apksigner" verify "$APK_SRC" || die "APK signature is invalid"
CERT=$("$BT/apksigner" verify --print-certs "$APK_SRC" | awk '/Signer #1 certificate SHA-256 digest/ {print $NF}')
[[ "$CERT" == "$EXPECTED_CERT" ]] || die "APK is signed with $CERT, expected $EXPECTED_CERT (different key = users can't update)"
BADGING_ALL=$("$BT/aapt2" dump badging "$APK_SRC")
BADGING="${BADGING_ALL%%$'\n'*}"
[[ "$BADGING" == *"name='$APP_ID'"* ]] || die "Wrong application id: $BADGING"
[[ "$BADGING" == *"versionCode='$NEW_CODE'"* ]] || die "Wrong versionCode: $BADGING"
[[ "$BADGING" == *"versionName='$VERSION_NAME'"* ]] || die "Wrong versionName: $BADGING"
MIN_SDK=$(sed -n "s/^minSdkVersion:'\([0-9]*\)'/\1/p" <<< "$BADGING_ALL")

# 4. Package + checksum + metadata
OUT="dist/$TAG"; rm -rf "$OUT"; mkdir -p "$OUT"
APK_NAME="RDM-Eq-$VERSION_NAME.apk"
cp "$APK_SRC" "$OUT/$APK_NAME"
SHA=$(shasum -a 256 "$OUT/$APK_NAME" | awk '{print $1}')
SIZE=$(stat -f%z "$OUT/$APK_NAME")
(cd "$OUT" && shasum -a 256 "$APK_NAME" > SHA256SUMS.txt)
cat > "$OUT/rdm-eq-update.json" <<JSON
{
  "applicationId": "$APP_ID",
  "versionCode": $NEW_CODE,
  "versionName": "$VERSION_NAME",
  "apk": "$APK_NAME",
  "sha256": "$SHA",
  "size": $SIZE,
  "signingCertSha256": "$CERT",
  "minSdk": ${MIN_SDK:-28}
}
JSON
echo "✓ Built and verified $OUT/$APK_NAME"
echo "  sha256 $SHA · $SIZE bytes · cert $CERT"

if $DRY; then echo "Dry run: nothing committed or published."; exit 0; fi

# 5. Commit, tag, push, publish
git add version.properties
git commit -q -m "Release $TAG"
COMMITTED=1
git tag -a "$TAG" -m "RDM Eq $VERSION_NAME"
git push -q origin HEAD "$TAG"
FLAGS=(); $BETA && FLAGS+=(--prerelease) || FLAGS+=(--latest)
"$GH" release create "$TAG" "$OUT/$APK_NAME" "$OUT/rdm-eq-update.json" "$OUT/SHA256SUMS.txt" \
  --title "RDM Eq $VERSION_NAME" --notes-file "$NOTES" "${FLAGS[@]}"
trap - EXIT
echo "✓ Published $TAG — installed apps will see it on their next update check."
