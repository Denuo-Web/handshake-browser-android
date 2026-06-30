# Android HNS-Native Browser Implementation Plan

> Use `docs/reference-links.md` as an implementation audit checklist before building each artifact. Favor SOLID, DRY, and KISS principles. Consider time complexity, efficient data structures, and appropriate algorithms during implementation.

## Project Goal

Build an Android HNS-native browser that resolves Handshake names locally, validates Handshake/DNSSEC/DANE locally, and supports modern HTTPS transports including QUIC/HTTP/3. The product should not depend on an external HNS resolver, remote proxy, hosted VPN, or hnsd process. It may use hnsd/hsd as reference implementations and test oracles, but the production resolver engine should be our own Rust core.

## Core Product Decision

Build the first-class product as an Android browser, not a system-wide VPN resolver.

## Product Reasoning

Owning the browser shell gives us control over URL interpretation, HNS resolution, DANE policy, transport policy, and error UX.

Use Android WebView as the rendering engine, with AndroidX `ProxyController` to route WebView traffic through our local gateway.

## Architecture

Use a clean layered architecture:

```text
Android UI / Browser Shell
  -> Kotlin app services
  -> WebView + ProxyController
  -> Local gateway on localhost
  -> Rust core via JNI/UniFFI
  -> HNS resolver, DNSSEC, DANE, transport, cache
  -> HNS peers, ICANN DNS, TCP TLS, QUIC/HTTP3
```

Keep the design modular:

- `hns-core`: consensus-neutral primitives, hashes, serialization
- `hns-chain`: headers, checkpoints, PoW, difficulty, reorgs
- `hns-p2p`: Handshake peer protocol, sync, peer scoring
- `hns-urkel`: Urkel proof verification
- `hns-resolver`: name resolution, root record extraction, DNS synthesis
- `hns-dnssec`: DNSSEC validation below HNS root
- `hns-dane`: TLSA policy and cert/key matching
- `hns-transport`: TCP TLS, QUIC, HTTP/1.1, HTTP/2, HTTP/3
- `hns-gateway`: local browser-facing proxy and origin fetch bridge
- `hns-cache`: persistent headers, proofs, records, TLS sessions
- `android-app`: Kotlin UI, service lifecycle, WebView, settings
- `android-ffi`: JNI/UniFFI bindings between Kotlin and Rust
- `test-fixtures`: protocol fixtures, HSD/HNSD comparison data

Apply KISS: each module has one job, avoids clever cross-layer behavior, and exposes small interfaces. Apply DRY: protocol encoding/decoding, validation rules, and cache TTL handling should live in shared libraries, not duplicated across gateway/resolver/tests. Apply SOLID: depend on traits/interfaces for peers, storage, clock, network, and resolver backends so tests can inject deterministic fakes.

## Persistent data

- Headers
- Checkpoints
- Chainwork/index metadata
- Peer database
- Name proof cache
- DNS resource cache
- TLSA/DANE cache
- QUIC/TLS session cache
- Diagnostics/events

## Do not persist

- Full blocks
- Full UTXO set
- Full name tree
- Wallet data
- Auction index
- Historical block serving data

## Security Model

### The app verifies

- Header chain work
- Checkpoint ancestry
- Proof-of-work difficulty
- Best-chain selection
- Urkel name proofs against committed tree roots
- DNSSEC chain below HNS delegation
- TLSA records under DNSSEC/DANE policy
- Origin certificate or SPKI against TLSA
- Transport downgrade resistance
- Cache TTL and expiry rules

### The app does not trust

- Single peers
- External HNS resolvers
- Unsigned DNS answers for HNS names
- TLSA answers without valid proof chain
- Server certificates that fail DANE policy
- Cached proofs past TTL or chain-validity window

### Failure policy

- Fail closed for HNS proof failure.
- Fail closed for DNSSEC validation failure.
- Fail closed when TLSA exists and DANE validation fails.
- Show a clear browser error page for validation failures.
- Allow explicit advanced override only if product policy accepts that risk.

## Phase 1: Repository And Tooling

Create a workspace with:

- `/rust`
- `/android`
- `/fixtures`
- `/docs`
- `/scripts`

### Rust setup

- cargo workspace
- cargo fmt
- cargo clippy
- cargo nextest
- cargo deny
- cargo fuzz or cargo-afl/honggfuzz
- criterion benchmarks
- proptest for protocol validation

