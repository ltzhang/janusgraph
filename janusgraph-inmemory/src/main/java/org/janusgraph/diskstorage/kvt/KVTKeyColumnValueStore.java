// Copyright 2024 JanusGraph Authors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.janusgraph.diskstorage.kvt;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import org.apache.commons.lang.NotImplementedException;
import org.janusgraph.diskstorage.BackendException;
import org.janusgraph.diskstorage.Entry;
import org.janusgraph.diskstorage.EntryList;
import org.janusgraph.diskstorage.StaticBuffer;
import org.janusgraph.diskstorage.keycolumnvalue.KeyColumnValueStore;
import org.janusgraph.diskstorage.keycolumnvalue.KeyIterator;
import org.janusgraph.diskstorage.keycolumnvalue.KeyRangeQuery;
import org.janusgraph.diskstorage.keycolumnvalue.KeySliceQuery;
import org.janusgraph.diskstorage.keycolumnvalue.KeySlicesIterator;
import org.janusgraph.diskstorage.keycolumnvalue.MultiSlicesQuery;
import org.janusgraph.diskstorage.keycolumnvalue.SliceQuery;
import org.janusgraph.diskstorage.keycolumnvalue.StoreTransaction;
import org.janusgraph.diskstorage.util.RecordIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * KVT-based implementation of KeyColumnValueStore.
 * 
 * This class bridges JanusGraph's key-column-value model to KVT's transactional
 * key-value store using JNI. The C++ layer handles the complexity of mapping
 * between the two models (either via composite keys or serialized columns).
 */
public class KVTKeyColumnValueStore implements KeyColumnValueStore {

    private static final Logger log = LoggerFactory.getLogger(KVTKeyColumnValueStore.class);

    private final String name;
    private final long storeId;
    private final ConcurrentHashMap<Long, Long> activeTransactions = new ConcurrentHashMap<>();
    private volatile boolean isOpen = true;

    // Native JNI methods
    private native long beginTransaction();
    private native boolean commitTransaction(long txId);
    private native boolean rollbackTransaction(long txId);
    private native Entry[] getSlice(long storeId, long txId, byte[] key, 
                                   byte[] columnStart, byte[] columnEnd, int limit);
    private native void mutate(long storeId, long txId, byte[] key, 
                              Entry[] additions, byte[][] deletions);
    private native byte[][] getKeys(long storeId, long txId, byte[] keyStart, byte[] keyEnd,
                                   byte[] columnStart, byte[] columnEnd, int limit);
    private native String getName(long storeId);
    private native void close(long storeId);

    public KVTKeyColumnValueStore(String name, long storeId) {
        this.name = Preconditions.checkNotNull(name);
        this.storeId = storeId;
        log.debug("Created KVT store: {} with ID: {}", name, storeId);
    }

    @Override
    public EntryList getSlice(KeySliceQuery query, StoreTransaction txh) throws BackendException {
        Preconditions.checkState(isOpen, "Store has been closed");
        Preconditions.checkNotNull(query);
        Preconditions.checkNotNull(txh);

        long txId = getOrCreateTransaction(txh);
        
        try {
            byte[] keyBytes = query.getKey().as(StaticBuffer.ARRAY_FACTORY);
            byte[] startBytes = query.getSliceQuery().getSliceStart().as(StaticBuffer.ARRAY_FACTORY);
            byte[] endBytes = query.getSliceQuery().getSliceEnd().as(StaticBuffer.ARRAY_FACTORY);
            int limit = query.getSliceQuery().hasLimit() ? query.getSliceQuery().getLimit() : -1;

            Entry[] entries = getSlice(storeId, txId, keyBytes, startBytes, endBytes, limit);
            
            if (entries == null || entries.length == 0) {
                return EntryList.EMPTY_LIST;
            }

            return EntryList.of(entries);
            
        } catch (Exception e) {
            log.error("Error in getSlice for store {}", name, e);
            throw new BackendException("Native KVT getSlice failed", e);
        }
    }

    @Override
    public Map<StaticBuffer, EntryList> getSlice(List<StaticBuffer> keys, SliceQuery query, 
                                                StoreTransaction txh) throws BackendException {
        Preconditions.checkState(isOpen, "Store has been closed");
        Preconditions.checkNotNull(keys);
        Preconditions.checkNotNull(query);
        Preconditions.checkNotNull(txh);

        Map<StaticBuffer, EntryList> result = Maps.newHashMapWithExpectedSize(keys.size());
        
        for (StaticBuffer key : keys) {
            KeySliceQuery keyQuery = new KeySliceQuery(key, query);
            result.put(key, getSlice(keyQuery, txh));
        }
        
        return result;
    }

