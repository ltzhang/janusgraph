import org.janusgraph.diskstorage.BackendException;
import org.janusgraph.diskstorage.Entry;
import org.janusgraph.diskstorage.EntryList;
import org.janusgraph.diskstorage.StaticBuffer;
import org.janusgraph.diskstorage.StoreMetaData;
import org.janusgraph.diskstorage.inmemory.InMemoryKeyColumnValueStore;
import org.janusgraph.diskstorage.inmemory.InMemoryStoreManager;
import org.janusgraph.diskstorage.keycolumnvalue.KCVMutation;
import org.janusgraph.diskstorage.keycolumnvalue.KeyColumnValueStore;
import org.janusgraph.diskstorage.keycolumnvalue.KeySliceQuery;
import org.janusgraph.diskstorage.keycolumnvalue.SliceQuery;
import org.janusgraph.diskstorage.keycolumnvalue.StoreTransaction;
import org.janusgraph.diskstorage.util.StandardBaseTransactionConfig;
import org.janusgraph.diskstorage.util.StaticArrayBuffer;
import org.janusgraph.diskstorage.util.StaticArrayEntry;
import org.janusgraph.diskstorage.util.time.TimestampProviders;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Comprehensive test program for JanusGraph InMemory Database functionality.
 * This program tests the core components without requiring external test frameworks.
 */
public class InMemoryDatabaseTest {

    private static final String COL_START = "00col-start";
    private static final String COL_END = "zzcol-end";
    private static final String VERY_END = "zzzzz";

    private static int testsRun = 0;
    private static int testsPass = 0;

