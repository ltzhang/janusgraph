import org.janusgraph.diskstorage.BackendException;
import org.janusgraph.diskstorage.Entry;
import org.janusgraph.diskstorage.EntryList;
import org.janusgraph.diskstorage.StaticBuffer;
import org.janusgraph.diskstorage.StoreMetaData;
import org.janusgraph.diskstorage.inmemory.InMemoryStoreManager;
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
import java.util.List;
import java.util.ArrayList;

/**
 * Equivalence test that compares Java and C++ implementations of the InMemory Database.
 * Tests the same operations on both implementations and verifies they produce identical results.
 */
public class EquivalenceTest {

    private static int testsRun = 0;
    private static int testsPass = 0;

    public static void main(String[] args) {
        System.out.println("Starting Java vs C++ InMemory Database Equivalence Tests...");
        
        try {
            testBasicOperations();
            testMultipleEntries();
            testSliceQueries();
            testMutations();
            testMultipleStores();
            testComplexScenario();
            
            System.out.println("\n=== Equivalence Test Results ===");
            System.out.println("Tests Run: " + testsRun);
            System.out.println("Tests Passed: " + testsPass);
            System.out.println("Tests Failed: " + (testsRun - testsPass));
            
            if (testsPass == testsRun) {
                System.out.println("\nAll equivalence tests passed!");
                System.out.println("Java and C++ implementations are equivalent.");
                System.exit(0);
            } else {
                System.out.println("\nSome equivalence tests failed!");
                System.exit(1);
            }
            
        } catch (Exception e) {
            System.err.println("Equivalence test suite failed with exception: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void testBasicOperations() throws Exception {
        System.out.println("\n--- Testing Basic Operations ---");
        
        // Create both implementations
        InMemoryStoreManager javaManager = new InMemoryStoreManager();
        NativeInMemoryDB cppDB = new NativeInMemoryDB();
        
        try {
            // Test store creation and existence
            KeyColumnValueStore javaStore = javaManager.openDatabase("testStore", StoreMetaData.EMPTY);
            NativeInMemoryDB.NativeStore cppStore = cppDB.openStore("testStore");
            
            assertEqual("Initial existence check", javaManager.exists(), cppDB.exists());
            
            // Test basic put operation
            StoreTransaction javaTx = javaManager.beginTransaction(StandardBaseTransactionConfig.of(
                TimestampProviders.MICRO, javaManager.getFeatures().getKeyConsistentTxConfig()));
            
            StaticBuffer key = makeStaticBuffer("key1");
            List<Entry> additions = Arrays.asList(makeEntry("col1", "val1"));
            javaStore.mutate(key, additions, Collections.emptyList(), javaTx);
            
            List<NativeInMemoryDB.Entry> cppAdditions = Arrays.asList(new NativeInMemoryDB.Entry("col1", "val1"));
            cppStore.mutate("key1", cppAdditions, Collections.emptyList());
            
            // Verify both have the entry
            SliceQuery slice = new SliceQuery(makeStaticBuffer(""), makeStaticBuffer("zzz"));
            EntryList javaResult = javaStore.getSlice(new KeySliceQuery(key, slice), javaTx);
            List<NativeInMemoryDB.Entry> cppResult = cppStore.getSlice("key1", "", "zzz");
            
            assertEqual("Basic put result count", javaResult.size(), cppResult.size());
            if (javaResult.size() > 0) {
                String javaCol = bytesToString(javaResult.get(0).getColumn());
                String javaVal = bytesToString(javaResult.get(0).getValue());
                assertEqual("Basic put column", javaCol, cppResult.get(0).column);
                assertEqual("Basic put value", javaVal, cppResult.get(0).value);
            }
            
        } finally {
            javaManager.close();
            cppDB.close();
        }
        
        System.out.println("Basic Operations: PASSED");
    }

    private static void testMultipleEntries() throws Exception {
        System.out.println("\n--- Testing Multiple Entries ---");
        
        InMemoryStoreManager javaManager = new InMemoryStoreManager();
        NativeInMemoryDB cppDB = new NativeInMemoryDB();
        
        try {
            KeyColumnValueStore javaStore = javaManager.openDatabase("multiStore", StoreMetaData.EMPTY);
            NativeInMemoryDB.NativeStore cppStore = cppDB.openStore("multiStore");
            
            StoreTransaction javaTx = javaManager.beginTransaction(StandardBaseTransactionConfig.of(
                TimestampProviders.MICRO, javaManager.getFeatures().getKeyConsistentTxConfig()));
            
            // Add multiple entries
            StaticBuffer key = makeStaticBuffer("multiKey");
            List<Entry> javaAdditions = Arrays.asList(
                makeEntry("col1", "val1"),
                makeEntry("col2", "val2"),
                makeEntry("col3", "val3")
            );
            javaStore.mutate(key, javaAdditions, Collections.emptyList(), javaTx);
            
            List<NativeInMemoryDB.Entry> cppAdditions = Arrays.asList(
                new NativeInMemoryDB.Entry("col1", "val1"),
                new NativeInMemoryDB.Entry("col2", "val2"),
                new NativeInMemoryDB.Entry("col3", "val3")
            );
            cppStore.mutate("multiKey", cppAdditions, Collections.emptyList());
            
            // Verify results
            SliceQuery slice = new SliceQuery(makeStaticBuffer(""), makeStaticBuffer("zzz"));
            EntryList javaResult = javaStore.getSlice(new KeySliceQuery(key, slice), javaTx);
            List<NativeInMemoryDB.Entry> cppResult = cppStore.getSlice("multiKey", "", "zzz");
            
            assertEqual("Multiple entries count", javaResult.size(), cppResult.size());
            
            // Compare each entry (assuming both are sorted)
            for (int i = 0; i < Math.min(javaResult.size(), cppResult.size()); i++) {
                String javaCol = bytesToString(javaResult.get(i).getColumn());
                String javaVal = bytesToString(javaResult.get(i).getValue());
                assertEqual("Multi entry " + i + " column", javaCol, cppResult.get(i).column);
                assertEqual("Multi entry " + i + " value", javaVal, cppResult.get(i).value);
            }
            
        } finally {
            javaManager.close();
            cppDB.close();
        }
        
        System.out.println("Multiple Entries: PASSED");
    }

    private static void testSliceQueries() throws Exception {
        System.out.println("\n--- Testing Slice Queries ---");
        
        InMemoryStoreManager javaManager = new InMemoryStoreManager();
        NativeInMemoryDB cppDB = new NativeInMemoryDB();
        
        try {
            KeyColumnValueStore javaStore = javaManager.openDatabase("sliceStore", StoreMetaData.EMPTY);
            NativeInMemoryDB.NativeStore cppStore = cppDB.openStore("sliceStore");
            
            StoreTransaction javaTx = javaManager.beginTransaction(StandardBaseTransactionConfig.of(
                TimestampProviders.MICRO, javaManager.getFeatures().getKeyConsistentTxConfig()));
            
            // Add test data
            StaticBuffer key = makeStaticBuffer("sliceKey");
            List<Entry> javaAdditions = Arrays.asList(
                makeEntry("col1", "val1"),
                makeEntry("col2", "val2"),
                makeEntry("col3", "val3"),
                makeEntry("col4", "val4"),
                makeEntry("col5", "val5")
            );
            javaStore.mutate(key, javaAdditions, Collections.emptyList(), javaTx);
            
            List<NativeInMemoryDB.Entry> cppAdditions = Arrays.asList(
                new NativeInMemoryDB.Entry("col1", "val1"),
                new NativeInMemoryDB.Entry("col2", "val2"),
                new NativeInMemoryDB.Entry("col3", "val3"),
                new NativeInMemoryDB.Entry("col4", "val4"),
                new NativeInMemoryDB.Entry("col5", "val5")
            );
            cppStore.mutate("sliceKey", cppAdditions, Collections.emptyList());
            
            // Test slice query (col2 to col4, exclusive)
            SliceQuery slice = new SliceQuery(makeStaticBuffer("col2"), makeStaticBuffer("col4"));
            EntryList javaResult = javaStore.getSlice(new KeySliceQuery(key, slice), javaTx);
            List<NativeInMemoryDB.Entry> cppResult = cppStore.getSlice("sliceKey", "col2", "col4");
            
            assertEqual("Slice query count", javaResult.size(), cppResult.size());
            
            // Verify slice results
            for (int i = 0; i < Math.min(javaResult.size(), cppResult.size()); i++) {
                String javaCol = bytesToString(javaResult.get(i).getColumn());
                String javaVal = bytesToString(javaResult.get(i).getValue());
                assertEqual("Slice entry " + i + " column", javaCol, cppResult.get(i).column);
                assertEqual("Slice entry " + i + " value", javaVal, cppResult.get(i).value);
            }
            
        } finally {
            javaManager.close();
            cppDB.close();
        }
        
        System.out.println("Slice Queries: PASSED");
    }

    private static void testMutations() throws Exception {
        System.out.println("\n--- Testing Mutations ---");
        
        InMemoryStoreManager javaManager = new InMemoryStoreManager();
        NativeInMemoryDB cppDB = new NativeInMemoryDB();
        
        try {
            KeyColumnValueStore javaStore = javaManager.openDatabase("mutStore", StoreMetaData.EMPTY);
            NativeInMemoryDB.NativeStore cppStore = cppDB.openStore("mutStore");
            
            StoreTransaction javaTx = javaManager.beginTransaction(StandardBaseTransactionConfig.of(
                TimestampProviders.MICRO, javaManager.getFeatures().getKeyConsistentTxConfig()));
            
            StaticBuffer key = makeStaticBuffer("mutKey");
            
            // Add initial data
            List<Entry> javaAdditions = Arrays.asList(
                makeEntry("col1", "val1"),
                makeEntry("col2", "val2"),
                makeEntry("col3", "val3")
            );
            javaStore.mutate(key, javaAdditions, Collections.emptyList(), javaTx);
            
            List<NativeInMemoryDB.Entry> cppAdditions = Arrays.asList(
                new NativeInMemoryDB.Entry("col1", "val1"),
                new NativeInMemoryDB.Entry("col2", "val2"),
                new NativeInMemoryDB.Entry("col3", "val3")
            );
            cppStore.mutate("mutKey", cppAdditions, Collections.emptyList());
            
            // Delete col2 from both
            List<StaticBuffer> javaDeletions = Arrays.asList(makeStaticBuffer("col2"));
            javaStore.mutate(key, Collections.emptyList(), javaDeletions, javaTx);
            
            List<String> cppDeletions = Arrays.asList("col2");
            cppStore.mutate("mutKey", Collections.emptyList(), cppDeletions);
            
            // Verify deletion results
            SliceQuery slice = new SliceQuery(makeStaticBuffer(""), makeStaticBuffer("zzz"));
            EntryList javaResult = javaStore.getSlice(new KeySliceQuery(key, slice), javaTx);
            List<NativeInMemoryDB.Entry> cppResult = cppStore.getSlice("mutKey", "", "zzz");
            
            assertEqual("After deletion count", javaResult.size(), cppResult.size());
            
            // Verify col2 is gone from both
            boolean javaHasCol2 = false, cppHasCol2 = false;
            for (int i = 0; i < javaResult.size(); i++) {
                if ("col2".equals(bytesToString(javaResult.get(i).getColumn()))) {
                    javaHasCol2 = true;
                    break;
                }
            }
            for (NativeInMemoryDB.Entry entry : cppResult) {
                if ("col2".equals(entry.column)) {
                    cppHasCol2 = true;
                    break;
                }
            }
            
            assertEqual("Java col2 deleted", false, javaHasCol2);
            assertEqual("C++ col2 deleted", false, cppHasCol2);
            
        } finally {
            javaManager.close();
            cppDB.close();
        }
        
        System.out.println("Mutations: PASSED");
    }

    private static void testMultipleStores() throws Exception {
        System.out.println("\n--- Testing Multiple Stores ---");
        
        InMemoryStoreManager javaManager = new InMemoryStoreManager();
        NativeInMemoryDB cppDB = new NativeInMemoryDB();
        
        try {
            KeyColumnValueStore javaStore1 = javaManager.openDatabase("store1", StoreMetaData.EMPTY);
            KeyColumnValueStore javaStore2 = javaManager.openDatabase("store2", StoreMetaData.EMPTY);
            NativeInMemoryDB.NativeStore cppStore1 = cppDB.openStore("store1");
            NativeInMemoryDB.NativeStore cppStore2 = cppDB.openStore("store2");
            
            StoreTransaction javaTx = javaManager.beginTransaction(StandardBaseTransactionConfig.of(
                TimestampProviders.MICRO, javaManager.getFeatures().getKeyConsistentTxConfig()));
            
            // Add different data to each store
            StaticBuffer key = makeStaticBuffer("commonKey");
            javaStore1.mutate(key, Arrays.asList(makeEntry("store1col", "store1val")), Collections.emptyList(), javaTx);
            javaStore2.mutate(key, Arrays.asList(makeEntry("store2col", "store2val")), Collections.emptyList(), javaTx);
            
            cppStore1.mutate("commonKey", Arrays.asList(new NativeInMemoryDB.Entry("store1col", "store1val")), Collections.emptyList());
            cppStore2.mutate("commonKey", Arrays.asList(new NativeInMemoryDB.Entry("store2col", "store2val")), Collections.emptyList());
            
            // Verify store counts
            assertEqual("Store count", 2, cppDB.getStoreCount());
            
            // Verify isolation
            SliceQuery slice = new SliceQuery(makeStaticBuffer(""), makeStaticBuffer("zzz"));
            EntryList javaResult1 = javaStore1.getSlice(new KeySliceQuery(key, slice), javaTx);
            EntryList javaResult2 = javaStore2.getSlice(new KeySliceQuery(key, slice), javaTx);
            List<NativeInMemoryDB.Entry> cppResult1 = cppStore1.getSlice("commonKey", "", "zzz");
            List<NativeInMemoryDB.Entry> cppResult2 = cppStore2.getSlice("commonKey", "", "zzz");
            
            assertEqual("Store1 entry count", javaResult1.size(), cppResult1.size());
            assertEqual("Store2 entry count", javaResult2.size(), cppResult2.size());
            
            if (javaResult1.size() > 0 && cppResult1.size() > 0) {
                assertEqual("Store1 column", bytesToString(javaResult1.get(0).getColumn()), cppResult1.get(0).column);
            }
            if (javaResult2.size() > 0 && cppResult2.size() > 0) {
                assertEqual("Store2 column", bytesToString(javaResult2.get(0).getColumn()), cppResult2.get(0).column);
            }
            
        } finally {
            javaManager.close();
            cppDB.close();
        }
        
        System.out.println("Multiple Stores: PASSED");
    }

    private static void testComplexScenario() throws Exception {
        System.out.println("\n--- Testing Complex Scenario ---");
        
        InMemoryStoreManager javaManager = new InMemoryStoreManager();
        NativeInMemoryDB cppDB = new NativeInMemoryDB();
        
        try {
            KeyColumnValueStore javaStore = javaManager.openDatabase("complexStore", StoreMetaData.EMPTY);
            NativeInMemoryDB.NativeStore cppStore = cppDB.openStore("complexStore");
            
            StoreTransaction javaTx = javaManager.beginTransaction(StandardBaseTransactionConfig.of(
                TimestampProviders.MICRO, javaManager.getFeatures().getKeyConsistentTxConfig()));
            
            // Add multiple keys with multiple columns
            for (int i = 0; i < 5; i++) {
                StaticBuffer key = makeStaticBuffer("key" + i);
                List<Entry> javaAdditions = new ArrayList<>();
                List<NativeInMemoryDB.Entry> cppAdditions = new ArrayList<>();
                
                for (int j = 0; j < 3; j++) {
                    String col = "col" + j;
                    String val = "value" + i + "_" + j;
                    javaAdditions.add(makeEntry(col, val));
                    cppAdditions.add(new NativeInMemoryDB.Entry(col, val));
                }
                
                javaStore.mutate(key, javaAdditions, Collections.emptyList(), javaTx);
                cppStore.mutate("key" + i, cppAdditions, Collections.emptyList());
            }
            
            // Test queries on different keys
            for (int i = 0; i < 5; i++) {
                StaticBuffer key = makeStaticBuffer("key" + i);
                SliceQuery slice = new SliceQuery(makeStaticBuffer("col1"), makeStaticBuffer("col3"));
                
                EntryList javaResult = javaStore.getSlice(new KeySliceQuery(key, slice), javaTx);
                List<NativeInMemoryDB.Entry> cppResult = cppStore.getSlice("key" + i, "col1", "col3");
                
                assertEqual("Complex key" + i + " count", javaResult.size(), cppResult.size());
            }
            
            // Test storage status
            assertEqual("Complex final existence", javaManager.exists(), cppDB.exists());
            
        } finally {
            javaManager.close();
            cppDB.close();
        }
        
        System.out.println("Complex Scenario: PASSED");
    }

    // Helper methods
    private static StaticBuffer makeStaticBuffer(String value) {
        return StaticArrayBuffer.of(value.getBytes());
    }

    private static Entry makeEntry(String column, String value) {
        return StaticArrayEntry.of(makeStaticBuffer(column), makeStaticBuffer(value));
    }

    private static String bytesToString(StaticBuffer buffer) {
        return new String(buffer.as(StaticBuffer.ARRAY_FACTORY));
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
}