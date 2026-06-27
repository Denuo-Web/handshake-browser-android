#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FUZZ_MANIFEST="$ROOT_DIR/rust/fuzz/Cargo.toml"

cargo fmt --manifest-path "$FUZZ_MANIFEST" --all -- --check
cargo check --manifest-path "$FUZZ_MANIFEST" --bins
