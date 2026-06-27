#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${1:-$ROOT_DIR/android/app/build/generated/rustJniLibs}"
PROFILE="${HNS_RUST_ANDROID_PROFILE:-release}"

if ! command -v cargo-ndk >/dev/null 2>&1; then
  echo "ERROR: cargo-ndk is required. Install with: cargo install cargo-ndk --version 4.1.2 --locked" >&2
  exit 2
fi

if [[ -z "${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}" ]]; then
  echo "ERROR: ANDROID_NDK_HOME or ANDROID_NDK_ROOT must point to the Android NDK." >&2
  exit 2
fi

mkdir -p "$OUT_DIR"
OUT_DIR="$(cd "$OUT_DIR" && pwd)"

ARGS=(
  ndk
  -t arm64-v8a
  -t x86_64
  -P 34
  -o "$OUT_DIR"
  build
  -p android-ffi
)

if [[ "$PROFILE" == "release" ]]; then
  ARGS+=(--release)
fi

cd "$ROOT_DIR/rust"
cargo "${ARGS[@]}"

NDK_DIR="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
OBJCOPY="$NDK_DIR/toolchains/llvm/prebuilt/linux-arm64/bin/llvm-objcopy"
if [[ "$PROFILE" == "release" && -x "$OBJCOPY" ]]; then
  find "$OUT_DIR" -type f -name '*.so' -print0 |
    while IFS= read -r -d '' library; do
      "$OBJCOPY" --strip-unneeded "$library"
    done
fi
