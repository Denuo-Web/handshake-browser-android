use hns_core::hash::Hash;
use hns_core::pow::{Chainwork, PowError, Target, target_for_work, verify_pow};
use hns_core::{BlockHeader, Height};
use rusqlite::{Connection, OptionalExtension, params};
use std::collections::HashMap;
use std::path::Path;
use thiserror::Error;

const MAINNET_POW_BITS: u32 = 0x1c00ffff;
const MAINNET_TARGET_SPACING: u64 = 10 * 60;
const MAINNET_BLOCKS_PER_DAY: u32 = 144;
const MAINNET_MIN_ACTUAL_TIMESPAN: u64 = 36 * MAINNET_TARGET_SPACING;
const MAINNET_MAX_ACTUAL_TIMESPAN: u64 = 576 * MAINNET_TARGET_SPACING;

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct StoredHeader {
    pub hash: Hash,
    pub header: BlockHeader,
    pub height: Height,
    pub chainwork: Chainwork,
}

pub trait HeaderStore {
    fn get_header(&self, hash: Hash) -> Option<StoredHeader>;
    fn put_header(&mut self, header: StoredHeader) -> Result<(), ChainError>;
    fn best_hash(&self) -> Option<Hash>;
    fn canonical_hash(&self, height: Height) -> Option<Hash>;
    fn promote_canonical_tip(&mut self, header: &StoredHeader) -> Result<(), ChainError>;
    fn replace_canonical_chain(&mut self, headers: &[StoredHeader]) -> Result<(), ChainError>;
}

#[derive(Default)]
pub struct MemoryHeaderStore {
    headers: HashMap<Hash, StoredHeader>,
    canonical: HashMap<u32, Hash>,
    best: Option<Hash>,
}

pub struct SqliteHeaderStore {
    connection: Connection,
}

pub struct HeaderChain<S> {
    store: S,
    difficulty_policy: DifficultyPolicy,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum DifficultyPolicy {
    Mainnet,
    Permissive,
}

#[derive(Debug, Error, Eq, PartialEq)]
pub enum ChainError {
    #[error("mainnet genesis header is invalid")]
    InvalidGenesisHeader,
    #[error("header parent is unknown")]
    UnknownParent,
    #[error("header already exists")]
    DuplicateHeader,
    #[error("best header is missing from store")]
    MissingBestHeader,
    #[error("header difficulty bits are invalid: got {actual:#010x}, expected {expected:#010x}")]
    InvalidDifficultyBits { actual: u32, expected: u32 },
    #[error("header difficulty window is invalid")]
    InvalidDifficultyWindow,
    #[error("header proof-of-work does not satisfy target")]
    InvalidProofOfWork,
    #[error("proof-of-work target error: {0}")]
    Pow(#[from] PowError),
    #[error("storage error: {0}")]
    Storage(String),
}

impl HeaderStore for MemoryHeaderStore {
    fn get_header(&self, hash: Hash) -> Option<StoredHeader> {
        self.headers.get(&hash).cloned()
    }

    fn put_header(&mut self, header: StoredHeader) -> Result<(), ChainError> {
        if self.headers.contains_key(&header.hash) {
            return Err(ChainError::DuplicateHeader);
        }

        self.headers.insert(header.hash, header);
        Ok(())
    }

    fn best_hash(&self) -> Option<Hash> {
        self.best
    }

    fn canonical_hash(&self, height: Height) -> Option<Hash> {
        self.canonical.get(&height.0).copied()
    }

    fn promote_canonical_tip(&mut self, header: &StoredHeader) -> Result<(), ChainError> {
        if !self.headers.contains_key(&header.hash) {
            return Err(ChainError::MissingBestHeader);
        }

        self.canonical.insert(header.height.0, header.hash);
        self.best = Some(header.hash);
        Ok(())
    }

