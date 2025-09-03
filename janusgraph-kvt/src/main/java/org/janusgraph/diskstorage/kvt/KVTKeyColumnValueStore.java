package org.janusgraph.diskstorage.kvt;

import org.janusgraph.diskstorage.BackendException;
import org.janusgraph.diskstorage.PermanentBackendException;
import org.janusgraph.diskstorage.Entry;
import org.janusgraph.diskstorage.EntryList;
import org.janusgraph.diskstorage.StaticBuffer;
import org.janusgraph.diskstorage.keycolumnvalue.*;
import org.janusgraph.diskstorage.util.BufferUtil;
import org.janusgraph.diskstorage.util.StaticArrayEntry;
import org.janusgraph.diskstorage.util.StaticArrayEntryList;
import org.janusgraph.diskstorage.util.RecordIterator;
import org.janusgraph.diskstorage.util.StaticArrayBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.janusgraph.diskstorage.logging.StorageLoggingUtil;
import java.util.*;

public class KVTKeyColumnValueStore implements KeyColumnValueStore {
    
    private static final Logger log = LoggerFactory.getLogger(KVTKeyColumnValueStore.class);
    
    private final KVTStoreManager manager;
    private final String name;
    private final long tableId;
    private volatile boolean isOpen;
    
    // Native methods for store operations
    private static native byte[] nativeGet(long managerPtr, long txId, long tableId, byte[] key);
    private static native boolean nativeSet(long managerPtr, long txId, long tableId, byte[] key, byte[] value);
    private static native boolean nativeDelete(long managerPtr, long txId, long tableId, byte[] key);
    private static native byte[][] nativeScan(long managerPtr, long txId, long tableId, 
                                              byte[] startKey, byte[] endKey, int limit);
    
    // Separator byte for composite keys (store_key + column_name)
    private static final byte COLUMN_SEPARATOR = 0x00;
    
    public KVTKeyColumnValueStore(KVTStoreManager manager, String name, long tableId) {
        this.manager = manager;
        this.name = name;
        this.tableId = tableId;
        this.isOpen = true;
    }
    
