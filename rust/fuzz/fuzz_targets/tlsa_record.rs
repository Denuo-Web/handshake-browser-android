#![no_main]

use hns_dane::{TlsaRecord, extract_spki_der};
use libfuzzer_sys::fuzz_target;

fuzz_target!(|data: &[u8]| {
    let _ = TlsaRecord::parse_rdata(data);
    let _ = extract_spki_der(data);
});
