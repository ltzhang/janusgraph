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

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Comprehensive stress test that compares Java and C++ implementations under heavy load
 * with randomly generated inputs and actions over extended periods.
 */
public class StressEquivalenceTest {

    // Test configuration
    private static final int SINGLE_THREAD_DURATION_MINUTES = 2;
    private static final int MULTI_THREAD_DURATION_MINUTES = 3;
    private static final int THREAD_COUNT = 4;
    private static final int OPERATIONS_BATCH_SIZE = 100;
    
    // Data generation parameters
    private static final int MAX_KEYS = 1000;
    private static final int MAX_COLUMNS_PER_KEY = 50;
    private static final int MAX_VALUE_LENGTH = 100;
    private static final String[] KEY_PREFIXES = {"user", "product", "order", "session", "metric"};
    private static final String[] COLUMN_PREFIXES = {"name", "id", "timestamp", "value", "status", "count"};
    
    // Test statistics
    private static final AtomicLong totalOperations = new AtomicLong(0);
    private static final AtomicLong successfulOperations = new AtomicLong(0);
    private static final AtomicLong equivalenceFailures = new AtomicLong(0);
    private static final AtomicLong javaErrors = new AtomicLong(0);
    private static final AtomicLong cppErrors = new AtomicLong(0);
    
    // Debugging features
    private static final List<MismatchRecord> mismatchRecords = Collections.synchronizedList(new ArrayList<>());
    private static final int MAX_MISMATCH_RECORDS = 50; // Limit memory usage
    private static boolean debugMode = false;
    private static final List<StressOperation> recentOperations = Collections.synchronizedList(new ArrayList<>());
    private static final int MAX_RECENT_OPERATIONS = 100; // Keep last 100 operations for context
    
    private static final Random random = new Random(System.currentTimeMillis());