    fn replace_canonical_chain(&mut self, headers: &[StoredHeader]) -> Result<(), ChainError> {
        let Some(tip) = headers.last() else {
            return Err(ChainError::MissingBestHeader);
        };
        if headers
            .iter()
            .any(|header| !self.headers.contains_key(&header.hash))
        {
            return Err(ChainError::MissingBestHeader);
        }

        self.canonical.clear();
        for header in headers {
            self.canonical.insert(header.height.0, header.hash);
        }
        self.best = Some(tip.hash);
        Ok(())
    }
}

impl MemoryHeaderStore {
    pub fn len(&self) -> usize {
        self.headers.len()
    }

    pub fn is_empty(&self) -> bool {
        self.headers.is_empty()
    }
}

impl SqliteHeaderStore {
    pub fn open(path: impl AsRef<Path>) -> Result<Self, ChainError> {
        let connection =
            Connection::open(path).map_err(|error| ChainError::Storage(error.to_string()))?;
        Self::from_connection(connection)
    }

    pub fn in_memory() -> Result<Self, ChainError> {
        let connection =
            Connection::open_in_memory().map_err(|error| ChainError::Storage(error.to_string()))?;
        Self::from_connection(connection)
    }

    pub fn from_connection(connection: Connection) -> Result<Self, ChainError> {
        let store = Self { connection };
        store.initialize()?;
        Ok(store)
    }

    fn initialize(&self) -> Result<(), ChainError> {
        self.connection
            .execute_batch(
                "
                PRAGMA journal_mode = WAL;
                PRAGMA synchronous = NORMAL;
                PRAGMA foreign_keys = ON;

                CREATE TABLE IF NOT EXISTS headers_by_hash (
                    hash BLOB PRIMARY KEY NOT NULL,
                    height INTEGER NOT NULL,
                    chainwork TEXT NOT NULL,
                    header BLOB NOT NULL
                );

                CREATE INDEX IF NOT EXISTS headers_by_height
                    ON headers_by_hash(height);

                CREATE TABLE IF NOT EXISTS hash_by_height (
                    height INTEGER PRIMARY KEY NOT NULL,
                    hash BLOB NOT NULL,
                    FOREIGN KEY(hash) REFERENCES headers_by_hash(hash)
                );

                CREATE TABLE IF NOT EXISTS chain_state (
                    key TEXT PRIMARY KEY NOT NULL,
                    value BLOB NOT NULL
                );
                ",
            )
            .map_err(|error| ChainError::Storage(error.to_string()))
    }

    pub fn flush(self) -> Result<(), ChainError> {
        self.connection
            .close()
            .map_err(|(_, error)| ChainError::Storage(error.to_string()))
    }
}

impl HeaderStore for SqliteHeaderStore {
    fn get_header(&self, hash: Hash) -> Option<StoredHeader> {
        self.connection
            .query_row(
                "SELECT height, chainwork, header FROM headers_by_hash WHERE hash = ?1",
                params![hash.as_bytes().as_slice()],
                |row| {
                    let height: u32 = row.get(0)?;
                    let chainwork_hex: String = row.get(1)?;
                    let header_bytes: Vec<u8> = row.get(2)?;
                    let header = BlockHeader::parse(&header_bytes).map_err(|error| {
                        rusqlite::Error::FromSqlConversionFailure(
                            2,
                            rusqlite::types::Type::Blob,
                            Box::new(error),
                        )
                    })?;
                    let chainwork = Chainwork::from_hex(&chainwork_hex).map_err(|error| {
                        rusqlite::Error::FromSqlConversionFailure(
                            1,
                            rusqlite::types::Type::Text,
                            Box::new(error),
                        )
                    })?;

                    Ok(StoredHeader {
                        hash,
                        header,
                        height: Height(height),
                        chainwork,
                    })
                },
            )
            .optional()
            .ok()
            .flatten()
    }

    fn put_header(&mut self, header: StoredHeader) -> Result<(), ChainError> {
        let inserted = self
            .connection
            .execute(
                "
                INSERT OR IGNORE INTO headers_by_hash(hash, height, chainwork, header)
                VALUES (?1, ?2, ?3, ?4)
                ",
                params![
                    header.hash.as_bytes().as_slice(),
                    header.height.0,
                    header.chainwork.to_hex(),
                    header.header.serialize().as_slice(),
                ],
            )
            .map_err(|error| ChainError::Storage(error.to_string()))?;

        if inserted == 0 {
            return Err(ChainError::DuplicateHeader);
        }

        Ok(())
    }

