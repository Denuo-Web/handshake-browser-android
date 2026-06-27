# Android HNS Browser Reference Index

Paste-ready research and implementation checklist for an Android WebView-based Handshake browser with HNS resolution, DNSSEC, DANE/TLSA, and optional local proxy/VPN modes.

> **Version note:** before coding, re-check every moving package/API version listed below.

## Baseline Rule

Before coding, re-check every “latest” package/API link. As of 2026-06-25, AndroidX WebKit stable is listed as 1.16.0, AGP stable docs show 9.2.0, Gradle stable shows 9.5.1, UniFFI docs show 0.31.1, Hickory DNS docs show 0.26.1, and cargo-nextest changelog shows 0.9.138. These are moving targets.

## Android Browser Shell

Use these for WebView, proxying, lifecycle, foreground behavior, and optional VPN mode.

- [AndroidX WebKit release notes](https://developer.android.com/jetpack/androidx/releases/webkit)
- [ProxyController](https://developer.android.com/reference/androidx/webkit/ProxyController)
- [ProxyConfig](https://developer.android.com/reference/androidx/webkit/ProxyConfig)
- [WebViewFeature](https://developer.android.com/reference/androidx/webkit/WebViewFeature)
- [Android WebView](https://developer.android.com/reference/android/webkit/WebView)
- [Android WebViewClient](https://developer.android.com/reference/android/webkit/WebViewClient)
- [shouldInterceptRequest](https://developer.android.com/reference/android/webkit/WebViewClient#shouldInterceptRequest(android.webkit.WebView,%20android.webkit.WebResourceRequest))
- [WebResourceRequest](https://developer.android.com/reference/android/webkit/WebResourceRequest)
- [WebResourceResponse](https://developer.android.com/reference/android/webkit/WebResourceResponse)
- [WebSettings](https://developer.android.com/reference/android/webkit/WebSettings)
- [CookieManager](https://developer.android.com/reference/android/webkit/CookieManager)
- [ServiceWorkerController](https://developer.android.com/reference/android/webkit/ServiceWorkerController)
- [Android Network Security Config](https://developer.android.com/privacy-and-security/security-config)
- [Android foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [Android 14 foreground service requirements](https://developer.android.com/about/versions/14/changes/fgs-types-required)
- [Optional compatibility mode, VpnService](https://developer.android.com/develop/connectivity/vpn)
- [VpnService.Builder](https://developer.android.com/reference/android/net/VpnService.Builder)
- [Chrome Root Store and local trust limitations](https://chromium.googlesource.com/chromium/src/+/main/net/data/ssl/chrome_root_store/faq.md)

## Android Build Stack

Use these for current Kotlin/Gradle/NDK setup.

- [Android Gradle Plugin release notes](https://developer.android.com/build/releases)
- [AGP 9.2.0 release notes](https://developer.android.com/build/releases/agp-9-2-0-release-notes)
- [Android Gradle Plugin API/DSL reference](https://developer.android.com/reference/tools/gradle-api)
- [Gradle releases](https://gradle.org/releases/)
- [Kotlin Gradle configuration](https://kotlinlang.org/docs/gradle-configure-project.html)
- [Android NDK docs](https://developer.android.com/ndk)
- [cargo-ndk](https://github.com/bbqsrc/cargo-ndk)
- [cargo-ndk crate](https://crates.io/crates/cargo-ndk)
- [UniFFI user guide](https://mozilla.github.io/uniffi-rs/)
- [UniFFI crate docs](https://docs.rs/uniffi)
- [JNI spec](https://docs.oracle.com/en/java/javase/21/docs/specs/jni/)

## Handshake Core References

Use these as the protocol source material and test oracle references.

- [Handshake protocol summary](https://hsd-dev.org/protocol/summary.html)
- [Handshake resource records](https://hsd-dev.org/guides/resource-records.html)
- [HSD config modes, SPV/prune size references](https://hsd-dev.org/guides/config.html)
- [HSD repository](https://github.com/handshake-org/hsd)
- [HNSD repository](https://github.com/handshake-org/hnsd)
- [HNSD architecture reference](https://github.com/handshake-org/hnsd#architecture)
- [Handshake whitepaper text](https://handshake.org/files/handshake.txt)
- [Urkel repository](https://github.com/handshake-org/urkel)
- [Liburkel reference](https://github.com/chjj/liburkel)
- [Handshake Merkle tree docs](https://github.com/handshake-org/handshake-org.github.io/blob/master/src/protocol/merkle.md)
- [HSD source, header structure reference](https://github.com/handshake-org/hsd/blob/master/lib/primitives/abstractblock.js)
- [HSD source, network constants](https://github.com/handshake-org/hsd/blob/master/lib/protocol/networks.js)
- [HSD source, consensus constants](https://github.com/handshake-org/hsd/blob/master/lib/protocol/consensus.js)
- [HSD source, resource encoding](https://github.com/handshake-org/hsd/blob/master/lib/dns/resource.js)
- [HSD source, name state](https://github.com/handshake-org/hsd/blob/master/lib/covenants/namestate.js)
- [HSD source, rules](https://github.com/handshake-org/hsd/blob/master/lib/covenants/rules.js)

## DNS Core RFCs

Required for DNS packet parsing, response synthesis, recursive behavior, cache behavior, and modern record handling.

- [RFC 1034, DNS concepts](https://www.rfc-editor.org/info/rfc1034)
- [RFC 1035, DNS implementation/specification](https://www.rfc-editor.org/info/rfc1035)
- [RFC 1123, host requirements](https://www.rfc-editor.org/info/rfc1123)
- [RFC 2181, DNS clarifications](https://www.rfc-editor.org/info/rfc2181)
- [RFC 2308, negative caching](https://www.rfc-editor.org/info/rfc2308)
- [RFC 2782, SRV records](https://www.rfc-editor.org/info/rfc2782)
- [RFC 3597, unknown DNS RR types](https://www.rfc-editor.org/info/rfc3597)
- [RFC 4343, DNS case insensitivity](https://www.rfc-editor.org/info/rfc4343)
- [RFC 4592, wildcards](https://www.rfc-editor.org/info/rfc4592)
- [RFC 6891, EDNS(0)](https://www.rfc-editor.org/info/rfc6891)
- [RFC 7766, DNS over TCP requirements](https://www.rfc-editor.org/info/rfc7766)
- [RFC 8499, DNS terminology](https://www.rfc-editor.org/info/rfc8499)
- [RFC 8914, Extended DNS Errors](https://www.rfc-editor.org/info/rfc8914)
- [RFC 9460, SVCB and HTTPS records](https://www.rfc-editor.org/info/rfc9460)

## DNSSEC RFCs

Required for validation below Handshake-delegated zones.

- [RFC 4033, DNSSEC intro/requirements](https://www.rfc-editor.org/info/rfc4033)
- [RFC 4034, DNSSEC resource records](https://www.rfc-editor.org/info/rfc4034)
- [RFC 4035, DNSSEC protocol modifications](https://www.rfc-editor.org/info/rfc4035)
- [RFC 4509, SHA-256 DS records](https://www.rfc-editor.org/info/rfc4509)
- [RFC 5155, NSEC3](https://www.rfc-editor.org/info/rfc5155)
- [RFC 5702, SHA-2 DNSSEC algorithms](https://www.rfc-editor.org/info/rfc5702)
- [RFC 6840, DNSSEC clarifications](https://www.rfc-editor.org/info/rfc6840)
- [RFC 8624, DNSSEC algorithm implementation requirements](https://www.rfc-editor.org/info/rfc8624)
- [RFC 9364, DNSSEC best current practice overview](https://www.rfc-editor.org/info/rfc9364)

## DANE / TLSA RFCs

Required for HTTPS trust decisions.

- [RFC 6698, TLSA base specification](https://www.rfc-editor.org/info/rfc6698)
- [RFC 7218, DANE acronyms](https://www.rfc-editor.org/info/rfc7218)
- [RFC 7671, DANE updates and operational guidance](https://www.rfc-editor.org/info/rfc7671)
- [RFC 7672, SMTP DANE guidance, useful operational reference](https://www.rfc-editor.org/info/rfc7672)
- [RFC 7673, DANE with SRV records](https://www.rfc-editor.org/info/rfc7673)
- [RFC 9102, TLS DNSSEC Chain Extension, optional research reference](https://www.rfc-editor.org/info/rfc9102)

## TLS / X.509 / PKI

Required for DANE validation, WebPKI fallback, local gateway trust, and QUIC TLS binding.

- [RFC 5280, X.509 PKIX certificate profile](https://www.rfc-editor.org/info/rfc5280)
- [RFC 6066, TLS extensions including SNI](https://www.rfc-editor.org/info/rfc6066)
- [RFC 6125, DNS-ID / service identity checks](https://www.rfc-editor.org/info/rfc6125)
- [RFC 6962, Certificate Transparency](https://www.rfc-editor.org/info/rfc6962)
- [RFC 7301, ALPN](https://www.rfc-editor.org/info/rfc7301)
- [RFC 8446, TLS 1.3](https://www.rfc-editor.org/info/rfc8446)
- [RFC 8448, TLS 1.3 examples](https://www.rfc-editor.org/info/rfc8448)
- [RFC 8879, TLS certificate compression](https://www.rfc-editor.org/info/rfc8879)
- [RFC 7250, raw public keys, optional DANE-adjacent reference](https://www.rfc-editor.org/info/rfc7250)

## QUIC / HTTP/3

Required if the browser gateway owns origin-side QUIC and HTTP/3.

- [RFC 8999, QUIC version-independent properties](https://www.rfc-editor.org/info/rfc8999)
- [RFC 9000, QUIC transport](https://www.rfc-editor.org/info/rfc9000)
- [RFC 9001, QUIC TLS mapping](https://www.rfc-editor.org/info/rfc9001)
- [RFC 9002, QUIC loss detection and congestion control](https://www.rfc-editor.org/info/rfc9002)
- [RFC 9114, HTTP/3](https://www.rfc-editor.org/info/rfc9114)
- [RFC 9204, QPACK](https://www.rfc-editor.org/info/rfc9204)
- [RFC 9218, extensible HTTP priorities](https://www.rfc-editor.org/info/rfc9218)
- [RFC 9221, QUIC DATAGRAM frames](https://www.rfc-editor.org/info/rfc9221)
- [RFC 9297, HTTP datagrams and capsules](https://www.rfc-editor.org/info/rfc9297)
- [RFC 9298, CONNECT-UDP](https://www.rfc-editor.org/info/rfc9298)
- [RFC 9368, QUIC v2](https://www.rfc-editor.org/info/rfc9368)

## HTTP / Proxy / WebSocket

Required for the local gateway between WebView and origin transports.

- [RFC 9110, HTTP semantics](https://www.rfc-editor.org/info/rfc9110)
- [RFC 9111, HTTP caching](https://www.rfc-editor.org/info/rfc9111)
- [RFC 9112, HTTP/1.1](https://www.rfc-editor.org/info/rfc9112)
- [RFC 9113, HTTP/2](https://www.rfc-editor.org/info/rfc9113)
- [RFC 9114, HTTP/3](https://www.rfc-editor.org/info/rfc9114)
- [RFC 6455, WebSocket](https://www.rfc-editor.org/info/rfc6455)
- [RFC 8441, WebSocket over HTTP/2](https://www.rfc-editor.org/info/rfc8441)
- [RFC 2818, HTTP over TLS](https://www.rfc-editor.org/info/rfc2818)
- [MDN HTTP reference, useful practical index](https://developer.mozilla.org/en-US/docs/Web/HTTP)

## Encrypted DNS / Resolver Transports

Optional, but relevant for ICANN fallback, private DNS, and future resolver modes.

- [RFC 7858, DNS over TLS](https://www.rfc-editor.org/info/rfc7858)
- [RFC 8484, DNS over HTTPS](https://www.rfc-editor.org/info/rfc8484)
- [RFC 9250, DNS over QUIC](https://www.rfc-editor.org/info/rfc9250)
- [RFC 9461, SVCB mapping for DNS servers](https://www.rfc-editor.org/info/rfc9461)
- [RFC 9462, Discovery of Designated Resolvers](https://www.rfc-editor.org/info/rfc9462)

## Rust Networking / Crypto Crates

These are candidates or reference APIs. Pin exact versions only after prototype validation.

- [rustls](https://docs.rs/rustls)
- [rustls::client::ClientConfig](https://docs.rs/rustls/latest/rustls/client/struct.ClientConfig.html)
- [rustls custom verifier APIs](https://docs.rs/rustls/latest/rustls/client/danger/)
- [rustls-platform-verifier](https://docs.rs/rustls-platform-verifier)
- [webpki-roots](https://docs.rs/webpki-roots)
- [rustls-pki-types](https://docs.rs/rustls-pki-types)
- [x509-parser](https://docs.rs/x509-parser)
- [rcgen, local certificate generation](https://docs.rs/rcgen)
- [quinn, QUIC transport](https://docs.rs/quinn)
- [Quinn repository](https://github.com/quinn-rs/quinn)
- [h3, HTTP/3 implementation](https://docs.rs/h3)
- [h3-quinn](https://docs.rs/h3-quinn)
- [hickory-proto, DNS wire/protocol library](https://docs.rs/hickory-proto)
- [Hickory DNS repository](https://github.com/hickory-dns/hickory-dns)
- [hickory-resolver](https://docs.rs/hickory-resolver)
- [hickory-client](https://docs.rs/hickory-client)
- [hickory-server](https://docs.rs/hickory-server)
- [tokio](https://docs.rs/tokio)
- [bytes](https://docs.rs/bytes)
- [tracing](https://docs.rs/tracing)
- [thiserror](https://docs.rs/thiserror)
- [serde](https://docs.rs/serde)
- [zeroize](https://docs.rs/zeroize)
- [secrecy](https://docs.rs/secrecy)
- [blake2](https://docs.rs/blake2)
- [sha2](https://docs.rs/sha2)
- [ripemd](https://docs.rs/ripemd)
- [secp256k1](https://docs.rs/secp256k1)

## Storage

Use one database abstraction in Rust. SQLite is easiest to inspect and operate; redb is a good pure-Rust KV alternative.

- [rusqlite](https://docs.rs/rusqlite)
- [rusqlite::Connection](https://docs.rs/rusqlite/latest/rusqlite/struct.Connection.html)
- [rusqlite::Transaction](https://docs.rs/rusqlite/latest/rusqlite/struct.Transaction.html)
- [SQLite WAL](https://sqlite.org/wal.html)
- [SQLite PRAGMA reference](https://sqlite.org/pragma.html)
- [redb](https://docs.rs/redb)
- [redb repository](https://github.com/cberner/redb)
- [Android Room, if Kotlin-side DB is needed](https://developer.android.com/training/data-storage/room)

## Testing / Fuzzing / Supply Chain

Use these from the first sprint because parsers and proofs are hostile-input surfaces.

- [Rust Fuzz Book](https://rust-fuzz.github.io/book/)
- [cargo-fuzz](https://github.com/rust-fuzz/cargo-fuzz)
- [libfuzzer-sys](https://docs.rs/libfuzzer-sys)
- [proptest](https://docs.rs/proptest)
- [arbitrary](https://docs.rs/arbitrary)
- [criterion](https://docs.rs/criterion)
- [cargo-nextest](https://nexte.st/)
- [cargo-nextest changelog](https://nexte.st/changelog/)
- [cargo-deny](https://github.com/embarkstudios/cargo-deny)
- [cargo-deny docs](https://docs.rs/cargo-deny)
- [RustSec / cargo-audit](https://rustsec.org/)
- [cargo-audit](https://github.com/rustsec/rustsec/tree/main/cargo-audit)
- [Clippy](https://doc.rust-lang.org/clippy/)
- [Miri](https://github.com/rust-lang/miri)
- [OWASP MASVS](https://mas.owasp.org/MASVS/)
- [OWASP MASTG](https://mas.owasp.org/MASTG/)
- [OWASP mobile network communication testing](https://mas.owasp.org/MASTG/0x04f-Testing-Network-Communication/)

## Minimum App Requirements

Recommended starting target:

### Android

- minSdk: 26 or 29
- targetSdk: latest stable Android target at implementation time
- primary architecture: arm64-v8a
- secondary architectures: x86_64 for emulator, armeabi-v7a only if needed

### Permissions

- INTERNET
- ACCESS_NETWORK_STATE
- POST_NOTIFICATIONS if foreground status notifications target Android 13+
- FOREGROUND_SERVICE only if long-running sync/gateway service is foregrounded
- FOREGROUND_SERVICE_DATA_SYNC or appropriate service type if Android 14+ requires it
- BIND_VPN_SERVICE only for optional later VpnService compatibility mode

### Core runtime

- Android System WebView available and current
- AndroidX WebKit `PROXY_OVERRIDE` feature available
- Localhost gateway bound only to loopback
- Rust shared library loaded via JNI/UniFFI
- Persistent storage budget: 500 MB target, 1 GB recommended free space
- RAM: 4 GB device minimum, 6 GB comfortable
- Network: outbound HNS P2P, DNS, TCP 443, UDP 443

## Implementation-Time Version Checklist

At project creation, verify current versions here:

### AndroidX WebKit

- <https://developer.android.com/jetpack/androidx/releases/webkit>

### Android Gradle Plugin

- <https://developer.android.com/build/releases>

### Gradle

- <https://gradle.org/releases/>

### Kotlin

- <https://kotlinlang.org/docs/releases.html>

### UniFFI

- <https://crates.io/crates/uniffi>
- <https://mozilla.github.io/uniffi-rs/>

### cargo-ndk

- <https://crates.io/crates/cargo-ndk>

### rustls

- <https://crates.io/crates/rustls>

### quinn

- <https://crates.io/crates/quinn>

### h3 / h3-quinn

- <https://crates.io/crates/h3>
- <https://crates.io/crates/h3-quinn>

### hickory-proto

- <https://crates.io/crates/hickory-proto>

### rusqlite / redb

- <https://crates.io/crates/rusqlite>
- <https://crates.io/crates/redb>

## Primary Engineering References To Keep Open

For day-to-day implementation, these are the most important tabs:

### HSD protocol summary

- <https://hsd-dev.org/protocol/summary.html>

### HNSD architecture

- <https://github.com/handshake-org/hnsd#architecture>

### Handshake resource records

- <https://hsd-dev.org/guides/resource-records.html>

### Urkel

- <https://github.com/handshake-org/urkel>

### AndroidX ProxyController

- <https://developer.android.com/reference/androidx/webkit/ProxyController>

### Android Network Security Config

- <https://developer.android.com/privacy-and-security/security-config>

### RFC 4033/4034/4035 DNSSEC

- <https://www.rfc-editor.org/info/rfc4033>
- <https://www.rfc-editor.org/info/rfc4034>
- <https://www.rfc-editor.org/info/rfc4035>

### RFC 6698/7671 DANE

- <https://www.rfc-editor.org/info/rfc6698>
- <https://www.rfc-editor.org/info/rfc7671>

### RFC 8446 TLS 1.3

- <https://www.rfc-editor.org/info/rfc8446>

### RFC 9000/9001/9002 QUIC

- <https://www.rfc-editor.org/info/rfc9000>
- <https://www.rfc-editor.org/info/rfc9001>
- <https://www.rfc-editor.org/info/rfc9002>

### RFC 9114 HTTP/3

- <https://www.rfc-editor.org/info/rfc9114>

### rustls

- <https://docs.rs/rustls>

### quinn

- <https://docs.rs/quinn>

### hickory-proto

- <https://docs.rs/hickory-proto>
