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

import java.nio.ByteBuffer;
import java.util.*;

public class KVTKeyColumnValueStore implements KeyColumnValueStore {
    
    private static final Logger log = LoggerFactory.getLogger(KVTKeyColumnValueStore.class);
    
    private final KVTStoreManager manager;
    private final String name;
    private final long tableId;
    private volatile boolean isOpen;
    
    // Native methods for store operations
    private static native byte[] nativeGet(long managerPtr, long txId, String tableName, byte[] key);
    private static native boolean nativeSet(long managerPtr, long txId, String tableName, byte[] key, byte[] value);
    private static native boolean nativeDelete(long managerPtr, long txId, String tableName, byte[] key);
    private static native byte[][] nativeScan(long managerPtr, long txId, String tableName, 
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
        if (!isOpen) {
            throw new IllegalStateException("Store is closed");
        }
        
        KVTTransaction tx = (KVTTransaction) txh;
        StaticBuffer keyBuffer = query.getKey();
        SliceQuery sliceQuery = query;
        
        // Build composite key prefix for this row
        byte[] keyBytes = getByteArray(keyBuffer);
        
        // Build start and end keys for scan
        byte[] startKey = buildCompositeKey(keyBytes, getByteArray(sliceQuery.getSliceStart()));
        byte[] endKey = buildCompositeKey(keyBytes, getByteArray(sliceQuery.getSliceEnd()));
        
        // Perform scan
        byte[][] results = nativeScan(manager.getNativeManagerPtr(), tx.getTransactionId(), 
                                      name, startKey, endKey, sliceQuery.getLimit());
        
        if (results == null || results.length == 0) {
            return StaticArrayEntryList.EMPTY_LIST;
        }
        
        // Parse results into entries
        List<Entry> entries = new ArrayList<>(results.length / 2);
        for (int i = 0; i < results.length; i += 2) {
            if (i + 1 < results.length) {
                byte[] compositeKey = results[i];
                byte[] value = results[i + 1];
                
                // Extract column from composite key
                byte[] column = extractColumn(compositeKey, keyBytes.length);
                if (column != null) {
                    entries.add(StaticArrayEntry.of(
                        new StaticArrayBuffer(column),
                        new StaticArrayBuffer(value)
                    ));
                }
            }
        }
        
        return StaticArrayEntryList.of(entries);
    }
    
    @Override
    public Map<StaticBuffer, EntryList> getSlice(List<StaticBuffer> keys, SliceQuery query, StoreTransaction txh) throws BackendException {
        Map<StaticBuffer, EntryList> result = new HashMap<>(keys.size());
        
        for (StaticBuffer key : keys) {
            result.put(key, getSlice(new KeySliceQuery(key, query), txh));
        }
        
        return result;
    }
    
    @Override
    public void mutate(StaticBuffer key, List<Entry> additions, List<StaticBuffer> deletions, StoreTransaction txh) throws BackendException {
        if (!isOpen) {
            throw new IllegalStateException("Store is closed");
        }
        
        KVTTransaction tx = (KVTTransaction) txh;
        byte[] keyBytes = getByteArray(key);
        
        // Process deletions
        if (deletions != null && !deletions.isEmpty()) {
            for (StaticBuffer column : deletions) {
                byte[] compositeKey = buildCompositeKey(keyBytes, getByteArray(column));
                if (!nativeDelete(manager.getNativeManagerPtr(), tx.getTransactionId(), name, compositeKey)) {
                    log.warn("Failed to delete key-column: {}-{}", key, column);
                }
            }
        }
        
        // Process additions
        if (additions != null && !additions.isEmpty()) {
            for (Entry entry : additions) {
                byte[] compositeKey = buildCompositeKey(keyBytes, getByteArray(entry.getColumn()));
                byte[] value = getByteArray(entry.getValue());
                
                if (!nativeSet(manager.getNativeManagerPtr(), tx.getTransactionId(), name, compositeKey, value)) {
                    throw new PermanentBackendException("Failed to set key-column: " + key + "-" + entry.getColumn());
                }
            }
        }
    }
    
    @Override
    public void acquireLock(StaticBuffer key, StaticBuffer column, StaticBuffer expectedValue, StoreTransaction txh) throws BackendException {
        // KVT uses pessimistic locking (2PL), locks are acquired automatically on access
        // This method is a no-op as locking is handled internally by KVT
    }
    
    @Override
    public KeyIterator getKeys(KeyRangeQuery query, StoreTransaction txh) throws BackendException {
        if (!isOpen) {
            throw new IllegalStateException("Store is closed");
        }
        
        KVTTransaction tx = (KVTTransaction) txh;
        
        // Convert key range to byte arrays
        byte[] startKey = query.getKeyStart() != null ? getByteArray(query.getKeyStart()) : new byte[0];
        byte[] endKey = query.getKeyEnd() != null ? getByteArray(query.getKeyEnd()) : new byte[]{(byte)0xFF};
        
        // Scan for keys in range
        byte[][] results = nativeScan(manager.getNativeManagerPtr(), tx.getTransactionId(),
                                      name, startKey, endKey, query.getLimit());
        
        if (results == null || results.length == 0) {
            return new EmptyKeyIterator();
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
        
        return new KeyIterator() {
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
    }
    
    @Override
    public KeyIterator getKeys(SliceQuery query, StoreTransaction txh) throws BackendException {
        // For unordered scan, we scan all keys and filter by column range
        return getKeys(new KeyRangeQuery(new StaticArrayBuffer(new byte[]{0}), new StaticArrayBuffer(new byte[]{(byte)0xFF}), query), txh);
    }
    
    @Override
    public KeySlicesIterator getKeys(MultiSlicesQuery query, StoreTransaction txh) throws BackendException {
        // Simplified implementation - would need optimization for production
        return new KeySlicesIterator() {
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
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public void close() throws BackendException {
        isOpen = false;
    }
    
    // Helper methods
    
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