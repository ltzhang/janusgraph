package org.janusgraph.diskstorage.kvt;

import org.janusgraph.diskstorage.BackendException;
import org.janusgraph.diskstorage.BaseTransactionConfig;
import org.janusgraph.diskstorage.PermanentBackendException;
import org.janusgraph.diskstorage.StaticBuffer;
import org.janusgraph.diskstorage.StoreMetaData;
import org.janusgraph.diskstorage.common.AbstractStoreManager;
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

import java.io.File;
import org.janusgraph.diskstorage.logging.StorageLoggingUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.janusgraph.graphdb.configuration.GraphDatabaseConfiguration.GRAPH_NAME;

public class KVTStoreManager extends AbstractStoreManager implements KeyColumnValueStoreManager {
    
    private static final Logger log = LoggerFactory.getLogger(KVTStoreManager.class);
    
    public static final String KVT_BACKEND_NAME = "kvt";
    
    private final StoreFeatures features;
    private final Map<String, KVTKeyColumnValueStore> stores;
    private final Configuration configuration;
    private volatile boolean isOpen;
    
    static {
        // Load native library
        try {
            String libName = System.mapLibraryName("janusgraph-kvt-jni");
            String nativePath = KVTStoreManager.class.getResource("/native/" + libName).getPath();
            System.load(nativePath);
            log.info("Loaded KVT native library from: {}", nativePath);
        } catch (Exception e) {
            log.warn("Failed to load KVT native library from resources, trying system library path", e);
            try {
                System.loadLibrary("janusgraph-kvt-jni");
                log.info("Loaded KVT native library from system path");
            } catch (UnsatisfiedLinkError ule) {
                log.error("Failed to load KVT native library", ule);
                throw new RuntimeException("Could not load KVT native library", ule);
            }
        }
    }
    
    // Native methods
    private static native long nativeInitialize();
    private static native void nativeShutdown(long managerPtr);
    private static native long nativeCreateTable(long managerPtr, String tableName, String partitionMethod);
    private static native long nativeStartTransaction(long managerPtr);
    private static native boolean nativeCommitTransaction(long managerPtr, long txId);
    private static native boolean nativeRollbackTransaction(long managerPtr, long txId);
    
    private long nativeManagerPtr = 0;
    
    public KVTStoreManager(Configuration configuration) throws BackendException {
        super(configuration);
        this.configuration = configuration;
        this.stores = new ConcurrentHashMap<>();
        this.isOpen = true;
        
        // Initialize native KVT manager
        nativeManagerPtr = nativeInitialize();
        if (nativeManagerPtr == 0) {
            throw new PermanentBackendException("Failed to initialize KVT native manager");
        }
        
        // Build store features
        StandardStoreFeatures.Builder builder = new StandardStoreFeatures.Builder();
        
        // KVT supports full transactions
        builder.transactional(true);
        builder.locking(true);
        
        // KVT is in-memory but we treat it as persistent per instructions
        builder.persists(true);
        
        // KVT supports batch operations
        builder.batchMutation(true);
        
        // KVT supports ordered scans with range partitioning
        builder.orderedScan(true);
        builder.unorderedScan(true);
        
        // Keys are ordered when using range partitioning
        builder.keyOrdered(true);
        
        // KVT can be distributed (per instructions to assume all capabilities)
        builder.distributed(true);
        
        // Set key consistency with basic configuration
        builder.keyConsistent(GraphDatabaseConfiguration.buildGraphConfiguration());
        
        // KVT uses pessimistic locking (2PL)
        builder.optimisticLocking(false);
        
        // Additional features
        builder.multiQuery(true);
        builder.cellTTL(false); // Not evident from interface
        builder.storeTTL(false); // Not evident from interface
        builder.timestamps(false); // Not evident from interface
        builder.visibility(false); // Not evident from interface
        
        this.features = builder.build();
        
        log.info("Initialized KVT Store Manager with native pointer: {}", nativeManagerPtr);
    }
    
    @Override
    public KeyColumnValueStore openDatabase(String name) throws BackendException {
        long startTime = System.currentTimeMillis();
        
        KeyColumnValueStore result = openDatabase(name, StoreMetaData.EMPTY);
        
        Map<String, Object> params = new HashMap<>();
        params.put("Name", name);
        StorageLoggingUtil.logFunctionCall("STORAGE-MANAGER", "openDatabase()", params, startTime);
        
        return result;
    }
    
    @Override
    public KeyColumnValueStore openDatabase(String name, StoreMetaData.Container metaData) throws BackendException {
        if (!isOpen) {
            throw new IllegalStateException("Store manager is closed");
        }
        
        return stores.computeIfAbsent(name, n -> {
            try {
                // Create table in KVT with range partitioning for ordered operations
                long tableId = nativeCreateTable(nativeManagerPtr, n, "range");
                if (tableId == 0) {
                    throw new PermanentBackendException("Failed to create KVT table: " + n);
                }
                
                log.debug("Created KVT table '{}' with ID: {}", n, tableId);
                return new KVTKeyColumnValueStore(this, n, tableId);
            } catch (Exception e) {
                throw new RuntimeException("Failed to open database: " + n, e);
            }
        });
    }
    
