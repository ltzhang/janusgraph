package org.janusgraph.diskstorage.inmemory;

import org.janusgraph.diskstorage.BackendException;
import org.janusgraph.diskstorage.BaseTransactionConfig;
import org.janusgraph.diskstorage.StaticBuffer;
import org.janusgraph.diskstorage.keycolumnvalue.KeySliceQuery;
import org.janusgraph.diskstorage.keycolumnvalue.SliceQuery;
import org.janusgraph.diskstorage.keycolumnvalue.StoreTransaction;
import org.janusgraph.diskstorage.logging.Backend;
import org.janusgraph.diskstorage.util.StaticArrayBuffer;

/**
 * Simple test to verify InMemory logging implementation.
 */
public class InMemoryLoggingTest {
    
    public static void main(String[] args) throws BackendException {
        // Enable logging
        Backend.ENABLE_DETAILED_LOGGING = true;
        
        System.out.println("=== InMemory Storage Backend Logging Test ===");
        
        // Create store manager
        InMemoryStoreManager manager = new InMemoryStoreManager();
        
        // Begin transaction
        StoreTransaction tx = manager.beginTransaction(null);
        
        // Open database
        manager.openDatabase("edgestore", null);
        
        // Create some test data
        StaticBuffer key1 = new StaticArrayBuffer("vertex123".getBytes());
        StaticBuffer key2 = new StaticArrayBuffer(new byte[]{(byte)0x00, (byte)0x01, (byte)0x02, (byte)0x03});
        StaticBuffer column1 = new StaticArrayBuffer("property1".getBytes());
        StaticBuffer column2 = new StaticArrayBuffer(new byte[]{(byte)0xFF, (byte)0xFE, (byte)0xFD});
        
        // Test getSlice with printable key
        KeySliceQuery query1 = new KeySliceQuery(key1, new SliceQuery(column1, column1));
        manager.openDatabase("edgestore", null).getSlice(query1, tx);
        
        // Test getSlice with binary key
        KeySliceQuery query2 = new KeySliceQuery(key2, new SliceQuery(column2, column2));
        manager.openDatabase("edgestore", null).getSlice(query2, tx);
        
        // Test mutate
        manager.openDatabase("edgestore", null).mutate(key1, null, null, tx);
        
        // Commit transaction
        tx.commit();
        
        // Close manager
        manager.close();
        
        System.out.println("=== Test Complete ===");
    }
}
