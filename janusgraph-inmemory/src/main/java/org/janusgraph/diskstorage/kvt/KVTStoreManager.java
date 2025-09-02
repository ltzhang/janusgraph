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
import org.janusgraph.diskstorage.BackendException;
import org.janusgraph.diskstorage.PermanentBackendException;
import org.janusgraph.diskstorage.BaseTransactionConfig;
import org.janusgraph.diskstorage.StaticBuffer;
import org.janusgraph.diskstorage.StoreMetaData;
import org.janusgraph.diskstorage.common.AbstractStoreTransaction;
import org.janusgraph.diskstorage.configuration.ConfigNamespace;
import org.janusgraph.diskstorage.configuration.ConfigOption;
import org.janusgraph.diskstorage.configuration.Configuration;
import org.janusgraph.diskstorage.keycolumnvalue.KCVMutation;
import org.janusgraph.diskstorage.keycolumnvalue.KeyColumnValueStore;
import org.janusgraph.diskstorage.keycolumnvalue.KeyColumnValueStoreManager;
import org.janusgraph.diskstorage.keycolumnvalue.KeyRange;
import org.janusgraph.diskstorage.keycolumnvalue.StandardStoreFeatures;
import org.janusgraph.diskstorage.keycolumnvalue.StoreFeatures;
import org.janusgraph.diskstorage.keycolumnvalue.StoreTransaction;
import org.janusgraph.graphdb.configuration.GraphDatabaseConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * KVT (Key-Value Transaction) based storage manager.
 * 
 * This implementation uses a C++ transactional key-value store via JNI
 * to provide JanusGraph with a high-performance in-memory backend.
 * 
 * Features:
 * - Full ACID transactions
 * - Two storage methods: composite keys or serialized columns
 * - Thread-safe operations
 * - Range queries for ordered access
 */
public class KVTStoreManager implements KeyColumnValueStoreManager {

    private static final Logger log = LoggerFactory.getLogger(KVTStoreManager.class);

    public static final ConfigNamespace KVT_NS = new ConfigNamespace(
        GraphDatabaseConfiguration.STORAGE_NS, "kvt", "KVT storage backend options");

    public static final ConfigOption<Boolean> KVT_USE_COMPOSITE_KEY = new ConfigOption<>(
        KVT_NS, "use-composite-key",
        "Use composite key method (true) or serialized columns method (false)",
        ConfigOption.Type.LOCAL, false);

    private final ConcurrentHashMap<String, KVTKeyColumnValueStore> stores;
    private final StoreFeatures features;
    private final boolean useCompositeKey;
    private volatile boolean isOpen = false;

    static {
        try {
            System.loadLibrary("janusgraph_kvt");
            log.info("Successfully loaded KVT native library");
        } catch (UnsatisfiedLinkError e) {
            log.error("Failed to load KVT native library: {}", e.getMessage());
            throw new RuntimeException("Cannot load KVT native library", e);
        }
    }

    // Native methods
    private native boolean initializeKVT();
    private native void shutdownKVT();
    private native long openDatabase(String storeName, boolean useCompositeKey);
    private native boolean nativeExists();
    private native void nativeClearStorage();

    public KVTStoreManager() {
        this(Configuration.EMPTY);
    }

    public KVTStoreManager(final Configuration configuration) {
        this.stores = new ConcurrentHashMap<>();
        this.useCompositeKey = configuration.get(KVT_USE_COMPOSITE_KEY);

        // Initialize native KVT system
        if (!initializeKVT()) {
            throw new RuntimeException("Failed to initialize KVT system");
        }

        this.features = new StandardStoreFeatures.Builder()
            .orderedScan(true)  // KVT supports range scans
            .unorderedScan(true)
            .keyOrdered(true)   // Keys are ordered in KVT
            .persists(false)    // In-memory storage
            .optimisticLocking(false)  // KVT uses pessimistic locking
            .keyConsistent(GraphDatabaseConfiguration.buildGraphConfiguration())
            .transactional(true)  // Full ACID transactions
            .batchMutation(true)
            .multiQuery(true)
            .timestamps(false)
            .build();

        this.isOpen = true;
        log.info("KVTStoreManager initialized with composite key method: {}", useCompositeKey);
    }

    @Override
    public StoreTransaction beginTransaction(final BaseTransactionConfig config) throws BackendException {
        Preconditions.checkState(isOpen, "Store manager has been closed");
        return new KVTTransaction(config);
    }