    @Override
    public void mutateMany(Map<String, Map<StaticBuffer, KCVMutation>> mutations, StoreTransaction txh) throws BackendException {
        long startTime = System.currentTimeMillis();
        
        if (!isOpen) {
            throw new IllegalStateException("Store manager is closed");
        }
        
        KVTTransaction tx = (KVTTransaction) txh;
        
        for (Map.Entry<String, Map<StaticBuffer, KCVMutation>> storeEntry : mutations.entrySet()) {
            String storeName = storeEntry.getKey();
            KVTKeyColumnValueStore store = (KVTKeyColumnValueStore) openDatabase(storeName);
            
            for (Map.Entry<StaticBuffer, KCVMutation> mutationEntry : storeEntry.getValue().entrySet()) {
                StaticBuffer key = mutationEntry.getKey();
                KCVMutation mutation = mutationEntry.getValue();
                
                store.mutate(key, mutation.getAdditions(), mutation.getDeletions(), tx);
            }
        }
        
        Map<String, Object> params = new HashMap<>();
        params.put("Stores", mutations.size());
        params.put("TotalMutations", mutations.values().stream().mapToInt(Map::size).sum());
        StorageLoggingUtil.logFunctionCall("STORAGE-MANAGER", "mutateMany()", params, startTime);
    }
    
    @Override
    public StoreTransaction beginTransaction(BaseTransactionConfig config) throws BackendException {
        long startTime = System.currentTimeMillis();
        
        if (!isOpen) {
            throw new IllegalStateException("Store manager is closed");
        }
        
        long txId = nativeStartTransaction(nativeManagerPtr);
        if (txId == 0) {
            throw new PermanentBackendException("Failed to start KVT transaction");
        }
        
        KVTTransaction tx = new KVTTransaction(this, txId, config);
        
        Map<String, Object> params = new HashMap<>();
        params.put("Config", config.toString());
        params.put("TxId", txId);
        StorageLoggingUtil.logFunctionCall("STORAGE-MANAGER", "beginTransaction()", params, startTime);
        
        return tx;
    }
    
    @Override
    public void close() throws BackendException {
        long startTime = System.currentTimeMillis();
        
        if (!isOpen) {
            return;
        }
        
        isOpen = false;
        
        // Close all stores
        for (KVTKeyColumnValueStore store : stores.values()) {
            store.close();
        }
        stores.clear();
        
        // Shutdown native manager
        if (nativeManagerPtr != 0) {
            nativeShutdown(nativeManagerPtr);
            nativeManagerPtr = 0;
        }
        
        log.info("KVT Store Manager closed");
        
        StorageLoggingUtil.logFunctionCall("STORAGE-MANAGER", "close()", null, startTime);
    }
    
    @Override
    public void clearStorage() throws BackendException {
        long startTime = System.currentTimeMillis();
        
        if (!isOpen) {
            throw new IllegalStateException("Store manager is closed");
        }
        
        // Close and recreate all stores
        for (String storeName : stores.keySet()) {
            stores.remove(storeName);
        }
        
        // Re-initialize native manager
        if (nativeManagerPtr != 0) {
            nativeShutdown(nativeManagerPtr);
        }
        nativeManagerPtr = nativeInitialize();
        if (nativeManagerPtr == 0) {
            throw new PermanentBackendException("Failed to re-initialize KVT after clear");
        }
        
        log.info("Cleared KVT storage");
        
        StorageLoggingUtil.logFunctionCall("STORAGE-MANAGER", "clearStorage()", null, startTime);
    }
    
    @Override
    public boolean exists() throws BackendException {
        long startTime = System.currentTimeMillis();
        boolean result = isOpen && nativeManagerPtr != 0;
        
        Map<String, Object> params = new HashMap<>();
        params.put("Result", result);
        StorageLoggingUtil.logFunctionCall("STORAGE-MANAGER", "exists()", params, startTime);
        
        return result;
    }
    
    @Override
    public StoreFeatures getFeatures() {
        long startTime = System.currentTimeMillis();
        StoreFeatures result = features;
        
        StorageLoggingUtil.logFunctionCall("STORAGE-MANAGER", "getFeatures()", null, startTime);
        
        return result;
    }
    
    @Override
    public String getName() {
        long startTime = System.currentTimeMillis();
        String result = KVT_BACKEND_NAME;
        
        Map<String, Object> params = new HashMap<>();
        params.put("Result", result);
        StorageLoggingUtil.logFunctionCall("STORAGE-MANAGER", "getName()", params, startTime);
        
        return result;
    }
    
    @Override
    public List<KeyRange> getLocalKeyPartition() throws BackendException {
        long startTime = System.currentTimeMillis();
        
        StorageLoggingUtil.logFunctionCall("STORAGE-MANAGER", "getLocalKeyPartition()", null, startTime);
        
        // Return empty list for non-distributed storage
        return new ArrayList<>();
    }
    
    public Configuration getConfiguration() {
        return configuration;
    }
    
    // Package-private methods for transaction management
    boolean commitTransaction(long txId) {
        return nativeCommitTransaction(nativeManagerPtr, txId);
    }
    
    boolean rollbackTransaction(long txId) {
        return nativeRollbackTransaction(nativeManagerPtr, txId);
    }
    
    long getNativeManagerPtr() {
        return nativeManagerPtr;
    }
}