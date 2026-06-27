#![no_main]

use hns_core::dns::{DnsMessage, DnsName, SvcbRecord};
use libfuzzer_sys::fuzz_target;

fuzz_target!(|data: &[u8]| {
    let _ = DnsMessage::parse(data);
    let _ = DnsName::parse_wire(data, 0);
    let _ = SvcbRecord::parse_rdata(data);
});
