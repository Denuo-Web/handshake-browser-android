#![no_main]

use hns_core::network;
use hns_p2p::{FrameDecoder, Packet, decode_frame};
use libfuzzer_sys::fuzz_target;

fuzz_target!(|data: &[u8]| {
    let mainnet = network::mainnet();
    let _ = decode_frame(&mainnet, data);
    let mut decoder = FrameDecoder::new(mainnet);
    let _ = decoder.feed(data);

    if let Some((&packet_type, payload)) = data.split_first() {
        let _ = Packet::decode_payload(packet_type, payload);
    }
});