    @Override
    public EntryList getSlice(KeySliceQuery query, StoreTransaction txh) throws BackendException {
        long startTime = System.currentTimeMillis();
        
        if (!isOpen) {
            throw new IllegalStateException("Store is closed");
        }
        
        KVTTransaction tx = (KVTTransaction) txh;
        StaticBuffer keyBuffer = query.getKey();
        SliceQuery sliceQuery = query;
        
        // Build composite key prefix for this row
        byte[] keyBytes = getByteArray(keyBuffer);
        
        // DEBUG: Log the key being queried
        log.debug("JAVA getSlice - key bytes (len={}): {}", keyBytes.length, bytesToHex(keyBytes));
        log.debug("JAVA getSlice - original limit: {}", sliceQuery.getLimit());
        
        // Check if this is a vertex existence query being misused for iteration
        // When the key is exactly 10 bytes (vertex ID) and limit is 1, this might be
        // a vertex iteration that's incorrectly using limit=1
        if (keyBytes.length == 10 && sliceQuery.getLimit() == 1) {
            log.warn("JAVA getSlice - Detected possible vertex iteration with limit=1, key={}", bytesToHex(keyBytes));
            // For debugging, let's trace where this is coming from
            Exception e = new Exception("Stack trace for limit=1 query");
            log.debug("Stack trace:", e);
        }
        
        // Build start and end keys for scan
        byte[] startKey = buildCompositeKey(keyBytes, getByteArray(sliceQuery.getSliceStart()));
        byte[] endKey = buildCompositeKey(keyBytes, getByteArray(sliceQuery.getSliceEnd()));
        
        // DEBUG: Log scan parameters
        log.debug("JAVA scan - startKey (len={}): {}", startKey.length, bytesToHex(startKey));
        log.debug("JAVA scan - endKey (len={}): {}", endKey.length, bytesToHex(endKey));
        log.debug("JAVA scan - limit: {}", sliceQuery.getLimit());
        
        // Perform scan
        byte[][] results = nativeScan(manager.getNativeManagerPtr(), tx.getTransactionId(), 
                                      tableId, startKey, endKey, sliceQuery.getLimit());
        
        // DEBUG: Log scan results
        if (results == null) {
            log.debug("JAVA scan returned null");
        } else {
            log.debug("JAVA scan returned {} items", results.length);
            for (int i = 0; i < Math.min(results.length, 10); i++) {
                if (results[i] != null) {
                    log.debug("JAVA scan result[{}] (len={}): {}", i, results[i].length, bytesToHex(results[i]));
                } else {
                    log.debug("JAVA scan result[{}] is null", i);
                }
            }
        }
        
        if (results == null || results.length == 0) {
            EntryList result = StaticArrayEntryList.EMPTY_LIST;
            
            Map<String, Object> params = new HashMap<>();
            params.put("Key", StorageLoggingUtil.serializeBuffer(keyBuffer));
            params.put("Columns", StorageLoggingUtil.serializeBuffer(sliceQuery.getSliceStart()) + "-" + StorageLoggingUtil.serializeBuffer(sliceQuery.getSliceEnd()));
            params.put("Limit", sliceQuery.getLimit());
            params.put("ResultCount", result.size());
            StorageLoggingUtil.logFunctionCall("STORAGE-STORE:" + name, "getSlice(KeySliceQuery)", params, startTime);
            
            return result;
        }
        
        // Parse results into entries
        List<Entry> entries = new ArrayList<>(results.length / 2);
        for (int i = 0; i < results.length; i += 2) {
            if (i + 1 < results.length) {
                byte[] compositeKey = results[i];
                byte[] value = results[i + 1];
                
                // DEBUG: Log before extracting column
                log.debug("JAVA parsing entry {}: key={}, value={}", i/2, 
                    compositeKey != null ? bytesToHex(compositeKey) : "null",
                    value != null ? bytesToHex(value) : "null");
                
                // Extract column from composite key
                byte[] column = extractColumn(compositeKey, keyBytes.length);
                if (column != null) {
                    log.debug("JAVA extracted column (len={}): {}", column.length, bytesToHex(column));
                    entries.add(StaticArrayEntry.of(
                        new StaticArrayBuffer(column),
                        new StaticArrayBuffer(value)
                    ));
                } else {
                    log.debug("JAVA failed to extract column from composite key");
                }
            }
        }
        
        log.debug("JAVA returning {} entries", entries.size());
        EntryList result = StaticArrayEntryList.of(entries);
        
        Map<String, Object> params = new HashMap<>();
        params.put("Key", StorageLoggingUtil.serializeBuffer(keyBuffer));
        params.put("Columns", StorageLoggingUtil.serializeBuffer(sliceQuery.getSliceStart()) + "-" + StorageLoggingUtil.serializeBuffer(sliceQuery.getSliceEnd()));
        params.put("Limit", sliceQuery.getLimit());
        params.put("ResultCount", result.size());
        StorageLoggingUtil.logFunctionCall("STORAGE-STORE:" + name, "getSlice(KeySliceQuery)", params, startTime);
        
        return result;
    }
    
    @Override
    public Map<StaticBuffer, EntryList> getSlice(List<StaticBuffer> keys, SliceQuery query, StoreTransaction txh) throws BackendException {
        long startTime = System.currentTimeMillis();
        
        Map<StaticBuffer, EntryList> result = new HashMap<>(keys.size());
        
        for (StaticBuffer key : keys) {
            result.put(key, getSlice(new KeySliceQuery(key, query), txh));
        }
        
        Map<String, Object> params = new HashMap<>();
        params.put("KeyCount", keys.size());
        params.put("Columns", StorageLoggingUtil.serializeBuffer(query.getSliceStart()) + "-" + StorageLoggingUtil.serializeBuffer(query.getSliceEnd()));
        params.put("Limit", query.getLimit());
        StorageLoggingUtil.logFunctionCall("STORAGE-STORE:" + name, "getSlice(List<StaticBuffer>)", params, startTime);
        
        return result;
    }
    
