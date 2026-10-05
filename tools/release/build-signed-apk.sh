#!/usr/bin/env bash
set -euo pipefail

echo 'Phase A: repository gate starts'
node --test tools/release/*.test.mjs
bash ./gradlew check
echo 'Phase A: repository gate passed'

umask 077
key_dir="$(mktemp -d)"
trap 'rm -rf "$key_dir"' EXIT

export SECOND_PASS_RELEASE_STORE_FILE="$key_dir/SecondPassReader-release.p12"
test -n "${ANDROID_RELEASE_KEYSTORE_B64:-}" || {
  echo 'Release keystore secret is missing.' >&2
  exit 1
}
printf '%s' "$ANDROID_RELEASE_KEYSTORE_B64" | base64 --decode > "$SECOND_PASS_RELEASE_STORE_FILE"
unset ANDROID_RELEASE_KEYSTORE_B64
test -s "$SECOND_PASS_RELEASE_STORE_FILE" || {
  echo 'Decoded release keystore is empty.' >&2
  exit 1
}

echo 'Phase B: release assembly/signing starts'
bash ./gradlew assembleRelease
echo 'Phase B: release assembly/signing passed'
echo 'Phase C: package/signer/checksum verification starts'
node tools/release/package-release.mjs
echo 'Phase C: verified release artifacts staged'