    @Override
    public void mutate(StaticBuffer key, List<Entry> additions, List<StaticBuffer> deletions, 
                      StoreTransaction txh) throws BackendException {
        Preconditions.checkState(isOpen, "Store has been closed");
        Preconditions.checkNotNull(key);
        Preconditions.checkNotNull(txh);

        long txId = getOrCreateTransaction(txh);
        
        try {
            byte[] keyBytes = key.as(StaticBuffer.ARRAY_FACTORY);
            
            // Convert additions
            Entry[] addArray = null;
            if (additions != null && !additions.isEmpty()) {
                addArray = additions.toArray(new Entry[0]);
            }
            
            // Convert deletions
            byte[][] delArray = null;
            if (deletions != null && !deletions.isEmpty()) {
                delArray = new byte[deletions.size()][];
                for (int i = 0; i < deletions.size(); i++) {
                    delArray[i] = deletions.get(i).as(StaticBuffer.ARRAY_FACTORY);
                }
            }

            mutate(storeId, txId, keyBytes, addArray, delArray);
            
        } catch (Exception e) {
            log.error("Error in mutate for store {}", name, e);
            throw new BackendException("Native KVT mutate failed", e);
        }
    }

    @Override
    public void acquireLock(StaticBuffer key, StaticBuffer column, StaticBuffer expectedValue, 
                           StoreTransaction txh) throws BackendException {
        // KVT handles locking internally through transactions
        // No explicit locking API needed
        log.debug("Lock acquisition handled internally by KVT transactions");
    }

    @Override
    public KeyIterator getKeys(KeyRangeQuery query, StoreTransaction txh) throws BackendException {
        Preconditions.checkState(isOpen, "Store has been closed");
        Preconditions.checkNotNull(query);
        Preconditions.checkNotNull(txh);

        long txId = getOrCreateTransaction(txh);
        
        try {
            byte[] keyStart = query.getKeyStart().as(StaticBuffer.ARRAY_FACTORY);
            byte[] keyEnd = query.getKeyEnd().as(StaticBuffer.ARRAY_FACTORY);
            byte[] colStart = query.getSliceStart().as(StaticBuffer.ARRAY_FACTORY);
            byte[] colEnd = query.getSliceEnd().as(StaticBuffer.ARRAY_FACTORY);
            int limit = query.hasLimit() ? query.getLimit() : -1;

            byte[][] keys = getKeys(storeId, txId, keyStart, keyEnd, colStart, colEnd, limit);
            
            return new KVTKeyIterator(keys, query.getSliceQuery(), txh);
            
        } catch (Exception e) {
            log.error("Error in getKeys for store {}", name, e);
            throw new BackendException("Native KVT getKeys failed", e);
        }
    }