    @Override
    public void close() throws BackendException {
        if (!isOpen) {
            return;
        }

        try {
            // Close all stores
            for (KVTKeyColumnValueStore store : stores.values()) {
                try {
                    store.close();
                } catch (Exception e) {
                    log.warn("Error closing store {}: {}", store.getName(), e.getMessage());
                }
            }
            stores.clear();

            // Shutdown native KVT system
            shutdownKVT();
            
            isOpen = false;
            log.info("KVTStoreManager closed successfully");
        } catch (Exception e) {
            throw new PermanentBackendException("Error closing KVT store manager", e);
        }
    }

    @Override
    public void clearStorage() throws BackendException {
        Preconditions.checkState(isOpen, "Store manager has been closed");
        
        try {
            // Clear all individual stores
            for (KVTKeyColumnValueStore store : stores.values()) {
                // This will be handled by the native implementation
            }
            
            // Call native clear
            nativeClearStorage();
            
            log.info("KVT storage cleared");
        } catch (Exception e) {
            throw new PermanentBackendException("Error clearing KVT storage", e);
        }
    }

    @Override
    public boolean exists() throws BackendException {
        if (!isOpen) {
            return false;
        }
        return nativeExists();
    }

    @Override
    public StoreFeatures getFeatures() {
        return features;
    }

    @Override
    public KeyColumnValueStore openDatabase(final String name, StoreMetaData.Container metaData) throws BackendException {
        Preconditions.checkState(isOpen, "Store manager has been closed");
        Preconditions.checkNotNull(name, "Store name cannot be null");

        if (stores.containsKey(name)) {
            return stores.get(name);
        }

        try {
            // Open database in native KVT
            long storeId = openDatabase(name, useCompositeKey);
            if (storeId == 0) {
                throw new PermanentBackendException("Failed to open KVT database: " + name);
            }

            KVTKeyColumnValueStore store = new KVTKeyColumnValueStore(name, storeId);
            KVTKeyColumnValueStore existing = stores.putIfAbsent(name, store);
            
            if (existing != null) {
                // Another thread beat us to it, close the one we created
                store.close();
                return existing;
            }

            log.debug("Opened KVT store: {}", name);
            return store;
            
        } catch (Exception e) {
            throw new PermanentBackendException("Error opening KVT database: " + name, e);
        }
    }

    @Override
    public void mutateMany(Map<String, Map<StaticBuffer, KCVMutation>> mutations, StoreTransaction txh) throws BackendException {
        Preconditions.checkState(isOpen, "Store manager has been closed");
        
        for (Map.Entry<String, Map<StaticBuffer, KCVMutation>> storeMut : mutations.entrySet()) {
            KeyColumnValueStore store = stores.get(storeMut.getKey());
            if (store == null) {
                // Auto-create store if it doesn't exist
                store = openDatabase(storeMut.getKey(), StoreMetaData.EMPTY);
            }
            
            for (Map.Entry<StaticBuffer, KCVMutation> keyMut : storeMut.getValue().entrySet()) {
                store.mutate(keyMut.getKey(), keyMut.getValue().getAdditions(), 
                           keyMut.getValue().getDeletions(), txh);
            }
        }
    }

    @Override
    public List<KeyRange> getLocalKeyPartition() throws BackendException {
        throw new UnsupportedOperationException("KVT does not support key partitioning");
    }

    @Override
    public String getName() {
        return "kvt";
    }

    /**
     * KVT-specific transaction implementation
     */
    private static class KVTTransaction extends AbstractStoreTransaction {
        
        private final long nativeTxId;
        private volatile boolean isOpen = true;

        public KVTTransaction(final BaseTransactionConfig config) {
            super(config);
            // Native transaction will be started when needed by individual stores
            this.nativeTxId = 0; // Placeholder - will be set by stores
        }

        public boolean isOpen() {
            return isOpen;
        }

        public void markClosed() {
            isOpen = false;
        }
    }

    // Package-private method for stores to access transaction state
    static boolean isTransactionOpen(StoreTransaction txh) {
        if (txh instanceof KVTTransaction) {
            return ((KVTTransaction) txh).isOpen();
        }
        return true; // Assume open for other transaction types
    }

    static void markTransactionClosed(StoreTransaction txh) {
        if (txh instanceof KVTTransaction) {
            ((KVTTransaction) txh).markClosed();
        }
    }
}