### Android setup

- Kotlin
- Gradle version catalog
- AndroidX WebKit
- WebView browser shell
- JNI or UniFFI bridge
- Detekt/ktlint
- Android instrumented tests

### CI

- Rust unit tests
- Rust integration tests
- Rust fuzz smoke tests
- Kotlin unit tests
- Android build
- JNI binding generation
- Fixture consistency tests
- License/dependency audit

## Phase 2: Rust Core Primitives

### Implement

- Endian-safe serialization
- Handshake hashes
- Block header structure
- Header hash/mask hash logic
- Network constants
- Difficulty bits/target conversion
- DNS wire format primitives
- TLSA record parser
- Resource record parser

### Best practices

- Use strongly typed newtypes for Hash, NameHash, Height, Chainwork, Target, Timestamp.
- Avoid passing raw Vec<u8> across validation boundaries.
- Make parsing total and bounded: no unbounded allocations from attacker-controlled lengths.
- Every parser returns structured errors, not strings.

### Deliverables

- Header parse/serialize matches hsd fixtures.
- DNS/TLSA parser accepts valid fixtures and rejects malformed inputs.
- No panics on fuzzed inputs.

## Phase 3: Header Chain

### Implement

- Genesis constants
- Checkpoint loading
- Header storage
- Headers-first sync state machine
- Contextual header validation
- Difficulty adjustment
- Median time rules if required
- Best-chain selection by chainwork
- Reorg support
- Checkpoint anchoring

### Storage

- SQLite or RocksDB-compatible embedded store

### Column families/tables

- `headers_by_hash`
- `hash_by_height`
- `chain_state`
- `checkpoints`
- `peer_state`

### Design notes

- Keep storage abstract behind a trait.
- Use atomic batch writes for header ranges.
- Never mark a header best until validation and chainwork calculation complete.
- Maintain a compact skip index for ancestor lookup.

### Deliverables

- Can import headers from fixtures.
- Can sync headers from peers.
- Can survive restart and continue.
- Can handle synthetic reorg tests.
- Can compute same best tip as hsd for fixture ranges.

## Phase 4: HNS Peer Sync

### Implement

- Peer discovery from seeds/checkpoints/static peers
- Handshake network messages
- Version/verack lifecycle
- Getheaders/headers
- Name proof request messages
- Timeouts and backoff
- Peer scoring
- Peer eviction
- Eclipse-resistance basics

### Peer policy

- Minimum outbound peers: 4
- Preferred outbound peers: 8
- Do not trust one peer for availability
- Ban malformed protocol responses
- Downgrade score for stale tips
- Rotate peers periodically
- Persist successful peers

### Keep this simple

- One sync coordinator.
- One peer manager.
- One message codec.
- No business logic in socket handlers.

### Deliverables

- Initial sync from network.
- Restart resumes from stored tip.
- Bad peer fixtures are rejected.
- Peer diversity is observable in diagnostics.

## Phase 5: Urkel Proof Verification

### Implement

- Name hash derivation
- Proof parser
- Proof verifier
- Inclusion proof validation
- Non-inclusion proof validation
- Root comparison against header treeRoot
- Resource extraction from proven name state

### Design notes

- This is consensus/security-critical. Keep it small and heavily tested.
- Use hnsd, hsd, and urkel fixtures as test oracles.
- Fuzz the proof parser independently.
- Separate parsing from verification.

### Deliverables

- Valid proofs verify against known tree roots.
- Invalid/mutated proofs fail.
- Non-existence proofs work.
- Proof verification is deterministic and bounded.

## Phase 6: HNS Resolver

### Implement

- Resolve root Handshake names
- Return DNS-style responses
- Extract NS/DS/glue/resource records
- Support A, AAAA, NS, DS, TXT, TLSA, SVCB, HTTPS, CNAME as appropriate
- Negative caching
- TTL handling
- Cache invalidation on reorg

### Resolution path

```text
Input name
  -> normalize
  -> determine HNS vs ICANN/search
  -> fetch/prove HNS root state
  -> synthesize authoritative response
  -> recursively resolve delegated records if needed
  -> validate DNSSEC below HNS when delegation exists
  -> return structured answer
```

### Important

- The browser should not need global root-zone enumeration.
- Resolve only what the user visits or what page resources request.
- Cache proofs and DNS results with strict TTL and chain-root association.

