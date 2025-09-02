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

import org.janusgraph.diskstorage.BackendException;
import org.janusgraph.diskstorage.Entry;
import org.janusgraph.diskstorage.EntryList;
import org.janusgraph.diskstorage.StaticBuffer;
import org.janusgraph.diskstorage.configuration.Configuration;
import org.janusgraph.diskstorage.configuration.ModifiableConfiguration;
import org.janusgraph.diskstorage.configuration.BasicConfiguration;
import org.janusgraph.graphdb.configuration.GraphDatabaseConfiguration;
import org.janusgraph.diskstorage.keycolumnvalue.KeyColumnValueStore;
import org.janusgraph.diskstorage.keycolumnvalue.KeySliceQuery;
import org.janusgraph.diskstorage.keycolumnvalue.SliceQuery;
import org.janusgraph.diskstorage.keycolumnvalue.StoreTransaction;
import org.janusgraph.diskstorage.util.StandardBaseTransactionConfig;
import org.janusgraph.diskstorage.util.StaticArrayBuffer;
import org.janusgraph.diskstorage.util.StaticArrayEntry;
import org.janusgraph.diskstorage.util.time.TimestampProviders;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for KVT backend.
 * 
 * These tests verify that the KVT backend works correctly with both
 * composite key and serialized column storage methods.
 */
public class KVTIntegrationTest {

    private KVTStoreManager compositeKeyManager;
    private KVTStoreManager serializedManager;