    fn best_hash(&self) -> Option<Hash> {
        self.connection
            .query_row(
                "SELECT value FROM chain_state WHERE key = 'best_hash'",
                [],
                |row| {
                    let bytes: Vec<u8> = row.get(0)?;
                    Hash::from_slice(&bytes).map_err(|error| {
                        rusqlite::Error::FromSqlConversionFailure(
                            0,
                            rusqlite::types::Type::Blob,
                            Box::new(error),
                        )
                    })
                },
            )
            .optional()
            .ok()
            .flatten()
    }

    fn canonical_hash(&self, height: Height) -> Option<Hash> {
        self.connection
            .query_row(
                "SELECT hash FROM hash_by_height WHERE height = ?1",
                params![height.0],
                |row| {
                    let bytes: Vec<u8> = row.get(0)?;
                    Hash::from_slice(&bytes).map_err(|error| {
                        rusqlite::Error::FromSqlConversionFailure(
                            0,
                            rusqlite::types::Type::Blob,
                            Box::new(error),
                        )
                    })
                },
            )
            .optional()
            .ok()
            .flatten()
    }

    fn promote_canonical_tip(&mut self, header: &StoredHeader) -> Result<(), ChainError> {
        let transaction = self
            .connection
            .transaction()
            .map_err(|error| ChainError::Storage(error.to_string()))?;

        transaction
            .execute(
                "
                INSERT INTO hash_by_height(height, hash)
                VALUES (?1, ?2)
                ON CONFLICT(height) DO UPDATE SET hash = excluded.hash
                ",
                params![header.height.0, header.hash.as_bytes().as_slice()],
            )
            .map_err(|error| ChainError::Storage(error.to_string()))?;
        transaction
            .execute(
                "
                INSERT INTO chain_state(key, value)
                VALUES ('best_hash', ?1)
                ON CONFLICT(key) DO UPDATE SET value = excluded.value
                ",
                params![header.hash.as_bytes().as_slice()],
            )
            .map_err(|error| ChainError::Storage(error.to_string()))?;
        transaction
            .commit()
            .map_err(|error| ChainError::Storage(error.to_string()))?;

        Ok(())
    }

    fn replace_canonical_chain(&mut self, headers: &[StoredHeader]) -> Result<(), ChainError> {
        let Some(tip) = headers.last() else {
            return Err(ChainError::MissingBestHeader);
        };
        let transaction = self
            .connection
            .transaction()
            .map_err(|error| ChainError::Storage(error.to_string()))?;

        transaction
            .execute("DELETE FROM hash_by_height", [])
            .map_err(|error| ChainError::Storage(error.to_string()))?;
        for header in headers {
            transaction
                .execute(
                    "
                    INSERT INTO hash_by_height(height, hash)
                    VALUES (?1, ?2)
                    ",
                    params![header.height.0, header.hash.as_bytes().as_slice()],
                )
                .map_err(|error| ChainError::Storage(error.to_string()))?;
        }
        transaction
            .execute(
                "
                INSERT INTO chain_state(key, value)
                VALUES ('best_hash', ?1)
                ON CONFLICT(key) DO UPDATE SET value = excluded.value
                ",
                params![tip.hash.as_bytes().as_slice()],
            )
            .map_err(|error| ChainError::Storage(error.to_string()))?;
        transaction
            .commit()
            .map_err(|error| ChainError::Storage(error.to_string()))?;

        Ok(())
    }
}

impl<S: HeaderStore> HeaderChain<S> {
    pub fn new(store: S) -> Self {
        Self::with_difficulty_policy(store, DifficultyPolicy::Mainnet)
    }

    pub fn with_difficulty_policy(store: S, difficulty_policy: DifficultyPolicy) -> Self {
        Self {
            store,
            difficulty_policy,
        }
    }