### Deliverables

- Can resolve known HNS names.
- Can prove non-existent names.
- Can resolve TLSA for HNS services.
- Can fall back to ICANN DNS for normal domains.

## Phase 7: DNSSEC

### Implement

- DNSKEY
- DS
- RRSIG
- NSEC/NSEC3 if needed for delegated zones
- Canonical name handling
- Canonical RRset serialization
- Algorithm support required by real-world zones
- Clock and inception/expiration validation

### KISS rule

- DNSSEC code should validate RRsets and chains.
- It should not know about Android, WebView, or HTTP.
- HNS resolver supplies the trust anchor context.

### Deliverables

- DNSSEC chain fixtures pass.
- Expired signatures fail.
- Wrong DS/DNSKEY fails.
- Unsigned delegated records fail when security is expected.

## Phase 8: DANE

### Implement

- TLSA lookup naming: _443._tcp.name
- Usage 0, 1, 2, 3
- Selector 0 cert, selector 1 SPKI
- Matching 0 exact, 1 SHA256, 2 SHA512
- Policy engine
- Certificate chain extraction
- SPKI extraction
- DANE result reporting

### Default policy

- If TLSA exists and validates: accept according to DANE.
- If TLSA exists and does not validate: fail closed.
- If TLSA does not exist:
  - HNS domain default can require DANE or allow WebPKI based on product mode.
  - ICANN domain uses normal WebPKI.

### Recommended product policy

- Strict HNS mode: HNS HTTPS requires valid DANE.
- Compatibility HNS mode: allow WebPKI if no TLSA exists, but show security state clearly.

### Deliverables

- DANE-EE 3 1 1 works.
- Full usage/selector/matching matrix tested.
- Wrong cert/SPKI fails.
- Error page identifies DANE validation failure.

## Phase 9: Transport

Implement origin transports:

- TCP TLS
- HTTP/1.1
- HTTP/2
- QUIC
- HTTP/3
- HTTPS/SVCB record use
- Alt-Svc use where safe
- Connection pooling
- Timeouts
- Retries
- Session resumption

Current implementation covers bounded HTTP/1.1 pooling, rustls session resumption scoped by TLS/DANE policy and ALPN, and same-port Alt-Svc promotion to implemented HTTP/2/HTTP/3 transports.

### Important architecture

- WebView talks to local gateway.
- Local gateway is browser-facing.
- Rust transport is origin-facing.
- DANE validation happens before origin response is trusted.
- For QUIC, DANE validation must bind to the TLS authentication inside QUIC.

- Do not rely on WebView’s own QUIC to validate DANE. We own the origin connection in the gateway.

### Deliverables

- Fetch HNS HTTP over TCP.
- Fetch HNS HTTPS over TCP TLS.
- Fetch HNS HTTPS over HTTP/2.
- Fetch HNS HTTPS over HTTP/3/QUIC.
- Validate TLSA before returning secure content.

## Phase 10: Local Gateway

Implement a local proxy/gateway:

- localhost listener
- HTTP proxy support
- CONNECT support
- WebSocket support
- Request routing
- Response streaming
- Header preservation
- Cookie-safe behavior
- Compression handling
- Range requests
- Large download streaming

Current implementation routes HNS WebSocket/HTTP Upgrade through a native stream tunnel after HNS resolution, HTTPS/SVCB policy, and DANE validation. It still fails closed when tunnel validation fails or the native bridge is unavailable.

### For DANE HTTPS

```text
Browser/WebView request enters local proxy.
Gateway identifies target hostname.
Resolver proves HNS answer and TLSA.
Gateway connects to origin via TCP TLS or QUIC.
Gateway validates DANE.
Gateway returns response to WebView.
```

### Architecture experiments

Avoid unnecessary MITM if using an HTTP fetch bridge for WebView-controlled browser. For general WebView HTTPS through `CONNECT`, certificate bridging may be required. Make this explicit in architecture experiments.

- Path A: full local proxy with generated local CA
- Path B: app-owned fetch bridge returning WebResourceResponse
- Path C: custom WebView proxy plus gateway termination

### Choose the path that preserves

- POST bodies
- WebSockets
- Streaming
- HTTP/3 origin support
- DANE security
- Browser compatibility

### Likely outcome