    @Override
    public KeyIterator getKeys(SliceQuery query, StoreTransaction txh) throws BackendException {
        Preconditions.checkState(isOpen, "Store has been closed");
        
        // For unordered scan, use empty key range to scan all keys
        byte[] emptyKey = new byte[0];
        byte[] maxKey = new byte[]{(byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF};
        
        long txId = getOrCreateTransaction(txh);
        
        try {
            byte[] colStart = query.getSliceStart().as(StaticBuffer.ARRAY_FACTORY);
            byte[] colEnd = query.getSliceEnd().as(StaticBuffer.ARRAY_FACTORY);
            int limit = query.hasLimit() ? query.getLimit() : -1;

            byte[][] keys = getKeys(storeId, txId, emptyKey, maxKey, colStart, colEnd, limit);
            
            return new KVTKeyIterator(keys, query, txh);
            
        } catch (Exception e) {
            log.error("Error in getKeys (unordered) for store {}", name, e);
            throw new BackendException("Native KVT getKeys failed", e);
        }
    }

    @Override
    public KeySlicesIterator getKeys(MultiSlicesQuery queries, StoreTransaction txh) throws BackendException {
        throw new NotImplementedException("MultiSlicesQuery not yet implemented for KVT");
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void close() throws BackendException {
        if (!isOpen) {
            return;
        }

        try {
            // Rollback any active transactions
            for (Long txId : activeTransactions.values()) {
                try {
                    rollbackTransaction(txId);
                } catch (Exception e) {
                    log.warn("Error rolling back transaction {}: {}", txId, e.getMessage());
                }
            }
            activeTransactions.clear();

            // Close native store
            close(storeId);
            
            isOpen = false;
            log.debug("Closed KVT store: {}", name);
            
        } catch (Exception e) {
            throw new BackendException("Error closing KVT store: " + name, e);
        }
    }

    /**
     * Get or create a native transaction for the given StoreTransaction
     */
    private long getOrCreateTransaction(StoreTransaction txh) throws BackendException {
        long txhId = System.identityHashCode(txh);
        
        return activeTransactions.computeIfAbsent(txhId, id -> {
            try {
                long nativeTxId = beginTransaction();
                if (nativeTxId == 0) {
                    throw new RuntimeException("Failed to begin native transaction");
                }
                
                // Register commit/rollback hooks
                txh.getConfiguration().getCustomOptions().put("kvt.store.instance", this);
                txh.getConfiguration().getCustomOptions().put("kvt.tx.id", nativeTxId);
                
                log.debug("Created native transaction {} for store {}", nativeTxId, name);
                return nativeTxId;
                
            } catch (Exception e) {
                log.error("Failed to create transaction for store {}", name, e);
                throw new RuntimeException("Transaction creation failed", e);
            }
        });
    }

    /**
     * Commit a native transaction
     */
    public boolean commitTransaction(StoreTransaction txh) {
        long txhId = System.identityHashCode(txh);
        Long nativeTxId = activeTransactions.remove(txhId);
        
        if (nativeTxId != null) {
            try {
                boolean success = commitTransaction(nativeTxId);
                log.debug("Committed native transaction {} for store {}: {}", nativeTxId, name, success);
                return success;
            } catch (Exception e) {
                log.error("Error committing transaction {} for store {}", nativeTxId, name, e);
                return false;
            }
        }
        
        return true; // No transaction to commit
    }

    /**
     * Rollback a native transaction
     */
    public boolean rollbackTransaction(StoreTransaction txh) {
        long txhId = System.identityHashCode(txh);
        Long nativeTxId = activeTransactions.remove(txhId);
        
        if (nativeTxId != null) {
            try {
                boolean success = rollbackTransaction(nativeTxId);
                log.debug("Rolled back native transaction {} for store {}: {}", nativeTxId, name, success);
                return success;
            } catch (Exception e) {
                log.error("Error rolling back transaction {} for store {}", nativeTxId, name, e);
                return false;
            }
        }
        
        return true; // No transaction to rollback
    }

    /**
     * Simple KeyIterator implementation for KVT
     */
    private class KVTKeyIterator implements KeyIterator {
        private final byte[][] keys;
        private final SliceQuery columnSlice;
        private final StoreTransaction transaction;
        private int currentIndex = 0;
        private boolean isClosed = false;

        public KVTKeyIterator(byte[][] keys, SliceQuery columnSlice, StoreTransaction transaction) {
            this.keys = keys != null ? keys : new byte[0][];
            this.columnSlice = columnSlice;
            this.transaction = transaction;
        }

        @Override
        public boolean hasNext() {
            return !isClosed && currentIndex < keys.length;
        }

        @Override
        public StaticBuffer next() {
            if (isClosed || currentIndex >= keys.length) {
                throw new IllegalStateException("No more keys available");
            }
            
            return StaticBuffer.of(keys[currentIndex++]);
        }

        @Override
        public RecordIterator<Entry> getEntries() {
            if (isClosed || currentIndex == 0) {
                throw new IllegalStateException("Must call next() before getEntries()");
            }
            
            // Get entries for the current key (previous key from next())
            StaticBuffer currentKey = StaticBuffer.of(keys[currentIndex - 1]);
            KeySliceQuery keyQuery = new KeySliceQuery(currentKey, columnSlice);
            
            try {
                EntryList entries = KVTKeyColumnValueStore.this.getSlice(keyQuery, transaction);
                return new SimpleRecordIterator<>(entries.iterator());
            } catch (BackendException e) {
                throw new RuntimeException("Error retrieving entries", e);
            }
        }

        @Override
        public void close() {
            isClosed = true;
        }
    }

    /**
     * Simple RecordIterator wrapper
     */
    private static class SimpleRecordIterator<T> implements RecordIterator<T> {
        private final Iterator<T> iterator;

        public SimpleRecordIterator(Iterator<T> iterator) {
            this.iterator = iterator;
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public T next() {
            return iterator.next();
        }

        @Override
        public void close() {
            // Nothing to close
        }
    }
}