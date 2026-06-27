use crate::hash::Hash;

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct Network {
    pub name: &'static str,
    pub magic: u32,
    pub port: u16,
    pub brontide_port: u16,
    pub dns_seeds: &'static [&'static str],
    pub pow_bits: u32,
    pub pow_limit_hex: &'static str,
    pub genesis_hash: Hash,
}

pub const MAINNET_DNS_SEEDS: &[&str] = &["hs-mainnet.bcoin.ninja", "seed.htools.work"];

pub fn mainnet() -> Network {
    Network {
        name: "main",
        magic: 1_533_997_779,
        port: 12_038,
        brontide_port: 44_806,
        dns_seeds: MAINNET_DNS_SEEDS,
        pow_bits: 0x1c00ffff,
        pow_limit_hex: "0000000000ffff00000000000000000000000000000000000000000000000000",
        genesis_hash: Hash::from_hex(
            "5b6ef2d3c1f3cdcadfd9a030ba1811efdd17740f14e166489760741d075992e0",
        )
        .expect("valid mainnet genesis hash"),
    }
}
