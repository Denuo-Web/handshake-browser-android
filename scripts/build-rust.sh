#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cargo build --manifest-path "$ROOT_DIR/rust/Cargo.toml" --workspace