    pub fn insert_genesis(&mut self, header: BlockHeader) -> Result<StoredHeader, ChainError> {
        self.validate_genesis(&header)?;
        let hash = header.hash();
        let stored = StoredHeader {
            hash,
            chainwork: Chainwork::from_bits(header.bits)?,
            header,
            height: Height(0),
        };

        self.store.put_header(stored.clone())?;
        self.promote_best_hash(hash)?;
        Ok(stored)
    }

    pub fn insert_header(&mut self, header: BlockHeader) -> Result<StoredHeader, ChainError> {
        let parent = self
            .store
            .get_header(header.prev_block)
            .ok_or(ChainError::UnknownParent)?;
        let hash = header.hash();
        self.validate_difficulty_bits(&header, &parent)?;
        if !verify_pow(hash, header.bits)? {
            return Err(ChainError::InvalidProofOfWork);
        }
        let chainwork = parent
            .chainwork
            .checked_add(&Chainwork::from_bits(header.bits)?);
        let stored = StoredHeader {
            hash,
            header,
            height: Height(parent.height.0 + 1),
            chainwork,
        };

        self.store.put_header(stored.clone())?;

        let best = self.best_header()?;
        let extends_best = best
            .as_ref()
            .is_some_and(|best| stored.header.prev_block == best.hash);
        let should_promote = match best {
            Some(best) => stored.chainwork > best.chainwork,
            None => true,
        };

        if should_promote {
            if extends_best {
                self.store.promote_canonical_tip(&stored)?;
            } else {
                self.promote_best_hash(hash)?;
            }
        }

        Ok(stored)
    }

    pub fn best_header(&self) -> Result<Option<StoredHeader>, ChainError> {
        match self.store.best_hash() {
            Some(hash) => self
                .store
                .get_header(hash)
                .map(Some)
                .ok_or(ChainError::MissingBestHeader),
            None => Ok(None),
        }
    }

    pub fn get_header(&self, hash: Hash) -> Option<StoredHeader> {
        self.store.get_header(hash)
    }

    pub fn canonical_hash(&self, height: Height) -> Option<Hash> {
        self.store.canonical_hash(height)
    }

    pub fn canonical_header(&self, height: Height) -> Option<StoredHeader> {
        self.canonical_hash(height)
            .and_then(|hash| self.store.get_header(hash))
    }

    pub fn into_store(self) -> S {
        self.store
    }

    fn promote_best_hash(&mut self, hash: Hash) -> Result<(), ChainError> {
        let headers = self.canonical_chain_to(hash)?;
        self.store.replace_canonical_chain(&headers)
    }

    fn validate_genesis(&self, header: &BlockHeader) -> Result<(), ChainError> {
        if self.difficulty_policy == DifficultyPolicy::Mainnet
            && header != &BlockHeader::mainnet_genesis()
        {
            return Err(ChainError::InvalidGenesisHeader);
        }

        Ok(())
    }

    fn validate_difficulty_bits(
        &self,
        header: &BlockHeader,
        parent: &StoredHeader,
    ) -> Result<(), ChainError> {
        let DifficultyPolicy::Mainnet = self.difficulty_policy else {
            return Ok(());
        };

        let expected = self.expected_mainnet_bits(parent)?;
        if header.bits != expected {
            return Err(ChainError::InvalidDifficultyBits {
                actual: header.bits,
                expected,
            });
        }

        Ok(())
    }

    fn expected_mainnet_bits(&self, parent: &StoredHeader) -> Result<u32, ChainError> {
        if parent.height.0 < MAINNET_BLOCKS_PER_DAY + 2 {
            return Ok(MAINNET_POW_BITS);
        }

        let last = self.suitable_block(parent)?;
        let ancestor = self.ancestor(parent, Height(parent.height.0 - MAINNET_BLOCKS_PER_DAY))?;
        let first = self.suitable_block(&ancestor)?;

        self.retarget_bits(&first, &last)
    }

