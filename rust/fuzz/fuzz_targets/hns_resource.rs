#![no_main]

use hns_core::dns::DnsName;
use hns_core::resource::decode_handshake_resource_records;
use libfuzzer_sys::fuzz_target;

fuzz_target!(|data: &[u8]| {
    let owner = DnsName::from_ascii("fuzz").expect("static owner is valid");
    let _ = decode_handshake_resource_records(&owner, data);
});
