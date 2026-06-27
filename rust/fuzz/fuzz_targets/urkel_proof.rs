#![no_main]

use hns_urkel::{ParsedProof, UrkelProof};
use libfuzzer_sys::fuzz_target;

fuzz_target!(|data: &[u8]| {
    let _ = ParsedProof::parse(data);
    let _ = UrkelProof::decode(data);
});
