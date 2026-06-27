#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR/android"

APKW_GRADLE="$HOME/APK_Workbench/scripts/dev/apkw-gradle.sh"

if [[ -x "$APKW_GRADLE" ]]; then
  "$APKW_GRADLE" --project-dir "$ROOT_DIR/android" assembleDebug
elif [[ -x ./gradlew ]]; then
  ./gradlew assembleDebug
else
  gradle assembleDebug
fi