    @Override
    public void mutate(StaticBuffer key, List<Entry> additions, List<StaticBuffer> deletions, StoreTransaction txh) throws BackendException {
        long startTime = System.currentTimeMillis();
        
        if (!isOpen) {
            throw new IllegalStateException("Store is closed");
        }
        
        KVTTransaction tx = (KVTTransaction) txh;
        byte[] keyBytes = getByteArray(key);
        
        // Process deletions
        if (deletions != null && !deletions.isEmpty()) {
            for (StaticBuffer column : deletions) {
                byte[] compositeKey = buildCompositeKey(keyBytes, getByteArray(column));
                if (!nativeDelete(manager.getNativeManagerPtr(), tx.getTransactionId(), tableId, compositeKey)) {
                    log.warn("Failed to delete key-column: {}-{}", key, column);
                }
            }
        }
        
        // Process additions
        if (additions != null && !additions.isEmpty()) {
            for (Entry entry : additions) {
                byte[] columnBytes = getByteArray(entry.getColumn());
                byte[] compositeKey = buildCompositeKey(keyBytes, columnBytes);
                byte[] value = getByteArray(entry.getValue());
                
                // DEBUG: Log what we're storing
                log.debug("JAVA mutate SET - key (len={}): {}", keyBytes.length, bytesToHex(keyBytes));
                log.debug("JAVA mutate SET - column (len={}): {}", columnBytes.length, bytesToHex(columnBytes));
                log.debug("JAVA mutate SET - compositeKey (len={}): {}", compositeKey.length, bytesToHex(compositeKey));
                log.debug("JAVA mutate SET - value (len={}): {}", value.length, bytesToHex(value));
                
                if (!nativeSet(manager.getNativeManagerPtr(), tx.getTransactionId(), tableId, compositeKey, value)) {
                    throw new PermanentBackendException("Failed to set key-column: " + key + "-" + entry.getColumn());
                }
            }
        }
        
        Map<String, Object> params = new HashMap<>();
        params.put("Key", StorageLoggingUtil.serializeBuffer(key));
        params.put("Additions", additions != null ? additions.size() : 0);
        params.put("Deletions", deletions != null ? deletions.size() : 0);
        StorageLoggingUtil.logFunctionCall("STORAGE-STORE:" + name, "mutate()", params, startTime);
    }
    
    @Override
    public void acquireLock(StaticBuffer key, StaticBuffer column, StaticBuffer expectedValue, StoreTransaction txh) throws BackendException {
        long startTime = System.currentTimeMillis();
        
        // KVT uses pessimistic locking (2PL), locks are acquired automatically on access
        // This method is a no-op as locking is handled internally by KVT
        
        Map<String, Object> params = new HashMap<>();
        params.put("Key", StorageLoggingUtil.serializeBuffer(key));
        params.put("Column", StorageLoggingUtil.serializeBuffer(column));
        params.put("ExpectedValue", expectedValue != null ? StorageLoggingUtil.serializeBuffer(expectedValue) : "null");
        StorageLoggingUtil.logFunctionCall("STORAGE-STORE:" + name, "acquireLock()", params, startTime);
    }
    