    fn retarget_bits(&self, first: &StoredHeader, last: &StoredHeader) -> Result<u32, ChainError> {
        if last.height.0 <= first.height.0 {
            return Err(ChainError::InvalidDifficultyWindow);
        }

        let mut actual_timespan = last.header.time.saturating_sub(first.header.time);
        actual_timespan =
            actual_timespan.clamp(MAINNET_MIN_ACTUAL_TIMESPAN, MAINNET_MAX_ACTUAL_TIMESPAN);

        let work = last
            .chainwork
            .checked_sub(&first.chainwork)
            .ok_or(ChainError::InvalidDifficultyWindow)?
            .mul_u64(MAINNET_TARGET_SPACING)
            .div_u64(actual_timespan)
            .ok_or(ChainError::InvalidDifficultyWindow)?;

        if work.is_zero() {
            return Ok(MAINNET_POW_BITS);
        }

        let target = target_for_work(&work)?;
        if target > Target::from_compact(MAINNET_POW_BITS)? {
            return Ok(MAINNET_POW_BITS);
        }

        Ok(target.to_compact())
    }

    fn suitable_block(&self, header: &StoredHeader) -> Result<StoredHeader, ChainError> {
        let z = header.clone();
        let y = self.previous(&z)?;
        let x = self.previous(&y)?;
        let mut blocks = [x, y, z];
        blocks.sort_by_key(|block| block.header.time);

        Ok(blocks[1].clone())
    }

    fn ancestor(&self, header: &StoredHeader, height: Height) -> Result<StoredHeader, ChainError> {
        if height.0 > header.height.0 {
            return Err(ChainError::InvalidDifficultyWindow);
        }

        let mut current = header.clone();
        while current.height.0 > height.0 {
            current = self.previous(&current)?;
        }

        Ok(current)
    }

    fn previous(&self, header: &StoredHeader) -> Result<StoredHeader, ChainError> {
        self.store
            .get_header(header.header.prev_block)
            .ok_or(ChainError::UnknownParent)
    }