- shouldInterceptRequest is insufficient alone because it is limited and awkward for POST/streaming.
- `ProxyController` plus local proxy is the better WebView integration.
- A local CA may still be required for transparent HTTPS proxying inside WebView.
- Since this is our app, configure Network Security Config/WebView trust intentionally for our local gateway.

### Deliverables

- WebView loads pages through gateway.
- POST forms work.
- WebSockets work.
- Large files stream without buffering whole body.
- HNS HTTPS page loads only after DANE validation.

Current Android proxy coverage includes HNS WebSocket/HTTP Upgrade native tunneling and bridge-unavailable fail-closed fallback.

## Phase 11: Android Browser Shell

### Implement

- Custom omnibox
- Tabs
- Back/forward/reload
- Downloads
- History
- Bookmarks
- Security indicator
- Settings
- Diagnostics page
- Certificate/DANE detail page
- Sync status page

### Omnibox rules

```text
https://name/ -> load exact
http://name/ -> load exact
name/ -> https://name/
singlelabel -> first try HNS resolution, else search
words with spaces -> search
icann.tld -> normal web
hns name with path -> preserve path/query/fragment
```

### Security UI

- HNS verified
- DANE verified
- WebPKI only
- Mixed policy
- Validation failed
- Sync stale
- Proof unavailable

### Avoid product confusion

- Do not present all HNS failures as generic network errors.
- Show whether failure is sync, proof, DNSSEC, DANE, transport, or origin.

### Deliverables

- User can type a bare HNS name.
- User can see resolver sync state.
- User can inspect DANE/TLSA result.
- Normal web browsing still works.

## Phase 12: Cache And Storage Policy

Implement cache layers:

- Header store: append-only, compact index
- Proof cache: key by namehash + treeRoot/height
- DNS cache: key by qname/qtype/class/security state
- TLSA cache: key by service name + chain context
- Transport cache: QUIC/TLS sessions
- HTTP cache: optional, later

### Eviction

- Headers retained by default.
- Proof cache LRU target: 50 MB.
- DNS/TLSA cache TTL-bound.
- Transport session cache bounded.
- Clear user data option.
- Clear resolver cache option.

### Reorg policy

- If reorg crosses cached proof height, invalidate affected proof/resource/TLSA cache.
- If reorg is shallow and proof still anchored to accepted tree root, keep only if chain remains ancestor-valid.

### Deliverables

- Cache remains under configured limit.
- Restart is fast.
- Reorg tests invalidate stale answers.

## Phase 13: Observability

### Expose diagnostics

- Header height
- Best peer height
- Sync progress
- Peer count
- Last proof height
- Cache size
- Recent validation failures
- Recent DANE decisions
- QUIC availability
- Gateway request log without sensitive bodies

### Logging

- Structured logs
- No private browsing content in default logs
- Redact query strings by default
- Debug mode opt-in
- Export diagnostic bundle

### Deliverables

- Support can diagnose sync/proof/DANE failures without packet captures.

## Phase 14: Security Review Checklist

### Review

- Parser bounds
- No panics on malformed network data
- No unbounded memory growth
- No trust in single peer
- No stale proof acceptance
- No TLSA downgrade
- No QUIC downgrade without policy event
- No DNS leak for HNS names
- No accidental external HNS resolver
- No local gateway exposed beyond localhost
- No private key leakage for local CA if used
- Clear local CA removal flow if used

### Threats

- Malicious HNS peer
- Eclipse attack
- Malformed Urkel proof
- Stale DNSSEC signatures
- TLSA stripping
- QUIC downgrade
- Local hostile app connecting to gateway
- Web content attacking localhost gateway
- Resource exhaustion
- Cache poisoning
- Clock manipulation

### Mitigations

- Peer diversity
- Checkpoint anchoring
- Proof verification
- DNSSEC time validation with sane clock warnings
- Strict DANE fail-closed
- Gateway origin checks
- Random localhost auth token if needed
- Rate limits
- Bounded parsers
- Fuzzing

## Phase 15: Testing Strategy

### Unit tests

- Serialization
- Header validation
- Difficulty
- Urkel proofs
- DNS parser
- DNSSEC validation
- TLSA validation
- DANE policy
- Cache expiry
- URL normalization

### Integration tests

