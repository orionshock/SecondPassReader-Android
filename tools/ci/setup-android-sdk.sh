#!/usr/bin/env bash
set -euo pipefail

android_cli_version="1.0.16406183"
android_cli_sha256="1e7f2da1bb678bdb3c78a20b421a276f6b9eb639f0b6a17d46fa02f01a0b67b4"
android_cli_dir="${RUNNER_TEMP:-${TMPDIR:-/tmp}}/android-cli-${android_cli_version}"
android_cli="$android_cli_dir/android"
android_sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/.android/sdk}}"

mkdir -p "$android_cli_dir" "$android_sdk"
curl -fsSL \
  "https://dl.google.com/android/cli/${android_cli_version}/linux_x86_64/android" \
  -o "$android_cli"
printf '%s  %s\n' "$android_cli_sha256" "$android_cli" | sha256sum --check
chmod +x "$android_cli"

"$android_cli" --no-metrics --sdk="$android_sdk" sdk install \
  platform-tools \
  platforms/android-37.0 \
  build-tools/36.0.0 \
  build-tools/37.0.0

printf 'ANDROID_HOME=%s\nANDROID_SDK_ROOT=%s\n' "$android_sdk" "$android_sdk" >> "$GITHUB_ENV"
printf '%s\n%s\n%s\n' \
  "$android_cli_dir" \
  "$android_sdk/platform-tools" \
  "$android_sdk/build-tools/37.0.0" >> "$GITHUB_PATH"