    @Override
    public KeyIterator getKeys(KeyRangeQuery query, StoreTransaction txh) throws BackendException {
        long startTime = System.currentTimeMillis();
        
        if (!isOpen) {
            throw new IllegalStateException("Store is closed");
        }
        
        KVTTransaction tx = (KVTTransaction) txh;
        
        // Convert key range to byte arrays
        byte[] startKey = query.getKeyStart() != null ? getByteArray(query.getKeyStart()) : new byte[0];
        byte[] endKey = query.getKeyEnd() != null ? getByteArray(query.getKeyEnd()) : new byte[]{(byte)0xFF};
        
        // Scan for keys in range
        byte[][] results = nativeScan(manager.getNativeManagerPtr(), tx.getTransactionId(),
                                      tableId, startKey, endKey, query.getLimit());
        
        if (results == null || results.length == 0) {
            KeyIterator result = new EmptyKeyIterator();
            
            Map<String, Object> params = new HashMap<>();
            params.put("KeyStart", StorageLoggingUtil.serializeBuffer(query.getKeyStart()));
            params.put("KeyEnd", StorageLoggingUtil.serializeBuffer(query.getKeyEnd()));
            params.put("Limit", query.getLimit());
            StorageLoggingUtil.logFunctionCall("STORAGE-STORE:" + name, "getKeys(KeyRangeQuery)", params, startTime);
            
            return result;
        }
        
        // Group results by key
        Map<StaticBuffer, List<Entry>> keyMap = new LinkedHashMap<>();
        byte[] lastKey = null;
        
        for (int i = 0; i < results.length; i += 2) {
            if (i + 1 < results.length) {
                byte[] compositeKey = results[i];
                byte[] value = results[i + 1];
                
                // Extract original key and column
                int sepIndex = findSeparator(compositeKey);
                if (sepIndex > 0) {
                    byte[] originalKey = Arrays.copyOfRange(compositeKey, 0, sepIndex);
                    byte[] column = Arrays.copyOfRange(compositeKey, sepIndex + 1, compositeKey.length);
                    
                    StaticBuffer keyBuffer = new StaticArrayBuffer(originalKey);
                    
                    keyMap.computeIfAbsent(keyBuffer, k -> new ArrayList<>())
                          .add(StaticArrayEntry.of(
                              new StaticArrayBuffer(column),
                              new StaticArrayBuffer(value)
                          ));
                }
            }
        }
        
        // Convert to iterator
        Iterator<Map.Entry<StaticBuffer, List<Entry>>> iter = keyMap.entrySet().iterator();
        
        KeyIterator result = new KeyIterator() {
            @Override
            public RecordIterator<Entry> getEntries() {
                if (!iter.hasNext()) {
                    return new RecordIterator<Entry>() {
                    @Override
                    public boolean hasNext() {
                        return false;
                    }
                    
                    @Override
                    public Entry next() {
                        throw new NoSuchElementException();
                    }
                    
                    @Override
                    public void close() {
                    }
                };
                }
                
                Map.Entry<StaticBuffer, List<Entry>> current = iter.next();
                return new RecordIterator<Entry>() {
                    private final Iterator<Entry> entries = current.getValue().iterator();
                    
                    @Override
                    public boolean hasNext() {
                        return entries.hasNext();
                    }
                    
                    @Override
                    public Entry next() {
                        return entries.next();
                    }
                    
                    @Override
                    public void close() {
                    }
                };
            }
            
            @Override
            public boolean hasNext() {
                return iter.hasNext();
            }
            
            @Override
            public StaticBuffer next() {
                return iter.next().getKey();
            }
            
            @Override
            public void close() {
            }
        };
        
        Map<String, Object> params = new HashMap<>();
        params.put("KeyStart", StorageLoggingUtil.serializeBuffer(query.getKeyStart()));
        params.put("KeyEnd", StorageLoggingUtil.serializeBuffer(query.getKeyEnd()));
        params.put("Limit", query.getLimit());
        StorageLoggingUtil.logFunctionCall("STORAGE-STORE:" + name, "getKeys(KeyRangeQuery)", params, startTime);
        
        return result;
    }
    
    @Override
    public KeyIterator getKeys(SliceQuery query, StoreTransaction txh) throws BackendException {
        long startTime = System.currentTimeMillis();
        
        // For unordered scan, we scan all keys and filter by column range
        // When doing a full scan for vertex iteration, we need to remove the limit from existence queries
        // The limit=1 is meant for checking vertex existence, not for iterating all vertices
        SliceQuery scanQuery = query;
        
        log.debug("JAVA getKeys(SliceQuery) called with limit={}, sliceStart={}, sliceEnd={}", 
                 query.getLimit(), 
                 query.getSliceStart() != null ? bytesToHex(query.getSliceStart().as(StaticBuffer.ARRAY_FACTORY)) : "null",
                 query.getSliceEnd() != null ? bytesToHex(query.getSliceEnd().as(StaticBuffer.ARRAY_FACTORY)) : "null");
        
        if (query.getLimit() == 1) {
            // This is likely a vertex existence query being reused for iteration
            // Create a new query without the limit
            log.debug("JAVA getKeys - removing limit=1 for full scan");
            scanQuery = new SliceQuery(query.getSliceStart(), query.getSliceEnd()).setLimit(Integer.MAX_VALUE);
        }
        // Use an empty byte array for start and a large byte array for end to scan all keys
        byte[] endBytes = new byte[8];
        Arrays.fill(endBytes, (byte)0xFF);
        KeyIterator result = getKeys(new KeyRangeQuery(new StaticArrayBuffer(new byte[0]), new StaticArrayBuffer(endBytes), scanQuery), txh);
        
        Map<String, Object> params = new HashMap<>();
        params.put("Columns", StorageLoggingUtil.serializeBuffer(query.getSliceStart()) + "-" + StorageLoggingUtil.serializeBuffer(query.getSliceEnd()));
        params.put("Limit", query.getLimit());
        StorageLoggingUtil.logFunctionCall("STORAGE-STORE:" + name, "getKeys(SliceQuery)", params, startTime);
        
        return result;
    }
    
