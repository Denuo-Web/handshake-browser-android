#!/usr/bin/env bash
set -euo pipefail

cat <<'EOF'
Review these moving version sources before dependency upgrades:
- AndroidX WebKit: https://developer.android.com/jetpack/androidx/releases/webkit
- Android Gradle Plugin: https://developer.android.com/build/releases
- Gradle: https://gradle.org/releases/
- Kotlin: https://kotlinlang.org/docs/releases.html
- UniFFI: https://crates.io/crates/uniffi
- cargo-ndk: https://crates.io/crates/cargo-ndk
- rustls: https://crates.io/crates/rustls
- ring: https://crates.io/crates/ring
- webpki-roots: https://crates.io/crates/webpki-roots
- rcgen: https://crates.io/crates/rcgen
- quinn: https://crates.io/crates/quinn
- h3: https://crates.io/crates/h3
- hickory-proto: https://crates.io/crates/hickory-proto
EOF
