#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cargo fmt --manifest-path "$ROOT_DIR/rust/Cargo.toml" --all -- --check
cargo clippy --manifest-path "$ROOT_DIR/rust/Cargo.toml" --workspace --all-targets -- -D warnings
cargo test --manifest-path "$ROOT_DIR/rust/Cargo.toml" --workspace
"$ROOT_DIR/scripts/fuzz-smoke.sh"