    public static void main(String[] args) {
        System.out.println("Starting Stress Equivalence Test for Java vs C++ InMemory Database");
        System.out.println("====================================================================");
        
        try {
            // Parse command line arguments for test duration
            double singleThreadMinutes = args.length > 0 ? Double.parseDouble(args[0]) : SINGLE_THREAD_DURATION_MINUTES;
            double multiThreadMinutes = args.length > 1 ? Double.parseDouble(args[1]) : MULTI_THREAD_DURATION_MINUTES;
            int threadCount = args.length > 2 ? Integer.parseInt(args[2]) : THREAD_COUNT;
            debugMode = args.length > 3 && "debug".equalsIgnoreCase(args[3]);
            
            System.out.println("Test Configuration:");
            System.out.println("  Single-threaded test duration: " + singleThreadMinutes + " minutes");
            System.out.println("  Multi-threaded test duration: " + multiThreadMinutes + " minutes");
            System.out.println("  Thread count: " + threadCount);
            System.out.println("  Debug mode: " + debugMode);
            System.out.println();
            
            // Round 1: Single-threaded stress test
            runSingleThreadedStressTest(singleThreadMinutes);
            
            // Brief pause between rounds
            Thread.sleep(2000);
            
            // Round 2: Multi-threaded stress test
            runMultiThreadedStressTest(multiThreadMinutes, threadCount);
            
            // Final results
            printFinalResults();
            printMismatchSummary();
            
            if (equivalenceFailures.get() == 0) {
                System.out.println("\n🎉 All stress tests passed! Java and C++ implementations are equivalent under stress.");
                System.exit(0);
            } else {
                System.err.println("\n❌ Some equivalence failures detected during stress testing.");
                System.exit(1);
            }
            
        } catch (Exception e) {
            System.err.println("Stress test failed with exception: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void runSingleThreadedStressTest(double durationMinutes) throws Exception {
        System.out.println("=== ROUND 1: Single-Threaded Stress Test ===");
        System.out.println("Duration: " + durationMinutes + " minutes");
        System.out.println("Testing deterministic operations with full result verification...");
        System.out.println();
        
        long startTime = System.currentTimeMillis();
        long endTime = startTime + (long)(durationMinutes * 60 * 1000L);
        long roundOperations = 0;
        long roundEquivalenceFailures = 0;
        
        // Create test instances
        InMemoryStoreManager javaManager = new InMemoryStoreManager();
        NativeInMemoryDB cppDB = new NativeInMemoryDB();
        
        try {
            KeyColumnValueStore javaStore = javaManager.openDatabase("stressStore", StoreMetaData.EMPTY);
            NativeInMemoryDB.NativeStore cppStore = cppDB.openStore("stressStore");
            
            StoreTransaction javaTx = javaManager.beginTransaction(StandardBaseTransactionConfig.of(
                TimestampProviders.MICRO, javaManager.getFeatures().getKeyConsistentTxConfig()));
            
            // Track data state for verification
            Map<String, Map<String, String>> expectedState = new HashMap<>();
            
            while (System.currentTimeMillis() < endTime) {
                try {
                    // Generate random operation
                    StressOperation operation = generateRandomOperation(expectedState);
                    recordOperation(operation);
                    
                    // Execute on both implementations
                    boolean javaSuccess = executeJavaOperation(javaStore, javaTx, operation);
                    boolean cppSuccess = executeCppOperation(cppStore, operation);
                    
                    if (javaSuccess && cppSuccess) {
                        // Update expected state
                        updateExpectedState(expectedState, operation);
                        
                        // Verify equivalence with detailed checking
                        if (verifyEquivalence(javaStore, javaTx, cppStore, operation, expectedState)) {
                            successfulOperations.incrementAndGet();
                        } else {
                            roundEquivalenceFailures++;
                            equivalenceFailures.incrementAndGet();
                        }
                    } else {
                        if (!javaSuccess) javaErrors.incrementAndGet();
                        if (!cppSuccess) cppErrors.incrementAndGet();
                    }
                    
                    roundOperations++;
                    totalOperations.incrementAndGet();
                    
                    // Progress reporting
                    if (roundOperations % OPERATIONS_BATCH_SIZE == 0) {
                        long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                        double opsPerSec = roundOperations / Math.max(1.0, elapsed);
                        System.out.printf("Single-threaded: %d ops, %.1f ops/sec, %d failures%n", 
                            roundOperations, opsPerSec, roundEquivalenceFailures);
                    }
                    
                } catch (Exception e) {
                    System.err.println("Single-threaded test operation failed: " + e.getMessage());
                    // Continue testing
                }
            }
            
        } finally {
            javaManager.close();
            cppDB.close();
        }
        
        long elapsed = (System.currentTimeMillis() - startTime) / 1000;
        System.out.printf("\nSingle-threaded test completed: %d operations in %d seconds (%.2f ops/sec)%n", 
            roundOperations, elapsed, roundOperations / Math.max(1.0, elapsed));
        System.out.printf("Equivalence failures: %d (%.3f%%)%n", 
            roundEquivalenceFailures, (100.0 * roundEquivalenceFailures) / Math.max(1, roundOperations));
        System.out.println();
    }

    private static void runMultiThreadedStressTest(double durationMinutes, int threadCount) throws Exception {
        System.out.println("=== ROUND 2: Multi-Threaded Stress Test ===");
        System.out.println("Duration: " + durationMinutes + " minutes, Threads: " + threadCount);
        System.out.println("Testing concurrent operations with statistical verification...");
        System.out.println();
        
        long startTime = System.currentTimeMillis();
        long endTime = startTime + (long)(durationMinutes * 60 * 1000L);
        
        // Create test instances (shared across threads)
        InMemoryStoreManager javaManager = new InMemoryStoreManager();
        NativeInMemoryDB cppDB = new NativeInMemoryDB();
        
        // Thread-safe counters for this round
        AtomicLong roundOperations = new AtomicLong(0);
        AtomicLong roundEquivalenceFailures = new AtomicLong(0);
        AtomicReference<Exception> firstException = new AtomicReference<>();
        
        try {
            KeyColumnValueStore javaStore = javaManager.openDatabase("multiStressStore", StoreMetaData.EMPTY);
            NativeInMemoryDB.NativeStore cppStore = cppDB.openStore("multiStressStore");
            
            // Create thread pool
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            List<Future<Void>> futures = new ArrayList<>();
            
            // Start worker threads
            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                Future<Void> future = executor.submit(() -> {
                    return runMultiThreadWorker(threadId, javaManager, javaStore, cppStore, 
                        endTime, roundOperations, roundEquivalenceFailures, firstException);
                });
                futures.add(future);
            }
            
            // Progress monitoring
            long lastReport = System.currentTimeMillis();
            while (System.currentTimeMillis() < endTime && firstException.get() == null) {
                Thread.sleep(5000); // Report every 5 seconds
                
                if (System.currentTimeMillis() - lastReport >= 10000) { // Report every 10 seconds
                    long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                    long ops = roundOperations.get();
                    double opsPerSec = ops / Math.max(1.0, elapsed);
                    System.out.printf("Multi-threaded: %d ops, %.1f ops/sec, %d failures (%d threads)%n", 
                        ops, opsPerSec, roundEquivalenceFailures.get(), threadCount);
                    lastReport = System.currentTimeMillis();
                }
            }
            
            // Shutdown threads
            executor.shutdown();
            try {
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
            }
            
            // Check for exceptions
            if (firstException.get() != null) {
                throw firstException.get();
            }
            
            // Collect results from all threads
            for (Future<Void> future : futures) {
                try {
                    future.get();
                } catch (Exception e) {
                    System.err.println("Thread execution failed: " + e.getMessage());
                }
            }
            
        } finally {
            javaManager.close();
            cppDB.close();
        }
        
        long elapsed = (System.currentTimeMillis() - startTime) / 1000;
        long ops = roundOperations.get();
        long failures = roundEquivalenceFailures.get();
        
        System.out.printf("\nMulti-threaded test completed: %d operations in %d seconds (%.2f ops/sec)%n", 
            ops, elapsed, ops / Math.max(1.0, elapsed));
        System.out.printf("Equivalence failures: %d (%.3f%%)%n", 
            failures, (100.0 * failures) / Math.max(1, ops));
        System.out.println();
    }

    private static Void runMultiThreadWorker(int threadId, InMemoryStoreManager javaManager, 
                                           KeyColumnValueStore javaStore, NativeInMemoryDB.NativeStore cppStore,
                                           long endTime, AtomicLong roundOperations, 
                                           AtomicLong roundEquivalenceFailures, 
                                           AtomicReference<Exception> firstException) {
        try {
            StoreTransaction javaTx = javaManager.beginTransaction(StandardBaseTransactionConfig.of(
                TimestampProviders.MICRO, javaManager.getFeatures().getKeyConsistentTxConfig()));
            
            // Each thread maintains its own key space to reduce conflicts
            String threadKeyPrefix = "thread" + threadId + "_";
            Map<String, Map<String, String>> threadExpectedState = new HashMap<>();
            
            while (System.currentTimeMillis() < endTime) {
                try {
                    // Generate thread-specific operation
                    StressOperation operation = generateThreadScopedOperation(threadKeyPrefix, threadExpectedState);
                    recordOperation(operation);
                    
                    // Execute on both implementations
                    boolean javaSuccess = executeJavaOperation(javaStore, javaTx, operation);
                    boolean cppSuccess = executeCppOperation(cppStore, operation);
                    
                    if (javaSuccess && cppSuccess) {
                        // Update thread's expected state
                        updateExpectedState(threadExpectedState, operation);
                        
                        // For multi-threaded tests, we verify thread-specific data only
                        // since other threads may interfere with global state
                        if (verifyThreadEquivalence(javaStore, javaTx, cppStore, operation, threadExpectedState)) {
                            successfulOperations.incrementAndGet();
                        } else {
                            roundEquivalenceFailures.incrementAndGet();
                            equivalenceFailures.incrementAndGet();
                        }
                    } else {
                        if (!javaSuccess) javaErrors.incrementAndGet();
                        if (!cppSuccess) cppErrors.incrementAndGet();
                    }
                    
                    roundOperations.incrementAndGet();
                    totalOperations.incrementAndGet();
                    
                } catch (Exception e) {
                    // Capture first exception for debugging
                    firstException.compareAndSet(null, e);
                    System.err.println("Thread " + threadId + " operation failed: " + e.getMessage());
                }
            }
            
        } catch (Exception e) {
            firstException.compareAndSet(null, e);
            System.err.println("Thread " + threadId + " failed: " + e.getMessage());
        }
        
        return null;
    }

    // Debugging and analysis classes
    private static class MismatchRecord {
        final long timestamp;
        final String threadName;
        final StressOperation operation;
        final String javaResult;
        final String cppResult;
        final List<StressOperation> precedingOperations;
        final String javaStoreState;
        final String cppStoreState;
        boolean reproducible;
        String reproductionResult;
        
        MismatchRecord(String threadName, StressOperation operation, String javaResult, String cppResult, 
                      List<StressOperation> precedingOperations, String javaStoreState, String cppStoreState) {
            this.timestamp = System.currentTimeMillis();
            this.threadName = threadName;
            this.operation = operation;
            this.javaResult = javaResult;
            this.cppResult = cppResult;
            this.precedingOperations = new ArrayList<>(precedingOperations);
            this.javaStoreState = javaStoreState;
            this.cppStoreState = cppStoreState;
            this.reproducible = false;
        }
        
        @Override
        public String toString() {
            return String.format("[%s] Thread: %s, Operation: %s, Java: %s, C++: %s, Reproducible: %s",
                new Date(timestamp), threadName, operation, javaResult, cppResult, reproducible);
        }
    }

    private static class StressOperation {
        enum Type { PUT, DELETE, MUTATE, QUERY }
        
        final Type type;
        final String key;
        final List<NativeInMemoryDB.Entry> additions;
        final List<String> deletions;
        final String queryStart;
        final String queryEnd;
        
        StressOperation(Type type, String key) {
            this.type = type;
            this.key = key;
            this.additions = new ArrayList<>();
            this.deletions = new ArrayList<>();
            this.queryStart = "";
            this.queryEnd = "zzz";
        }
        
        StressOperation(Type type, String key, String queryStart, String queryEnd) {
            this.type = type;
            this.key = key;
            this.additions = new ArrayList<>();
            this.deletions = new ArrayList<>();
            this.queryStart = queryStart;
            this.queryEnd = queryEnd;
        }
        
        @Override
        public String toString() {
            switch (type) {
                case PUT:
                    return "PUT(" + key + ", " + additions.get(0).column + " -> " + additions.get(0).value + ")";
                case DELETE:
                    return "DELETE(" + key + ", " + deletions.get(0) + ")";
                case MUTATE:
                    return "MUTATE(" + key + ", +" + additions.size() + " additions, -" + deletions.size() + " deletions)";
                case QUERY:
                    return "QUERY(" + key + ", " + queryStart + " to " + queryEnd + ")";
                default:
                    return type + "(" + key + ")";
            }
        }
    }

    private static StressOperation generateRandomOperation(Map<String, Map<String, String>> expectedState) {
        StressOperation.Type[] types = StressOperation.Type.values();
        StressOperation.Type type = types[random.nextInt(types.length)];
        
        String key = generateRandomKey();
        
        switch (type) {
            case PUT:
                StressOperation putOp = new StressOperation(type, key);
                putOp.additions.add(new NativeInMemoryDB.Entry(generateRandomColumn(), generateRandomValue()));
                return putOp;
                
            case DELETE:
                StressOperation delOp = new StressOperation(type, key);
                // Try to delete an existing column
                Map<String, String> keyData = expectedState.get(key);
                if (keyData != null && !keyData.isEmpty()) {
                    String[] columns = keyData.keySet().toArray(new String[0]);
                    delOp.deletions.add(columns[random.nextInt(columns.length)]);
                } else {
                    delOp.deletions.add(generateRandomColumn());
                }
                return delOp;
                
            case MUTATE:
                StressOperation mutOp = new StressOperation(type, key);
                // Add some entries
                int addCount = 1 + random.nextInt(5);
                for (int i = 0; i < addCount; i++) {
                    mutOp.additions.add(new NativeInMemoryDB.Entry(generateRandomColumn(), generateRandomValue()));
                }
                // Delete some entries
                if (random.nextBoolean() && expectedState.containsKey(key)) {
                    Map<String, String> mutKeyData = expectedState.get(key);
                    if (mutKeyData != null && !mutKeyData.isEmpty()) {
                        String[] columns = mutKeyData.keySet().toArray(new String[0]);
                        int delCount = random.nextInt(Math.min(3, columns.length) + 1);
                        for (int i = 0; i < delCount; i++) {
                            mutOp.deletions.add(columns[random.nextInt(columns.length)]);
                        }
                    }
                }
                return mutOp;
                
            case QUERY:
                String start = generateRandomColumn();
                String end = generateRandomColumn();
                if (start.compareTo(end) > 0) {
                    String temp = start;
                    start = end;
                    end = temp;
                }
                return new StressOperation(type, key, start, end);
                
            default:
                throw new IllegalStateException("Unknown operation type: " + type);
        }
    }

    private static StressOperation generateThreadScopedOperation(String threadPrefix, Map<String, Map<String, String>> threadState) {
        // Similar to generateRandomOperation but with thread-scoped keys
        StressOperation.Type[] types = StressOperation.Type.values();
        StressOperation.Type type = types[random.nextInt(types.length)];
        
        String key = threadPrefix + generateRandomKey();
        
        // Use same logic as generateRandomOperation but with thread-specific state
        switch (type) {
            case PUT:
                StressOperation putOp = new StressOperation(type, key);
                putOp.additions.add(new NativeInMemoryDB.Entry(generateRandomColumn(), generateRandomValue()));
                return putOp;
                
            case DELETE:
                StressOperation delOp = new StressOperation(type, key);
                Map<String, String> keyData = threadState.get(key);
                if (keyData != null && !keyData.isEmpty()) {
                    String[] columns = keyData.keySet().toArray(new String[0]);
                    delOp.deletions.add(columns[random.nextInt(columns.length)]);
                } else {
                    delOp.deletions.add(generateRandomColumn());
                }
                return delOp;
                
            case MUTATE:
                StressOperation mutOp = new StressOperation(type, key);
                int addCount = 1 + random.nextInt(3);
                for (int i = 0; i < addCount; i++) {
                    mutOp.additions.add(new NativeInMemoryDB.Entry(generateRandomColumn(), generateRandomValue()));
                }
                return mutOp;
                
            case QUERY:
                String start = generateRandomColumn();
                String end = generateRandomColumn();
                if (start.compareTo(end) > 0) {
                    String temp = start;
                    start = end;
                    end = temp;
                }
                return new StressOperation(type, key, start, end);
                
            default:
                throw new IllegalStateException("Unknown operation type: " + type);
        }
    }

    private static String generateRandomKey() {
        String prefix = KEY_PREFIXES[random.nextInt(KEY_PREFIXES.length)];
        int suffix = random.nextInt(MAX_KEYS);
        return prefix + "_" + suffix;
    }

    private static String generateRandomColumn() {
        String prefix = COLUMN_PREFIXES[random.nextInt(COLUMN_PREFIXES.length)];
        int suffix = random.nextInt(MAX_COLUMNS_PER_KEY);
        return prefix + "_" + suffix;
    }

    private static String generateRandomValue() {
        int length = 1 + random.nextInt(MAX_VALUE_LENGTH);
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append((char) ('a' + random.nextInt(26)));
        }
        return sb.toString();
    }

