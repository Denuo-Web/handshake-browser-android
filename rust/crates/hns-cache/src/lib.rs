use std::collections::{HashMap, VecDeque};
use std::hash::Hash;
use std::time::{Duration, Instant};

#[derive(Clone, Debug)]
struct Entry<V> {
    value: V,
    expires_at: Instant,
}

#[derive(Debug)]
pub struct TtlCache<K, V> {
    max_entries: usize,
    entries: HashMap<K, Entry<V>>,
    order: VecDeque<K>,
}

impl<K, V> TtlCache<K, V>
where
    K: Clone + Eq + Hash,
    V: Clone,
{
    pub fn new(max_entries: usize) -> Self {
        Self {
            max_entries,
            entries: HashMap::new(),
            order: VecDeque::new(),
        }
    }

    pub fn insert(&mut self, key: K, value: V, ttl: Duration) {
        self.prune_expired();
        let expires_at = Instant::now() + ttl;
        if !self.entries.contains_key(&key) {
            self.order.push_back(key.clone());
        }
        self.entries.insert(key, Entry { value, expires_at });
        self.evict_over_limit();
    }

    pub fn get(&mut self, key: &K) -> Option<V> {
        self.prune_expired();
        self.entries.get(key).map(|entry| entry.value.clone())
    }

    pub fn len(&mut self) -> usize {
        self.prune_expired();
        self.entries.len()
    }

    pub fn is_empty(&mut self) -> bool {
        self.len() == 0
    }

    pub fn clear(&mut self) {
        self.entries.clear();
        self.order.clear();
    }

    fn prune_expired(&mut self) {
        let now = Instant::now();
        self.entries.retain(|_, entry| entry.expires_at > now);
        self.order.retain(|key| self.entries.contains_key(key));
    }

    fn evict_over_limit(&mut self) {
        while self.entries.len() > self.max_entries {
            if let Some(key) = self.order.pop_front() {
                self.entries.remove(&key);
            } else {
                break;
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn evicts_oldest_entry() {
        let mut cache = TtlCache::new(1);

        cache.insert("a", 1, Duration::from_secs(60));
        cache.insert("b", 2, Duration::from_secs(60));

        assert_eq!(cache.get(&"a"), None);
        assert_eq!(cache.get(&"b"), Some(2));
    }
}