    fn canonical_chain_to(&self, hash: Hash) -> Result<Vec<StoredHeader>, ChainError> {
        let mut current = self
            .store
            .get_header(hash)
            .ok_or(ChainError::MissingBestHeader)?;
        let mut headers = vec![current.clone()];

        while current.height.0 > 0 {
            current = self
                .store
                .get_header(current.header.prev_block)
                .ok_or(ChainError::UnknownParent)?;
            headers.push(current.clone());
        }

        headers.reverse();
        Ok(headers)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn stores_genesis_as_best_tip() {
        let mut chain = HeaderChain::new(MemoryHeaderStore::default());
        let genesis = chain
            .insert_genesis(BlockHeader::mainnet_genesis())
            .unwrap();

        assert_eq!(chain.best_header().unwrap().unwrap(), genesis);
    }

    #[test]
    fn rejects_unknown_parent() {
        let mut chain = HeaderChain::new(MemoryHeaderStore::default());
        let header = BlockHeader::mainnet_genesis();

        assert_eq!(
            chain.insert_header(header).unwrap_err(),
            ChainError::UnknownParent,
        );
    }

    #[test]
    fn sqlite_store_survives_reopen_from_connection() {
        let store = SqliteHeaderStore::in_memory().unwrap();
        let mut chain = HeaderChain::new(store);
        let genesis = chain
            .insert_genesis(BlockHeader::mainnet_genesis())
            .unwrap();

        assert_eq!(chain.best_header().unwrap().unwrap(), genesis);
        assert_eq!(chain.canonical_hash(Height(0)), Some(genesis.hash));
    }

    #[test]
    fn rejects_header_that_fails_pow() {
        let mut chain = permissive_chain(MemoryHeaderStore::default());
        let genesis = chain
            .insert_genesis(BlockHeader::mainnet_genesis())
            .unwrap();
        let mut child = BlockHeader::mainnet_genesis();
        child.prev_block = genesis.hash;
        child.bits = 0x01010000;

        assert_eq!(
            chain.insert_header(child).unwrap_err(),
            ChainError::InvalidProofOfWork,
        );
    }

    #[test]
    fn rejects_non_mainnet_genesis_by_default() {
        let mut chain = HeaderChain::new(MemoryHeaderStore::default());
        let mut genesis = BlockHeader::mainnet_genesis();
        genesis.time += 1;

        assert_eq!(
            chain.insert_genesis(genesis).unwrap_err(),
            ChainError::InvalidGenesisHeader,
        );
    }

    #[test]
    fn rejects_unexpected_mainnet_difficulty_bits() {
        let mut chain = HeaderChain::new(MemoryHeaderStore::default());
        let genesis = chain
            .insert_genesis(BlockHeader::mainnet_genesis())
            .unwrap();
        let child = low_difficulty_child(&genesis, 1);

        assert_eq!(
            chain.insert_header(child).unwrap_err(),
            ChainError::InvalidDifficultyBits {
                actual: 0x207f_ffff,
                expected: MAINNET_POW_BITS,
            },
        );
    }

    #[test]
    fn mainnet_retarget_matches_hsd_after_initial_window() {
        let chain = seeded_mainnet_chain_with_spacing(MAINNET_TARGET_SPACING / 4);
        let parent = chain.best_header().unwrap().unwrap();

        assert_eq!(parent.height, Height(MAINNET_BLOCKS_PER_DAY + 2));
        assert_eq!(chain.expected_mainnet_bits(&parent).unwrap(), 0x1b3fffc0);
    }

    #[test]
    fn canonical_height_index_tracks_reorg_to_more_work_branch() {
        let mut chain = permissive_chain(MemoryHeaderStore::default());
        let genesis = chain
            .insert_genesis(BlockHeader::mainnet_genesis())
            .unwrap();
        let a1 = chain
            .insert_header(low_difficulty_child(&genesis, 1))
            .unwrap();
        let a2 = chain.insert_header(low_difficulty_child(&a1, 2)).unwrap();
        let b1 = chain
            .insert_header(low_difficulty_child(&genesis, 3))
            .unwrap();
        let b2 = chain.insert_header(low_difficulty_child(&b1, 4)).unwrap();

        assert_eq!(chain.best_header().unwrap().unwrap(), a2);
        assert_eq!(chain.canonical_hash(Height(1)), Some(a1.hash));
        assert_eq!(chain.canonical_hash(Height(2)), Some(a2.hash));

        let b3 = chain.insert_header(low_difficulty_child(&b2, 5)).unwrap();

        assert_eq!(chain.best_header().unwrap().unwrap(), b3);
        assert_eq!(chain.canonical_hash(Height(0)), Some(genesis.hash));
        assert_eq!(chain.canonical_hash(Height(1)), Some(b1.hash));
        assert_eq!(chain.canonical_hash(Height(2)), Some(b2.hash));
        assert_eq!(chain.canonical_hash(Height(3)), Some(b3.hash));
        assert_eq!(chain.canonical_hash(Height(4)), None);
        assert_eq!(chain.canonical_header(Height(2)).unwrap(), b2);
    }

    #[test]
    fn sqlite_canonical_height_index_survives_reopen() {
        let path = temp_db_path("canonical-height");
        let genesis;
        let child;
        {
            let store = SqliteHeaderStore::open(&path).unwrap();
            let mut chain = permissive_chain(store);
            genesis = chain
                .insert_genesis(BlockHeader::mainnet_genesis())
                .unwrap();
            child = chain
                .insert_header(low_difficulty_child(&genesis, 9))
                .unwrap();
            chain.into_store().flush().unwrap();
        }

        {
            let store = SqliteHeaderStore::open(&path).unwrap();
            let chain = permissive_chain(store);

            assert_eq!(chain.best_header().unwrap().unwrap(), child);
            assert_eq!(chain.canonical_hash(Height(0)), Some(genesis.hash));
            assert_eq!(chain.canonical_hash(Height(1)), Some(child.hash));
            assert_eq!(chain.canonical_header(Height(1)).unwrap(), child);
            chain.into_store().flush().unwrap();
        }

        cleanup_db_path(&path);
    }

    #[test]
    fn best_chain_extension_promotes_only_new_tip() {
        let mut chain = permissive_chain(CountingHeaderStore::default());
        let genesis = chain
            .insert_genesis(BlockHeader::mainnet_genesis())
            .unwrap();
        let child = chain
            .insert_header(low_difficulty_child(&genesis, 11))
            .unwrap();
        let store = chain.into_store();

        assert_eq!(store.full_replacements, 1);
        assert_eq!(store.tip_promotions, 1);
        assert_eq!(store.inner.canonical_hash(Height(0)), Some(genesis.hash));
        assert_eq!(store.inner.canonical_hash(Height(1)), Some(child.hash));
        assert_eq!(store.inner.best_hash(), Some(child.hash));
    }

    #[derive(Default)]
    struct CountingHeaderStore {
        inner: MemoryHeaderStore,
        full_replacements: usize,
        tip_promotions: usize,
    }

    impl HeaderStore for CountingHeaderStore {
        fn get_header(&self, hash: Hash) -> Option<StoredHeader> {
            self.inner.get_header(hash)
        }

        fn put_header(&mut self, header: StoredHeader) -> Result<(), ChainError> {
            self.inner.put_header(header)
        }

        fn best_hash(&self) -> Option<Hash> {
            self.inner.best_hash()
        }

        fn canonical_hash(&self, height: Height) -> Option<Hash> {
            self.inner.canonical_hash(height)
        }

        fn promote_canonical_tip(&mut self, header: &StoredHeader) -> Result<(), ChainError> {
            self.tip_promotions += 1;
            self.inner.promote_canonical_tip(header)
        }

        fn replace_canonical_chain(&mut self, headers: &[StoredHeader]) -> Result<(), ChainError> {
            self.full_replacements += 1;
            self.inner.replace_canonical_chain(headers)
        }
    }

    fn permissive_chain<S: HeaderStore>(store: S) -> HeaderChain<S> {
        HeaderChain::with_difficulty_policy(store, DifficultyPolicy::Permissive)
    }

    fn seeded_mainnet_chain_with_spacing(spacing: u64) -> HeaderChain<MemoryHeaderStore> {
        let mut store = MemoryHeaderStore::default();
        let genesis_header = BlockHeader::mainnet_genesis();
        let mut previous = StoredHeader {
            hash: genesis_header.hash(),
            chainwork: Chainwork::from_bits(genesis_header.bits).unwrap(),
            header: genesis_header,
            height: Height(0),
        };
        store.put_header(previous.clone()).unwrap();
        store.promote_canonical_tip(&previous).unwrap();

        for height in 1..=MAINNET_BLOCKS_PER_DAY + 2 {
            let mut header = BlockHeader::mainnet_genesis();
            header.prev_block = previous.hash;
            header.time = previous.header.time + spacing;
            header.extra_nonce[..4].copy_from_slice(&height.to_le_bytes());
            let chainwork = previous
                .chainwork
                .checked_add(&Chainwork::from_bits(header.bits).unwrap());
            let stored = StoredHeader {
                hash: header.hash(),
                header,
                height: Height(height),
                chainwork,
            };
            store.put_header(stored.clone()).unwrap();
            store.promote_canonical_tip(&stored).unwrap();
            previous = stored;
        }

        HeaderChain::new(store)
    }

    fn low_difficulty_child(parent: &StoredHeader, salt: u8) -> BlockHeader {
        let mut child = BlockHeader::mainnet_genesis();
        child.prev_block = parent.hash;
        child.bits = 0x207f_ffff;
        child.time = parent.header.time + u64::from(salt) + 1;
        child.extra_nonce[0] = salt;

        for nonce in 0..100_000 {
            child.nonce = nonce;
            if verify_pow(child.hash(), child.bits).unwrap() {
                return child;
            }
        }

        panic!("could not find low-difficulty header nonce");
    }

    fn temp_db_path(label: &str) -> std::path::PathBuf {
        let now = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap()
            .as_nanos();
        std::env::temp_dir().join(format!(
            "hns-chain-{label}-{}-{now}.sqlite",
            std::process::id()
        ))
    }

    fn cleanup_db_path(path: &std::path::Path) {
        let _ = std::fs::remove_file(path);
        let _ = std::fs::remove_file(path.with_extension("sqlite-wal"));
        let _ = std::fs::remove_file(path.with_extension("sqlite-shm"));
    }
}