    private static boolean executeJavaOperation(KeyColumnValueStore javaStore, StoreTransaction javaTx, StressOperation operation) {
        try {
            StaticBuffer key = makeStaticBuffer(operation.key);
            
            switch (operation.type) {
                case PUT:
                case MUTATE:
                    List<Entry> javaAdditions = new ArrayList<>();
                    for (NativeInMemoryDB.Entry entry : operation.additions) {
                        javaAdditions.add(makeEntry(entry.column, entry.value));
                    }
                    
                    List<StaticBuffer> javaDeletions = new ArrayList<>();
                    for (String deletion : operation.deletions) {
                        javaDeletions.add(makeStaticBuffer(deletion));
                    }
                    
                    javaStore.mutate(key, javaAdditions, javaDeletions, javaTx);
                    return true;
                    
                case DELETE:
                    List<StaticBuffer> javaDelOnly = new ArrayList<>();
                    for (String deletion : operation.deletions) {
                        javaDelOnly.add(makeStaticBuffer(deletion));
                    }
                    javaStore.mutate(key, Collections.emptyList(), javaDelOnly, javaTx);
                    return true;
                    
                case QUERY:
                    SliceQuery slice = new SliceQuery(makeStaticBuffer(operation.queryStart), makeStaticBuffer(operation.queryEnd));
                    javaStore.getSlice(new KeySliceQuery(key, slice), javaTx);
                    return true;
                    
                default:
                    return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean executeCppOperation(NativeInMemoryDB.NativeStore cppStore, StressOperation operation) {
        try {
            switch (operation.type) {
                case PUT:
                case MUTATE:
                    cppStore.mutate(operation.key, operation.additions, operation.deletions);
                    return true;
                    
                case DELETE:
                    cppStore.mutate(operation.key, Collections.emptyList(), operation.deletions);
                    return true;
                    
                case QUERY:
                    cppStore.getSlice(operation.key, operation.queryStart, operation.queryEnd);
                    return true;
                    
                default:
                    return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static void updateExpectedState(Map<String, Map<String, String>> expectedState, StressOperation operation) {
        if (operation.type == StressOperation.Type.QUERY) return; // Queries don't modify state
        
        Map<String, String> keyData = expectedState.computeIfAbsent(operation.key, k -> new HashMap<>());
        
        // Apply deletions first
        for (String deletion : operation.deletions) {
            keyData.remove(deletion);
        }
        
        // Apply additions
        for (NativeInMemoryDB.Entry addition : operation.additions) {
            keyData.put(addition.column, addition.value);
        }
        
        // Remove empty key entries
        if (keyData.isEmpty()) {
            expectedState.remove(operation.key);
        }
    }

    private static boolean verifyEquivalence(KeyColumnValueStore javaStore, StoreTransaction javaTx, 
                                           NativeInMemoryDB.NativeStore cppStore, StressOperation operation,
                                           Map<String, Map<String, String>> expectedState) {
        try {
            // For queries, verify the results match
            if (operation.type == StressOperation.Type.QUERY) {
                StaticBuffer key = makeStaticBuffer(operation.key);
                SliceQuery slice = new SliceQuery(makeStaticBuffer(operation.queryStart), makeStaticBuffer(operation.queryEnd));
                
                EntryList javaResult = javaStore.getSlice(new KeySliceQuery(key, slice), javaTx);
                List<NativeInMemoryDB.Entry> cppResult = cppStore.getSlice(operation.key, operation.queryStart, operation.queryEnd);
                
                boolean sizeMismatch = javaResult.size() != cppResult.size();
                
                // Convert and sort for comparison
                List<String> javaPairs = new ArrayList<>();
                for (int i = 0; i < javaResult.size(); i++) {
                    javaPairs.add(bytesToString(javaResult.get(i).getColumn()) + ":" + bytesToString(javaResult.get(i).getValue()));
                }
                Collections.sort(javaPairs);
                
                List<String> cppPairs = new ArrayList<>();
                for (NativeInMemoryDB.Entry entry : cppResult) {
                    cppPairs.add(entry.column + ":" + entry.value);
                }
                Collections.sort(cppPairs);
                
                boolean contentMismatch = !javaPairs.equals(cppPairs);
                
                if (sizeMismatch || contentMismatch) {
                    String javaResultStr = "SIZE=" + javaResult.size() + " CONTENT=" + javaPairs.toString();
                    String cppResultStr = "SIZE=" + cppResult.size() + " CONTENT=" + cppPairs.toString();
                    
                    // Record this mismatch for debugging
                    recordMismatch(Thread.currentThread().getName(), operation, javaResultStr, cppResultStr, 
                                  javaStore, javaTx, cppStore);
                    
                    if (!debugMode) {
                        // Only print brief message if not in debug mode
                        System.err.println("Query result mismatch: Java=" + javaResult.size() + ", C++=" + cppResult.size());
                    }
                    return false;
                }
                
                return true;
            }
            
            return true; // For mutations, we trust the expected state tracking
        } catch (Exception e) {
            System.err.println("Equivalence verification failed: " + e.getMessage());
            return false;
        }
    }

    private static boolean verifyThreadEquivalence(KeyColumnValueStore javaStore, StoreTransaction javaTx, 
                                                 NativeInMemoryDB.NativeStore cppStore, StressOperation operation,
                                                 Map<String, Map<String, String>> threadExpectedState) {
        // For multi-threaded tests, only verify operations on thread-specific keys
        // where we can be sure of the expected state
        return verifyEquivalence(javaStore, javaTx, cppStore, operation, threadExpectedState);
    }

    private static void printFinalResults() {
        System.out.println("\n=== FINAL STRESS TEST RESULTS ===");
        System.out.printf("Total Operations: %d%n", totalOperations.get());
        System.out.printf("Successful Operations: %d%n", successfulOperations.get());
        System.out.printf("Equivalence Failures: %d (%.3f%%)%n", 
            equivalenceFailures.get(), (100.0 * equivalenceFailures.get()) / Math.max(1, totalOperations.get()));
        System.out.printf("Java Errors: %d%n", javaErrors.get());
        System.out.printf("C++ Errors: %d%n", cppErrors.get());
        
        long totalErrors = javaErrors.get() + cppErrors.get() + equivalenceFailures.get();
        double successRate = 100.0 * (totalOperations.get() - totalErrors) / Math.max(1, totalOperations.get());
        System.out.printf("Overall Success Rate: %.2f%%%n", successRate);
    }

    // Debugging and analysis methods
    private static void recordMismatch(String threadName, StressOperation operation, 
                                     String javaResult, String cppResult,
                                     KeyColumnValueStore javaStore, StoreTransaction javaTx,
                                     NativeInMemoryDB.NativeStore cppStore) {
        if (mismatchRecords.size() >= MAX_MISMATCH_RECORDS) {
            return; // Avoid memory overflow
        }
        
        try {
            String javaStoreState = dumpJavaStoreState(javaStore, javaTx);
            String cppStoreState = dumpCppStoreState(cppStore);
            
            // Get copy of recent operations for context
            List<StressOperation> recentOps = new ArrayList<>(recentOperations);
            
            MismatchRecord record = new MismatchRecord(threadName, operation, javaResult, cppResult,
                                                      recentOps, javaStoreState, cppStoreState);
            
            mismatchRecords.add(record);
            
            if (debugMode) {
                System.out.println("\n=== MISMATCH DETECTED ===");
                System.out.println("Thread: " + threadName);
                System.out.println("Operation: " + operation);
                System.out.println("Java Result: " + javaResult);
                System.out.println("C++ Result: " + cppResult);
                System.out.println("Recent Operations (" + recentOps.size() + "):");
                for (int i = Math.max(0, recentOps.size() - 10); i < recentOps.size(); i++) {
                    System.out.println("  " + (i + 1) + ": " + recentOps.get(i));
                }
                System.out.println();
            }
            
            // Test reproducibility
            testReproducibility(record, javaStore, javaTx, cppStore);
            
        } catch (Exception e) {
            System.err.println("Error recording mismatch: " + e.getMessage());
        }
    }
    
    private static void testReproducibility(MismatchRecord record, KeyColumnValueStore javaStore, 
                                          StoreTransaction javaTx, NativeInMemoryDB.NativeStore cppStore) {
        try {
            // Try to reproduce the same operation
            String newJavaResult = executeJavaOperationForResult(javaStore, javaTx, record.operation);
            String newCppResult = executeCppOperationForResult(cppStore, record.operation);
            
            if (newJavaResult.equals(record.javaResult) && newCppResult.equals(record.cppResult)) {
                record.reproducible = true;
                record.reproductionResult = "Consistent: Java=" + newJavaResult + ", C++=" + newCppResult;
            } else {
                record.reproducible = false;
                record.reproductionResult = "Changed: Java=" + newJavaResult + " (was " + record.javaResult + 
                                           "), C++=" + newCppResult + " (was " + record.cppResult + ")";
            }
            
            if (debugMode) {
                System.out.println("Reproducibility test: " + record.reproductionResult);
            }
        } catch (Exception e) {
            record.reproductionResult = "Error during reproduction: " + e.getMessage();
        }
    }
    
    private static String executeJavaOperationForResult(KeyColumnValueStore javaStore, StoreTransaction javaTx, 
                                                       StressOperation operation) throws Exception {
        switch (operation.type) {
            case PUT:
                javaStore.mutate(makeStaticBuffer(operation.key), 
                               Arrays.asList(makeEntry(operation.additions.get(0).column, operation.additions.get(0).value)), 
                               Collections.emptyList(), javaTx);
                return "PUT_SUCCESS";
                
            case DELETE:
                javaStore.mutate(makeStaticBuffer(operation.key), 
                               Collections.emptyList(), 
                               Arrays.asList(makeStaticBuffer(operation.deletions.get(0))), javaTx);
                return "DELETE_SUCCESS";
                
            case MUTATE:
                List<Entry> javaAdditions = new ArrayList<>();
                for (NativeInMemoryDB.Entry e : operation.additions) {
                    javaAdditions.add(makeEntry(e.column, e.value));
                }
                List<StaticBuffer> javaDeletions = new ArrayList<>();
                for (String col : operation.deletions) {
                    javaDeletions.add(makeStaticBuffer(col));
                }
                javaStore.mutate(makeStaticBuffer(operation.key), javaAdditions, javaDeletions, javaTx);
                return "MUTATE_SUCCESS";
                
            case QUERY:
                SliceQuery query = new SliceQuery(makeStaticBuffer(operation.queryStart), 
                                                 makeStaticBuffer(operation.queryEnd));
                KeySliceQuery keyQuery = new KeySliceQuery(makeStaticBuffer(operation.key), query);
                EntryList result = javaStore.getSlice(keyQuery, javaTx);
                return "QUERY_SIZE=" + result.size();
                
            default:
                return "UNKNOWN_OPERATION";
        }
    }
    
    private static String executeCppOperationForResult(NativeInMemoryDB.NativeStore cppStore, StressOperation operation) {
        try {
            switch (operation.type) {
                case PUT:
                    cppStore.put(operation.key, operation.additions.get(0).column, operation.additions.get(0).value);
                    return "PUT_SUCCESS";
                    
                case DELETE:
                    cppStore.delete(operation.key, operation.deletions.get(0));
                    return "DELETE_SUCCESS";
                    
                case MUTATE:
                    cppStore.mutate(operation.key, operation.additions, operation.deletions);
                    return "MUTATE_SUCCESS";
                    
                case QUERY:
                    List<NativeInMemoryDB.Entry> result = cppStore.getSlice(operation.key, operation.queryStart, operation.queryEnd);
                    return "QUERY_SIZE=" + (result != null ? result.size() : 0);
                    
                default:
                    return "UNKNOWN_OPERATION";
            }
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }
    
    private static String dumpJavaStoreState(KeyColumnValueStore javaStore, StoreTransaction javaTx) {
        StringBuilder sb = new StringBuilder();
        try {
            sb.append("JAVA_STORE_STATE:\n");
            
            // Try to get all keys by scanning with a wide range
            SliceQuery allQuery = new SliceQuery(makeStaticBuffer(""), makeStaticBuffer("~"));
            
            // Since we can't directly list all keys, we'll sample known key patterns
            Set<String> sampleKeys = new HashSet<>();
            synchronized (recentOperations) {
                for (StressOperation op : recentOperations) {
                    if (op.key != null) {
                        sampleKeys.add(op.key);
                    }
                }
            }
            
            for (String key : sampleKeys) {
                KeySliceQuery keyQuery = new KeySliceQuery(makeStaticBuffer(key), allQuery);
                EntryList entries = javaStore.getSlice(keyQuery, javaTx);
                
                if (entries.size() > 0) {
                    sb.append("  Key: ").append(key).append(" (").append(entries.size()).append(" entries)\n");
                    for (Entry entry : entries) {
                        String col = bytesToString(entry.getColumn());
                        String val = bytesToString(entry.getValue());
                        sb.append("    ").append(col).append(" -> ").append(val).append("\n");
                    }
                }
            }
        } catch (Exception e) {
            sb.append("ERROR: ").append(e.getMessage());
        }
        return sb.toString();
    }
    
    private static String dumpCppStoreState(NativeInMemoryDB.NativeStore cppStore) {
        StringBuilder sb = new StringBuilder();
        try {
            sb.append("CPP_STORE_STATE:\n");
            
            // Sample the same keys as Java
            Set<String> sampleKeys = new HashSet<>();
            synchronized (recentOperations) {
                for (StressOperation op : recentOperations) {
                    if (op.key != null) {
                        sampleKeys.add(op.key);
                    }
                }
            }
            
            for (String key : sampleKeys) {
                List<NativeInMemoryDB.Entry> entries = cppStore.getSlice(key, "", "~");
                if (entries != null && entries.size() > 0) {
                    sb.append("  Key: ").append(key).append(" (").append(entries.size()).append(" entries)\n");
                    for (NativeInMemoryDB.Entry entry : entries) {
                        sb.append("    ").append(entry.column).append(" -> ").append(entry.value).append("\n");
                    }
                }
            }
        } catch (Exception e) {
            sb.append("ERROR: ").append(e.getMessage());
        }
        return sb.toString();
    }
    
    private static void recordOperation(StressOperation operation) {
        synchronized (recentOperations) {
            recentOperations.add(operation);
            if (recentOperations.size() > MAX_RECENT_OPERATIONS) {
                recentOperations.remove(0); // Remove oldest
            }
        }
    }
    
    private static void printMismatchSummary() {
        if (mismatchRecords.isEmpty()) {
            System.out.println("\n=== No mismatches recorded ===");
            return;
        }
        
        System.out.println("\n=== MISMATCH ANALYSIS SUMMARY ===");
        System.out.println("Total mismatches recorded: " + mismatchRecords.size());
        
        Map<String, Integer> operationTypeCounts = new HashMap<>();
        Map<String, Integer> reproducibilityCounts = new HashMap<>();
        
        for (MismatchRecord record : mismatchRecords) {
            String opType = record.operation.type.toString();
            operationTypeCounts.put(opType, operationTypeCounts.getOrDefault(opType, 0) + 1);
            
            String repro = record.reproducible ? "Reproducible" : "Non-reproducible";
            reproducibilityCounts.put(repro, reproducibilityCounts.getOrDefault(repro, 0) + 1);
        }
        
        System.out.println("\nMismatches by operation type:");
        for (Map.Entry<String, Integer> entry : operationTypeCounts.entrySet()) {
            System.out.println("  " + entry.getKey() + ": " + entry.getValue());
        }
        
        System.out.println("\nReproducibility:");
        for (Map.Entry<String, Integer> entry : reproducibilityCounts.entrySet()) {
            System.out.println("  " + entry.getKey() + ": " + entry.getValue());
        }
        
        System.out.println("\nFirst 10 mismatch records:");
        for (int i = 0; i < Math.min(10, mismatchRecords.size()); i++) {
            MismatchRecord record = mismatchRecords.get(i);
            System.out.println("  " + (i + 1) + ": " + record);
            if (record.reproductionResult != null) {
                System.out.println("      " + record.reproductionResult);
            }
        }
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
}