    @BeforeEach
    public void setUp() {
        try {
            // Test composite key method
            ModifiableConfiguration compositeConfig = GraphDatabaseConfiguration.buildGraphConfiguration();
            compositeConfig.set(KVTStoreManager.KVT_USE_COMPOSITE_KEY, true);
            compositeKeyManager = new KVTStoreManager(compositeConfig);

            // Test serialized column method
            ModifiableConfiguration serializedConfig = GraphDatabaseConfiguration.buildGraphConfiguration();
            serializedConfig.set(KVTStoreManager.KVT_USE_COMPOSITE_KEY, false);
            serializedManager = new KVTStoreManager(serializedConfig);
            
        } catch (RuntimeException e) {
            if (e.getMessage().contains("native library")) {
                Assumptions.assumeTrue(false, "KVT native library not available: " + e.getMessage());
            }
            throw e;
        }
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (compositeKeyManager != null) {
            try {
                compositeKeyManager.close();
            } catch (Exception e) {
                // Ignore
            }
        }
        if (serializedManager != null) {
            try {
                serializedManager.close();
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    @Test
    public void testCompositeKeyMethod() throws BackendException {
        testStorageMethod(compositeKeyManager, "Composite Key");
    }

    @Test
    public void testSerializedColumnMethod() throws BackendException {
        testStorageMethod(serializedManager, "Serialized Column");
    }

    private void testStorageMethod(KVTStoreManager manager, String methodName) throws BackendException {
        System.out.println("Testing " + methodName + " method...");
        
        KeyColumnValueStore store = manager.openDatabase("test_" + methodName.toLowerCase().replace(" ", "_"), null);
        StoreTransaction tx = manager.beginTransaction(new StandardBaseTransactionConfig.Builder()
            .timestampProvider(TimestampProviders.MICRO)
            .build());

        try {
            // Test data
            StaticBuffer key = new StaticArrayBuffer("vertex:1".getBytes());
            StaticBuffer col1 = new StaticArrayBuffer("name".getBytes());
            StaticBuffer val1 = new StaticArrayBuffer("Alice".getBytes());
            StaticBuffer col2 = new StaticArrayBuffer("age".getBytes());
            StaticBuffer val2 = new StaticArrayBuffer("30".getBytes());

            // Test single insertion
            List<Entry> additions = Arrays.asList(
                StaticArrayEntry.of(col1, val1),
                StaticArrayEntry.of(col2, val2)
            );
            
            store.mutate(key, additions, Collections.emptyList(), tx);

            // Test retrieval
            SliceQuery slice = new SliceQuery(new StaticArrayBuffer(new byte[0]), 
                                            new StaticArrayBuffer(new byte[]{(byte)0xFF}));
            KeySliceQuery keySlice = new KeySliceQuery(key, slice);
            EntryList entries = store.getSlice(keySlice, tx);

            assertEquals(2, entries.size(), methodName + ": Should retrieve 2 entries");

            // Verify entries (they should be sorted by column)
            Entry entry1 = entries.get(0); // "age" comes before "name"
            Entry entry2 = entries.get(1);

            assertTrue(Arrays.equals("age".getBytes(), entry1.getColumn().as(StaticBuffer.ARRAY_FACTORY)),
                      methodName + ": First entry should be 'age'");
            assertTrue(Arrays.equals("30".getBytes(), entry1.getValue().as(StaticBuffer.ARRAY_FACTORY)),
                      methodName + ": First value should be '30'");

            assertTrue(Arrays.equals("name".getBytes(), entry2.getColumn().as(StaticBuffer.ARRAY_FACTORY)),
                      methodName + ": Second entry should be 'name'");
            assertTrue(Arrays.equals("Alice".getBytes(), entry2.getValue().as(StaticBuffer.ARRAY_FACTORY)),
                      methodName + ": Second value should be 'Alice'");

            // Test column range query
            SliceQuery nameOnly = new SliceQuery(new StaticArrayBuffer("name".getBytes()),
                                               new StaticArrayBuffer("namf".getBytes())); // Just after "name"
            KeySliceQuery nameKeySlice = new KeySliceQuery(key, nameOnly);
            EntryList nameEntries = store.getSlice(nameKeySlice, tx);

            assertEquals(1, nameEntries.size(), methodName + ": Should retrieve 1 entry for name range");
            assertTrue(Arrays.equals("name".getBytes(), nameEntries.get(0).getColumn().as(StaticBuffer.ARRAY_FACTORY)),
                      methodName + ": Should retrieve 'name' column");

            // Test deletion
            List<StaticBuffer> deletions = Collections.singletonList(col1); // Delete "name"
            store.mutate(key, Collections.emptyList(), deletions, tx);

            // Verify deletion
            EntryList afterDelete = store.getSlice(keySlice, tx);
            assertEquals(1, afterDelete.size(), methodName + ": Should have 1 entry after deletion");
            assertTrue(Arrays.equals("age".getBytes(), afterDelete.get(0).getColumn().as(StaticBuffer.ARRAY_FACTORY)),
                      methodName + ": Remaining entry should be 'age'");

            System.out.println(methodName + " method test passed!");

        } finally {
            // Note: KVT transactions are auto-committed for now
            // In a full implementation, we'd have explicit commit/rollback
        }
    }

    @Test
    public void testTransactionalBehavior() throws BackendException {
        // This test would verify ACID properties once transaction management is fully implemented
        System.out.println("Transaction behavior test - placeholder for future implementation");
    }

    @Test
    public void testMultipleStores() throws BackendException {
        KeyColumnValueStore store1 = compositeKeyManager.openDatabase("store1", null);
        KeyColumnValueStore store2 = compositeKeyManager.openDatabase("store2", null);
        
        assertNotNull(store1);
        assertNotNull(store2);
        assertNotEquals(store1, store2);
        
        assertEquals("store1", store1.getName());
        assertEquals("store2", store2.getName());
        
        System.out.println("Multiple stores test passed!");
    }

    @Test
    public void testStoreFeatures() {
        assertTrue(compositeKeyManager.getFeatures().hasOrderedScan());
        assertTrue(compositeKeyManager.getFeatures().hasUnorderedScan());
        assertTrue(compositeKeyManager.getFeatures().isKeyOrdered());
        assertFalse(compositeKeyManager.getFeatures().hasPersistence());
        assertTrue(compositeKeyManager.getFeatures().hasTxIsolation());
        
        System.out.println("Store features test passed!");
    }

    // Main method for standalone testing
    public static void main(String[] args) {
        System.out.println("KVT Integration Test");
        System.out.println("===================");
        
        KVTIntegrationTest test = new KVTIntegrationTest();
        
        try {
            test.setUp();
            
            test.testCompositeKeyMethod();
            test.testSerializedColumnMethod(); 
            test.testMultipleStores();
            test.testStoreFeatures();
            
            System.out.println("\nAll tests passed! KVT backend is working correctly.");
            
        } catch (Exception e) {
            System.err.println("Test failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            try {
                test.tearDown();
            } catch (Exception e) {
                System.err.println("Cleanup failed: " + e.getMessage());
            }
        }
    }
}