    public static void main(String[] args) {
        System.out.println("Starting JanusGraph InMemory Database Tests...");
        
        try {
            testStaticBufferOperations();
            testEntryOperations();
            testInMemoryStoreManager();
            testKeyColumnValueStore();
            testMutations();
            testSliceQueries();
            testMultipleStores();
            testComplexScenario();
            testSnapshotFunctionality();
            
            System.out.println("\n=== Test Results ===");
            System.out.println("Tests Run: " + testsRun);
            System.out.println("Tests Passed: " + testsPass);
            System.out.println("Tests Failed: " + (testsRun - testsPass));
            
            if (testsPass == testsRun) {
                System.out.println("\nAll tests passed successfully!");
                System.out.println("JanusGraph InMemory Database is working correctly.");
                System.exit(0);
            } else {
                System.out.println("\nSome tests failed!");
                System.exit(1);
            }
            
        } catch (Exception e) {
            System.err.println("Test suite failed with exception: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void testStaticBufferOperations() throws Exception {
        System.out.println("\n--- Testing StaticBuffer Operations ---");
        
        // Test buffer creation
        StaticBuffer buf1 = makeStaticBuffer("hello");
        StaticBuffer buf2 = makeStaticBuffer("hello");
        StaticBuffer buf3 = makeStaticBuffer("world");
        
        assertEqual("StaticBuffer equality", buf1, buf2);
        assertNotEqual("StaticBuffer inequality", buf1, buf3);
        assertTrue("StaticBuffer length", buf1.length() == 5);
        assertTrue("StaticBuffer comparison", buf1.compareTo(buf3) < 0);
        
        System.out.println("StaticBuffer operations: PASSED");
    }

    private static void testEntryOperations() throws Exception {
        System.out.println("\n--- Testing Entry Operations ---");
        
        Entry entry1 = makeEntry("column1", "value1");
        Entry entry2 = makeEntry("column1", "value1");
        Entry entry3 = makeEntry("column2", "value2");
        
        assertEqual("Entry equality", entry1, entry2);
        assertNotEqual("Entry inequality", entry1, entry3);
        assertEqual("Entry column", entry1.getColumn(), makeStaticBuffer("column1"));
        assertEqual("Entry value", entry1.getValue(), makeStaticBuffer("value1"));
        
        System.out.println("Entry operations: PASSED");
    }

    private static void testInMemoryStoreManager() throws Exception {
        System.out.println("\n--- Testing InMemoryStoreManager ---");
        
        InMemoryStoreManager manager = new InMemoryStoreManager();
        
        // Test store creation
        KeyColumnValueStore store1 = manager.openDatabase("testStore1", StoreMetaData.EMPTY);
        KeyColumnValueStore store2 = manager.openDatabase("testStore2", StoreMetaData.EMPTY);
        
        assertEqual("Store1 name", store1.getName(), "testStore1");
        assertEqual("Store2 name", store2.getName(), "testStore2");
        
        // Test opening same store returns same instance
        KeyColumnValueStore store1Again = manager.openDatabase("testStore1", StoreMetaData.EMPTY);
        assertTrue("Same store instance", store1 == store1Again);
        
        // Test existence
        assertTrue("Manager exists", manager.exists());
        
        manager.close();
        System.out.println("InMemoryStoreManager: PASSED");
    }

    private static void testKeyColumnValueStore() throws Exception {
        System.out.println("\n--- Testing KeyColumnValueStore ---");
        
        InMemoryStoreManager manager = new InMemoryStoreManager();
        KeyColumnValueStore store = manager.openDatabase("testStore", StoreMetaData.EMPTY);
        StoreTransaction txh = manager.beginTransaction(StandardBaseTransactionConfig.of(
            TimestampProviders.MICRO, manager.getFeatures().getKeyConsistentTxConfig()));
        
        StaticBuffer key = makeStaticBuffer("testKey");
        
        // Test adding entries
        List<Entry> additions = Arrays.asList(
            makeEntry("col1", "val1"),
            makeEntry("col2", "val2"),
            makeEntry("col3", "val3")
        );
        
        store.mutate(key, additions, Collections.emptyList(), txh);
        
        // Test retrieving entries
        SliceQuery sliceQuery = new SliceQuery(
            makeStaticBuffer("col1"),
            makeStaticBuffer("col4")
        );
        
        EntryList result = store.getSlice(new KeySliceQuery(key, sliceQuery), txh);
        
        assertEqual("Retrieved entries count", result.size(), 3);
        assertEqual("First entry column", result.get(0).getColumn(), makeStaticBuffer("col1"));
        assertEqual("First entry value", result.get(0).getValue(), makeStaticBuffer("val1"));
        
        manager.close();
        System.out.println("KeyColumnValueStore: PASSED");
    }

    private static void testMutations() throws Exception {
        System.out.println("\n--- Testing Mutations ---");
        
        InMemoryStoreManager manager = new InMemoryStoreManager();
        KeyColumnValueStore store = manager.openDatabase("mutationTest", StoreMetaData.EMPTY);
        StoreTransaction txh = manager.beginTransaction(StandardBaseTransactionConfig.of(
            TimestampProviders.MICRO, manager.getFeatures().getKeyConsistentTxConfig()));
        
        StaticBuffer key = makeStaticBuffer("mutationKey");
        
        // Add initial data
        List<Entry> additions = Arrays.asList(
            makeEntry("col1", "val1"),
            makeEntry("col2", "val2"),
            makeEntry("col3", "val3")
        );
        store.mutate(key, additions, Collections.emptyList(), txh);
        
        // Test deletion
        List<StaticBuffer> deletions = Arrays.asList(makeStaticBuffer("col2"));
        store.mutate(key, Collections.emptyList(), deletions, txh);
        
        // Verify deletion
        SliceQuery sliceQuery = new SliceQuery(
            makeStaticBuffer(""),
            makeStaticBuffer("zzzz")
        );
        EntryList result = store.getSlice(new KeySliceQuery(key, sliceQuery), txh);
        
        assertEqual("Entries after deletion", result.size(), 2);
        
        // Verify col2 is gone
        boolean foundCol2 = false;
        for (int i = 0; i < result.size(); i++) {
            if (result.get(i).getColumn().equals(makeStaticBuffer("col2"))) {
                foundCol2 = true;
                break;
            }
        }
        assertFalse("Col2 should be deleted", foundCol2);
        
        manager.close();
        System.out.println("Mutations: PASSED");
    }

    private static void testSliceQueries() throws Exception {
        System.out.println("\n--- Testing Slice Queries ---");
        
        InMemoryStoreManager manager = new InMemoryStoreManager();
        KeyColumnValueStore store = manager.openDatabase("sliceTest", StoreMetaData.EMPTY);
        StoreTransaction txh = manager.beginTransaction(StandardBaseTransactionConfig.of(
            TimestampProviders.MICRO, manager.getFeatures().getKeyConsistentTxConfig()));
        
        StaticBuffer key = makeStaticBuffer("sliceKey");
        
        // Add ordered data
        List<Entry> additions = Arrays.asList(
            makeEntry("col1", "val1"),
            makeEntry("col2", "val2"),
            makeEntry("col3", "val3"),
            makeEntry("col4", "val4"),
            makeEntry("col5", "val5")
        );
        store.mutate(key, additions, Collections.emptyList(), txh);
        
        // Test range query
        SliceQuery sliceQuery = new SliceQuery(
            makeStaticBuffer("col2"),
            makeStaticBuffer("col4")
        );
        EntryList result = store.getSlice(new KeySliceQuery(key, sliceQuery), txh);
        
        assertEqual("Range query result size", result.size(), 2);
        assertEqual("First in range", result.get(0).getColumn(), makeStaticBuffer("col2"));
        assertEqual("Second in range", result.get(1).getColumn(), makeStaticBuffer("col3"));
        
        // Test multi-key slice
        List<StaticBuffer> keys = Arrays.asList(key, makeStaticBuffer("nonexistentKey"));
        Map<StaticBuffer, EntryList> multiResult = store.getSlice(keys, sliceQuery, txh);
        
        assertEqual("Multi-key result size", multiResult.size(), 2);
        assertEqual("Existing key result", multiResult.get(key).size(), 2);
        assertEqual("Non-existent key result", multiResult.get(makeStaticBuffer("nonexistentKey")).size(), 0);
        
        manager.close();
        System.out.println("Slice Queries: PASSED");
    }

    private static void testMultipleStores() throws Exception {
        System.out.println("\n--- Testing Multiple Stores ---");
        
        InMemoryStoreManager manager = new InMemoryStoreManager();
        KeyColumnValueStore store1 = manager.openDatabase("store1", StoreMetaData.EMPTY);
        KeyColumnValueStore store2 = manager.openDatabase("store2", StoreMetaData.EMPTY);
        StoreTransaction txh = manager.beginTransaction(StandardBaseTransactionConfig.of(
            TimestampProviders.MICRO, manager.getFeatures().getKeyConsistentTxConfig()));
        
        // Add different data to each store
        StaticBuffer key = makeStaticBuffer("commonKey");
        
        store1.mutate(key, Arrays.asList(makeEntry("store1col", "store1val")), Collections.emptyList(), txh);
        store2.mutate(key, Arrays.asList(makeEntry("store2col", "store2val")), Collections.emptyList(), txh);
        
        // Verify isolation
        SliceQuery sliceQuery = new SliceQuery(makeStaticBuffer(""), makeStaticBuffer("zzzz"));
        
        EntryList result1 = store1.getSlice(new KeySliceQuery(key, sliceQuery), txh);
        EntryList result2 = store2.getSlice(new KeySliceQuery(key, sliceQuery), txh);
        
        assertEqual("Store1 entries", result1.size(), 1);
        assertEqual("Store2 entries", result2.size(), 1);
        assertEqual("Store1 column", result1.get(0).getColumn(), makeStaticBuffer("store1col"));
        assertEqual("Store2 column", result2.get(0).getColumn(), makeStaticBuffer("store2col"));
        
        manager.close();
        System.out.println("Multiple Stores: PASSED");
    }

    private static void testComplexScenario() throws Exception {
        System.out.println("\n--- Testing Complex Scenario ---");
        
        InMemoryStoreManager manager = new InMemoryStoreManager();
        StoreTransaction txh = manager.beginTransaction(StandardBaseTransactionConfig.of(
            TimestampProviders.MICRO, manager.getFeatures().getKeyConsistentTxConfig()));
        
        // Use mutateMany to update multiple stores
        Map<String, Map<StaticBuffer, KCVMutation>> allMutations = new HashMap<>();
        Map<StaticBuffer, KCVMutation> store1Mutations = new HashMap<>();
        Map<StaticBuffer, KCVMutation> store2Mutations = new HashMap<>();
        
        allMutations.put("complexStore1", store1Mutations);
        allMutations.put("complexStore2", store2Mutations);
        
        // Add data to store1
        List<Entry> additions1 = Arrays.asList(
            makeEntry("user1:name", "Alice"),
            makeEntry("user1:age", "25"),
            makeEntry("user1:city", "New York")
        );
        store1Mutations.put(makeStaticBuffer("user1"), new KCVMutation(additions1, Collections.emptyList()));
        
        // Add data to store2
        List<Entry> additions2 = Arrays.asList(
            makeEntry("product1:name", "Laptop"),
            makeEntry("product1:price", "999.99")
        );
        store2Mutations.put(makeStaticBuffer("product1"), new KCVMutation(additions2, Collections.emptyList()));
        
        // Open stores first (required for mutateMany)
        KeyColumnValueStore store1 = manager.openDatabase("complexStore1", StoreMetaData.EMPTY);
        KeyColumnValueStore store2 = manager.openDatabase("complexStore2", StoreMetaData.EMPTY);
        
        // Execute batch mutation
        manager.mutateMany(allMutations, txh);
        
        SliceQuery allColumns = new SliceQuery(makeStaticBuffer(""), makeStaticBuffer("zzzz"));
        
        EntryList user1Data = store1.getSlice(new KeySliceQuery(makeStaticBuffer("user1"), allColumns), txh);
        EntryList product1Data = store2.getSlice(new KeySliceQuery(makeStaticBuffer("product1"), allColumns), txh);
        
        assertEqual("User data count", user1Data.size(), 3);
        assertEqual("Product data count", product1Data.size(), 2);
        
        manager.close();
        System.out.println("Complex Scenario: PASSED");
    }

    private static void testSnapshotFunctionality() throws Exception {
        System.out.println("\n--- Testing Snapshot Functionality ---");
        
        InMemoryStoreManager manager = new InMemoryStoreManager();
        KeyColumnValueStore store = manager.openDatabase("snapshotTest", StoreMetaData.EMPTY);
        StoreTransaction txh = manager.beginTransaction(StandardBaseTransactionConfig.of(
            TimestampProviders.MICRO, manager.getFeatures().getKeyConsistentTxConfig()));
        
        // Add some data
        StaticBuffer key = makeStaticBuffer("snapshotKey");
        List<Entry> additions = Arrays.asList(
            makeEntry("data1", "value1"),
            makeEntry("data2", "value2")
        );
        store.mutate(key, additions, Collections.emptyList(), txh);
        
        // Test clearStorage
        assertTrue("Store has data before clear", manager.exists());
        manager.clearStorage();
        assertFalse("Store empty after clear", manager.exists());
        
        // Re-add data
        store = manager.openDatabase("snapshotTest", StoreMetaData.EMPTY);
        store.mutate(key, additions, Collections.emptyList(), txh);
        
        manager.close();
        System.out.println("Snapshot Functionality: PASSED");
    }

    // Helper methods
    private static StaticBuffer makeStaticBuffer(String value) {
        return StaticArrayBuffer.of(value.getBytes());
    }

    private static Entry makeEntry(String column, String value) {
        return StaticArrayEntry.of(makeStaticBuffer(column), makeStaticBuffer(value));
    }

    private static void assertEqual(String message, Object expected, Object actual) {
        testsRun++;
        if ((expected == null && actual == null) || (expected != null && expected.equals(actual))) {
            testsPass++;
            System.out.println("  ✓ " + message);
        } else {
            System.err.println("  ✗ " + message + " - Expected: " + expected + ", Actual: " + actual);
        }
    }

    private static void assertNotEqual(String message, Object expected, Object actual) {
        testsRun++;
        if ((expected == null && actual != null) || (expected != null && !expected.equals(actual))) {
            testsPass++;
            System.out.println("  ✓ " + message);
        } else {
            System.err.println("  ✗ " + message + " - Values should not be equal: " + expected);
        }
    }

    private static void assertTrue(String message, boolean condition) {
        testsRun++;
        if (condition) {
            testsPass++;
            System.out.println("  ✓ " + message);
        } else {
            System.err.println("  ✗ " + message + " - Expected true, got false");
        }
    }

    private static void assertFalse(String message, boolean condition) {
        testsRun++;
        if (!condition) {
            testsPass++;
            System.out.println("  ✓ " + message);
        } else {
            System.err.println("  ✗ " + message + " - Expected false, got true");
        }
    }
}