- Header sync from fixture peer
- Resolve HNS fixture name
- Resolve non-existent HNS fixture name
- Resolve delegated DNSSEC zone
- Fetch DANE HTTPS over TCP
- Fetch DANE HTTPS over QUIC
- WebView loads HNS page through gateway
- POST/WebSocket/download flows

### Comparison tests

- Compare HNS answers against hnsd.
- Compare selected RPC/name states against hsd.
- Compare DNSSEC validation against known-good fixtures.
- Compare TLSA behavior against RFC examples and generated cert fixtures.

### Fuzz tests

- Peer message parser
- Header parser
- DNS parser
- TLSA parser
- Urkel proof parser
- HTTP proxy parser
- QUIC packet boundary handling if applicable

### Device tests

- Android 14, 15, 16, 17
- Low-memory phone
- Network changes Wi-Fi/cellular
- Airplane mode recovery
- Doze/background sync behavior
- WebView version variation

## Phase 16: Delivery Milestones

### Milestone 1: Rust proof kernel

- Header parsing
- Header validation
- Fixture import
- Urkel proof verification
- Name resource extraction

### Milestone 2: Live HNS sync

- Peer connection
- Header sync
- Proof requests
- Verified name lookup
- Persistent cache

### Milestone 3: DANE core

- DNSSEC validation
- TLSA validation
- Cert/SPKI matching
- Policy engine

### Milestone 4: Origin transport

- TCP TLS fetch
- HTTP/2 fetch
- QUIC/HTTP3 fetch
- DANE-bound transport validation

### Milestone 5: Android browser

- Kotlin shell
- WebView
- `ProxyController`
- Local gateway
- Omnibox HNS handling
- Security UI

### Milestone 6: Production hardening

- Cache caps
- Diagnostics
- Fuzzing
- Device matrix
- Battery/network optimization
- Security review

## Non-Goals For First Production Browser

### Do not implement

- Wallet
- Auction management
- Name registration
- Full node validation
- Full root-zone browser
- System-wide resolver
- VPN compatibility mode
- Desktop browser extensions
- Remote hosted resolver

## Definition Of Done

The first serious production release is done when:

- User installs Android app.
- User opens HNS browser.
- App syncs/verifies compact HNS header state.
- User types a bare HNS name.
- App resolves it locally without external HNS resolver.
- App verifies name proof against header tree root.
- App validates DNSSEC and DANE where applicable.
- App can fetch HTTPS over TCP and QUIC/HTTP3.
- App displays accurate security state.
- App fails closed on invalid proofs/TLSA/certs.
- Normal ICANN browsing works.
- Storage stays under target cap.
- The app survives restart, network changes, and stale peers.

## Plan Considerations

- Set `compileSdk = 37` and `targetSdk = 37` from day one.
- Keep `minSdk = 34` as we prefer less compatibility debt.
- Add CI checks for Android behavior, 16 KB native library alignment, WebView proxy feature support, and Play target API drift.

## Android 16 Items To Consider

- Predictive back: implement AndroidX back handling correctly; do not rely on legacy onBackPressed.
- Adaptive layouts: no locked phone-only assumptions; support tablets/foldables.
- Local network protection: keep the gateway loopback-only, bind to 127.0.0.1, and test future local-network permission behavior.
- Native build: use AGP 8.5.1+, preferably NDK r28+, and verify .so alignment.
- WebView proxy: gate `ProxyController` behind `WebViewFeature.PROXY_OVERRIDE` runtime checks.

## Android 17 Items to Consider

- Local Network Permission: Android 17 enforces `ACCESS_LOCAL_NETWORK` for apps targeting API 37. Our gateway should stay loopback-only by default, and only request local-network access if the user intentionally visits LAN/private IP targets.
- Large Screens: Android 17 ignores orientation/resizability opt-outs on large screens. The browser shell must be fully adaptive for tablets, foldables, desktop mode, and external displays.
- Native Loading: Do not dynamically load writable native libraries. Bundle Rust code as normal read-only JNI/UniFFI .so files and use `System.loadLibrary`.
- TLS Policy: Android 17 enables Certificate Transparency behavior for target 37 apps. For any WebView-direct HTTPS path, test CT/ECH behavior. For Rust gateway-managed HTTPS/DANE paths, implement and test certificate policy explicitly.
- Keystore Hygiene: Do not generate per-site Android Keystore keys. Use at most a small fixed number of app identity keys; keep DANE/cache data outside Keystore.