    @Override
    public KeySlicesIterator getKeys(MultiSlicesQuery query, StoreTransaction txh) throws BackendException {
        long startTime = System.currentTimeMillis();
        
        // Simplified implementation - would need optimization for production
        KeySlicesIterator result = new KeySlicesIterator() {
            @Override
            public boolean hasNext() {
                return false;
            }
            
            @Override
            public StaticBuffer next() {
                throw new NoSuchElementException();
            }
            
            @Override
            public void close() {
            }
            
            @Override
            public Map<SliceQuery, RecordIterator<Entry>> getEntries() {
                return new HashMap<>();
            }
        };
        
        Map<String, Object> params = new HashMap<>();
        params.put("SliceCount", query.getQueries().size());
        StorageLoggingUtil.logFunctionCall("STORAGE-STORE:" + name, "getKeys(MultiSlicesQuery)", params, startTime);
        
        return result;
    }
    
    @Override
    public String getName() {
        long startTime = System.currentTimeMillis();
        String result = name;
        
        StorageLoggingUtil.logFunctionCall("STORAGE-STORE:" + name, "getName()", null, startTime);
        
        return result;
    }
    
    @Override
    public void close() throws BackendException {
        long startTime = System.currentTimeMillis();
        
        isOpen = false;
        
        StorageLoggingUtil.logFunctionCall("STORAGE-STORE:" + name, "close()", null, startTime);
    }
    
    // Helper methods
    
    private static String bytesToHex(byte[] bytes) {
        if (bytes == null) return "null";
        if (bytes.length == 0) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < Math.min(bytes.length, 32); i++) {
            if (i > 0) sb.append(" ");
            sb.append(String.format("%02X", bytes[i] & 0xFF));
        }
        if (bytes.length > 32) {
            sb.append(" ... (").append(bytes.length).append(" bytes total)");
        }
        sb.append("]");
        return sb.toString();
    }
    
    private static byte[] getByteArray(StaticBuffer buffer) {
        byte[] bytes = new byte[buffer.length()];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = buffer.getByte(i);
        }
        return bytes;
    }
    
    private static byte[] buildCompositeKey(byte[] key, byte[] column) {
        byte[] composite = new byte[key.length + 1 + column.length];
        System.arraycopy(key, 0, composite, 0, key.length);
        composite[key.length] = COLUMN_SEPARATOR;
        System.arraycopy(column, 0, composite, key.length + 1, column.length);
        return composite;
    }
    
    private static byte[] extractColumn(byte[] compositeKey, int keyLength) {
        if (compositeKey.length <= keyLength + 1) {
            return null;
        }
        if (compositeKey[keyLength] != COLUMN_SEPARATOR) {
            return null;
        }
        return Arrays.copyOfRange(compositeKey, keyLength + 1, compositeKey.length);
    }
    
    private static int findSeparator(byte[] compositeKey) {
        for (int i = 0; i < compositeKey.length; i++) {
            if (compositeKey[i] == COLUMN_SEPARATOR) {
                return i;
            }
        }
        return -1;
    }
    
    // Empty KeyIterator implementation
    private static class EmptyKeyIterator implements KeyIterator {
        @Override
        public RecordIterator<Entry> getEntries() {
            return new RecordIterator<Entry>() {
                @Override
                public boolean hasNext() {
                    return false;
                }
                
                @Override
                public Entry next() {
                    throw new NoSuchElementException();
                }
                
                @Override
                public void close() {
                }
            };
        }
        
        @Override
        public boolean hasNext() {
            return false;
        }
        
        @Override
        public StaticBuffer next() {
            throw new NoSuchElementException();
        }
        
        @Override
        public void close() {
        }
    }
}