# Storage Backend Logging Annotation Plan

## Overview
This document outlines the plan for adding detailed logging to JanusGraph storage backends (InMemory, BerkeleyJE, HBase, KVT). The logging will capture all function calls from JanusGraph core to the storage engine, including inputs, outputs, and performance metrics in a compact single-line format.

## Runtime Configuration

### Global Logging Control
```java
// Add to a central location (e.g., Backend class)
public static volatile boolean ENABLE_DETAILED_LOGGING = false;

// Can be set at runtime via:
// 1. System property: -Djanusgraph.storage.logging=true
// 2. Environment variable: JANUSGRAPH_STORAGE_LOGGING=true
// 3. Programmatically: Backend.ENABLE_DETAILED_LOGGING = true;
```

### Configuration Loading
```java
// In Backend class or configuration loading
ENABLE_DETAILED_LOGGING = Boolean.parseBoolean(
    System.getProperty("janusgraph.storage.logging", 
    System.getenv("JANUSGRAPH_STORAGE_LOGGING") != null ? 
    System.getenv("JANUSGRAPH_STORAGE_LOGGING") : "false"));
```

## Functions to be Annotated

### 1. StoreManager Functions (Common across all backends)

#### 1.1 beginTransaction()
- **Input**: `BaseTransactionConfig config`
- **Output**: `StoreTransaction`
- **Log Format**: `[STORAGE-MANAGER] beginTransaction() | Config:{config} | TxId:{tx.hashCode()} | Duration:{duration}ms`

#### 1.2 openDatabase()
- **Input**: `String name`, `StoreMetaData.Container metaData` (optional)
- **Output**: `KeyColumnValueStore` or `KeyValueStore`
- **Log Format**: `[STORAGE-MANAGER] openDatabase() | Name:{name} | Created:{store!=null} | Duration:{duration}ms`

#### 1.3 mutateMany()
- **Input**: `Map<String, KCVMutation>` or `Map<String, KVMutation>`, `StoreTransaction txh`
- **Output**: `void`
- **Log Format**: `[STORAGE-MANAGER] mutateMany() | Stores:{mutations.size()} | TotalOps:{totalAdditions+totalDeletions} | Duration:{duration}ms`

#### 1.4 close()
- **Input**: None
- **Output**: `void`
- **Log Format**: `[STORAGE-MANAGER] close() | Duration:{duration}ms`

#### 1.5 clearStorage()
- **Input**: None
- **Output**: `void`
- **Log Format**: `[STORAGE-MANAGER] clearStorage() | Duration:{duration}ms`

#### 1.6 exists()
- **Input**: None
- **Output**: `boolean`
- **Log Format**: `[STORAGE-MANAGER] exists() | Result:{result} | Duration:{duration}ms`

#### 1.7 getFeatures()
- **Input**: None
- **Output**: `StoreFeatures`
- **Log Format**: `[STORAGE-MANAGER] getFeatures() | Duration:{duration}ms`

#### 1.8 getName()
- **Input**: None
- **Output**: `String`
- **Log Format**: `[STORAGE-MANAGER] getName() | Name:{name} | Duration:{duration}ms`

#### 1.9 getLocalKeyPartition()
- **Input**: None
- **Output**: `List<KeyRange>` (may throw UnsupportedOperationException)
- **Log Format**: `[STORAGE-MANAGER] getLocalKeyPartition() | Result:{result/exception} | Duration:{duration}ms`

### 2. Store Functions

#### 2.1 getSlice(KeySliceQuery) - KeyColumnValueStore
- **Input**: `KeySliceQuery query`, `StoreTransaction txh`
- **Output**: `EntryList`
- **Log Format**: `[STORAGE-STORE:{storeName}] getSlice(KeySliceQuery) | Key:{key} | Columns:{sliceStart}-{sliceEnd} | Limit:{limit} | ResultCount:{result.size()} | Duration:{duration}ms`

#### 2.2 getSlice(List<StaticBuffer>, SliceQuery) - KeyColumnValueStore
- **Input**: `List<StaticBuffer> keys`, `SliceQuery query`, `StoreTransaction txh`
- **Output**: `Map<StaticBuffer, EntryList>`
- **Log Format**: `[STORAGE-STORE:{storeName}] getSlice(MultiKey) | Keys:{keys.size()} | ResultKeys:{result.size()} | Duration:{duration}ms`

#### 2.3 getSlice(KVQuery) - KeyValueStore
- **Input**: `KVQuery query`, `StoreTransaction txh`
- **Output**: `RecordIterator<KeyValueEntry>`
- **Log Format**: `[STORAGE-STORE:{storeName}] getSlice(KVQuery) | Range:{start}-{end} | Duration:{duration}ms`

#### 2.4 mutate() - KeyColumnValueStore
- **Input**: `StaticBuffer key`, `List<Entry> additions`, `List<StaticBuffer> deletions`, `StoreTransaction txh`
- **Output**: `void`
- **Log Format**: `[STORAGE-STORE:{storeName}] mutate() | Key:{key} | Additions:{additions.size()} | Deletions:{deletions.size()} | Duration:{duration}ms`

#### 2.5 insert() - KeyValueStore
- **Input**: `StaticBuffer key`, `StaticBuffer value`, `StoreTransaction txh`, `Integer ttl`
- **Output**: `void`
- **Log Format**: `[STORAGE-STORE:{storeName}] insert() | Key:{key} | Value:{value.length()} | TTL:{ttl} | Duration:{duration}ms`

#### 2.6 delete() - KeyValueStore
- **Input**: `StaticBuffer key`, `StoreTransaction txh`
- **Output**: `void`
- **Log Format**: `[STORAGE-STORE:{storeName}] delete() | Key:{key} | Duration:{duration}ms`

#### 2.7 get() - KeyValueStore
- **Input**: `StaticBuffer key`, `StoreTransaction txh`
- **Output**: `StaticBuffer`
- **Log Format**: `[STORAGE-STORE:{storeName}] get() | Key:{key} | Result:{result!=null} | Duration:{duration}ms`

#### 2.8 containsKey() - KeyValueStore
- **Input**: `StaticBuffer key`, `StoreTransaction txh`
- **Output**: `boolean`
- **Log Format**: `[STORAGE-STORE:{storeName}] containsKey() | Key:{key} | Result:{result} | Duration:{duration}ms`

#### 2.9 acquireLock()
- **Input**: `StaticBuffer key`, `StaticBuffer column`, `StaticBuffer expectedValue`, `StoreTransaction txh`
- **Output**: `void` (may throw UnsupportedOperationException)
- **Log Format**: `[STORAGE-STORE:{storeName}] acquireLock() | Key:{key} | Column:{column} | Duration:{duration}ms`

#### 2.10 getKeys(KeyRangeQuery)
- **Input**: `KeyRangeQuery query`, `StoreTransaction txh`
- **Output**: `KeyIterator`
- **Log Format**: `[STORAGE-STORE:{storeName}] getKeys(KeyRangeQuery) | Range:{start}-{end} | Duration:{duration}ms`

#### 2.11 getKeys(SliceQuery)
- **Input**: `SliceQuery query`, `StoreTransaction txh`
- **Output**: `KeyIterator`
- **Log Format**: `[STORAGE-STORE:{storeName}] getKeys(SliceQuery) | Columns:{sliceStart}-{sliceEnd} | Duration:{duration}ms`

#### 2.12 getKeys(MultiSlicesQuery)
- **Input**: `MultiSlicesQuery queries`, `StoreTransaction txh`
- **Output**: `KeySlicesIterator` (may throw UnsupportedOperationException)
- **Log Format**: `[STORAGE-STORE:{storeName}] getKeys(MultiSlicesQuery) | Queries:{queries.size()} | Duration:{duration}ms`

#### 2.13 getName()
- **Input**: None
- **Output**: `String`
- **Log Format**: `[STORAGE-STORE:{storeName}] getName() | Name:{name} | Duration:{duration}ms`

#### 2.14 close()
- **Input**: None
- **Output**: `void`
- **Log Format**: `[STORAGE-STORE:{storeName}] close() | Duration:{duration}ms`

#### 2.15 clear()
- **Input**: None
- **Output**: `void`
- **Log Format**: `[STORAGE-STORE:{storeName}] clear() | Duration:{duration}ms`

### 3. Transaction Functions

#### 3.1 commit()
- **Input**: None
- **Output**: `void`
- **Log Format**: `[STORAGE-TX:{txId}] commit() | Duration:{duration}ms`

#### 3.2 rollback()
- **Input**: None
- **Output**: `void`
- **Log Format**: `[STORAGE-TX:{txId}] rollback() | Duration:{duration}ms`

### 4. KVT-Specific Functions

*Note: KVT implements the same public interface as InMemory. No additional public functions to log.*

### 5. Special KVT Considerations

*Note: All KVT-specific considerations are internal implementation details and not part of the public API.*

## Backend-Specific Differences

### BerkeleyJE vs InMemory Differences

#### 1. StoreManager Interface
- **BerkeleyJE**: Implements `OrderedKeyValueStoreManager` (extends `KeyValueStoreManager`)
- **InMemory**: Implements `KeyColumnValueStoreManager` (extends `StoreManager`)
- **Difference**: BerkeleyJE uses key-value model, InMemory uses key-column-value model

#### 2. Store Interface
- **BerkeleyJE**: `BerkeleyJEKeyValueStore` implements `OrderedKeyValueStore`
- **InMemory**: `InMemoryKeyColumnValueStore` implements `KeyColumnValueStore`
- **Additional BerkeleyJE Functions**:
  - `get(StaticBuffer key, StoreTransaction txh)` → `StaticBuffer`
  - `containsKey(StaticBuffer key, StoreTransaction txh)` → `boolean`
  - `insert(StaticBuffer key, StaticBuffer value, StoreTransaction txh, Integer ttl)` → `void`
  - `delete(StaticBuffer key, StoreTransaction txh)` → `void`
  - `getSlice(KVQuery query, StoreTransaction txh)` → `RecordIterator<KeyValueEntry>`
  - `getSlices(List<KVQuery> queries, StoreTransaction txh)` → `Map<KVQuery, RecordIterator<KeyValueEntry>>`

#### 3. Transaction Interface
- **BerkeleyJE**: `BerkeleyJETx` extends `AbstractStoreTransaction`
- **InMemory**: `InMemoryTransaction` extends `AbstractStoreTransaction`
- **Additional BerkeleyJE Features**:
  - `getTransaction()` → `Transaction` (BerkeleyDB transaction)
  - `openCursor(Database db)` → `Cursor`
  - `closeCursor(Cursor cursor)` → `void`
  - `getCacheMode()` → `CacheMode`
  - `getLockMode()` → `LockMode`

#### 4. mutateMany() Parameters
- **BerkeleyJE**: `Map<String, KVMutation>` (key-value mutations)
- **InMemory**: `Map<String, Map<StaticBuffer, KCVMutation>>` (key-column-value mutations)

#### 5. Configuration Differences
- **BerkeleyJE**: Has extensive configuration options (cache, lock mode, isolation level)
- **InMemory**: Minimal configuration (mostly defaults)

### HBase vs InMemory Differences

#### 1. StoreManager Interface
- **HBase**: Implements `KeyColumnValueStoreManager` (same as InMemory)
- **Additional HBase Functions**:
  - `getDeployment()` → `Deployment` (REMOTE/LOCAL/EMBEDDED)
  - `getTableName()` → `TableName`
  - `getConnection()` → `Connection`

#### 2. Store Interface
- **HBase**: `HBaseKeyColumnValueStore` implements `KeyColumnValueStore` (same as InMemory)
- **Additional HBase Features**:
  - Uses HBase `Table`, `Get`, `Put`, `Delete` operations
  - Supports HBase filters and scan operations
  - Has column family management

### KVT vs InMemory Differences

#### 1. StoreManager Interface
- **KVT**: Implements `KeyColumnValueStoreManager` (same as InMemory)
- **No additional public functions**: KVT implements the same interface as InMemory

#### 2. Store Interface
- **KVT**: `KVTKeyColumnValueStore` implements `KeyColumnValueStore` (same as InMemory)
- **No additional public functions**: KVT implements the same interface as InMemory

#### 3. Transaction Interface
- **KVT**: `KVTTransaction` extends `AbstractStoreTransaction`
- **No additional public functions**: KVT implements the same interface as InMemory

## Logging Implementation Details

### 1. Compact Logging Utility Class
```java
public class StorageLoggingUtil {
    private static final Logger log = LoggerFactory.getLogger("StorageBackend");
    
    public static void logFunctionCall(String component, String function, Map<String, Object> params, long startTime) {
        if (!Backend.ENABLE_DETAILED_LOGGING) {
            return;
        }
        
        long duration = System.currentTimeMillis() - startTime;
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(component).append("] ").append(function).append(" | ");
        
        if (params != null && !params.isEmpty()) {
            boolean first = true;
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                if (!first) sb.append(" | ");
                sb.append(entry.getKey()).append(":").append(entry.getValue());
                first = false;
            }
            sb.append(" | ");
        }
        
        sb.append("Duration:").append(duration).append("ms");
        
        log.info(sb.toString());
    }
}
```

### 2. Compact Buffer Serialization with Binary Value Mapping
```java
public class CompactBufferSerializer {
    private static final int MAX_BYTES = 16;
    private static final Map<String, String> binaryValueMap = new ConcurrentHashMap<>();
    private static final AtomicInteger valueCounter = new AtomicInteger(0);
    
    public static String serialize(StaticBuffer buffer) {
        if (buffer == null) return "null";
        if (buffer.length() == 0) return "[]";
        
        byte[] bytes = new byte[Math.min(buffer.length(), MAX_BYTES)];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = buffer.getByte(i);
        }
        
        // Check if printable ASCII
        if (isPrintableAscii(bytes)) {
            String result = bytesToText(bytes);
            if (buffer.length() > MAX_BYTES) {
                result += "...";
            }
            return result;
        } else {
            // Use hash table for non-printable data
            return getOrCreateBinaryValue(bytes);
        }
    }
    
    private static boolean isPrintableAscii(byte[] bytes) {
        for (byte b : bytes) {
            if (b < 32 || b > 126) return false;
        }
        return true;
    }
    
    private static String getOrCreateBinaryValue(byte[] bytes) {
        String hexKey = bytesToHex(bytes);
        return binaryValueMap.computeIfAbsent(hexKey, k -> {
            int counter = valueCounter.incrementAndGet();
            String valueName = "V_" + counter;
            // Log the mapping immediately
            System.out.println("V_" + counter + "=" + 
                Arrays.stream(bytes).mapToObj(b -> String.format("\\%02X", b & 0xFF)).collect(Collectors.joining("")));
            return valueName;
        });
    }
    
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) sb.append(" ");
            sb.append(String.format("%02X", bytes[i] & 0xFF));
        }
        sb.append("]");
        return sb.toString();
    }
    
    private static String bytesToText(byte[] bytes) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < bytes.length; i++) {
            sb.append((char) bytes[i]);
        }
        sb.append("\"");
        return sb.toString();
    }
}
```

### 3. Compact Entry Serialization
```java
public class CompactEntrySerializer {
    public static String serialize(EntryList entries) {
        if (entries == null) return "null";
        if (entries.isEmpty()) return "[]";
        
        return "[" + entries.size() + " entries]";
    }
    
    public static String serialize(RecordIterator<KeyValueEntry> iterator) {
        if (iterator == null) return "null";
        return "[iterator]";
    }
}

### 4. Binary Value Mapping for Non-Printable Data

#### 4.1 Hash Table Mechanism
- **Purpose**: Make logs readable when dealing with binary/non-ASCII data
- **Storage**: `ConcurrentHashMap<String, String>` maps hex representation to readable names
- **Naming**: `V_1`, `V_2`, `V_3`, etc. (auto-incrementing)

#### 4.2 ASCII Detection
- **Printable range**: ASCII 32-126 (space to tilde)
- **Non-printable**: Any byte outside this range triggers hash table lookup
- **First occurrence**: Logs the full binary value with escape sequences

#### 4.3 Example Mapping Output
```
V_1=\00\02\33\12
V_2=\FF\FE\01\02\03
```

#### 4.4 Log Output with Binary Values
```
[STORAGE-STORE:edgestore] getSlice(KeySliceQuery) | Key:"vertex123" | Columns:V_1-V_2 | Limit:100 | ResultCount:5 | Duration:2ms
[STORAGE-STORE:edgestore] mutate() | Key:V_3 | Additions:3 | Deletions:1 | Duration:1ms
```

## Performance Considerations

### 1. Conditional Logging
- All logging is wrapped in `if (ENABLE_DETAILED_LOGGING)` checks
- No string formatting or object serialization when logging is disabled
- Minimal performance impact when logging is off

### 2. Efficient Serialization
- Buffer serialization limited to first 32 bytes for large buffers
- Entry list serialization limited to first 10 entries
- Use of StringBuilder for efficient string concatenation

### 3. Thread Safety
- Logging utility methods are thread-safe
- No shared state between logging calls
- Use of volatile boolean for global logging control

## Usage Examples

### Enable Logging at Runtime
```bash
# Via system property
java -Djanusgraph.storage.logging=true -jar janusgraph-server.jar

# Via environment variable
export JANUSGRAPH_STORAGE_LOGGING=true
java -jar janusgraph-server.jar
```

### Programmatic Control
```java
// Enable logging
Backend.ENABLE_DETAILED_LOGGING = true;

// Disable logging
Backend.ENABLE_DETAILED_LOGGING = false;
```

### Sample Log Output with Binary Value Mapping
```
V_1=\00\02\33\12
V_2=\FF\FE\01\02\03
[STORAGE-MANAGER] beginTransaction() | Config:BaseTransactionConfig{...} | TxId:12345 | Duration:2ms
[STORAGE-STORE:edgestore] getSlice(KeySliceQuery) | Key:"vertex123" | Columns:V_1-V_2 | Limit:100 | ResultCount:5 | Duration:1ms
[STORAGE-STORE:edgestore] mutate() | Key:V_3 | Additions:3 | Deletions:1 | Duration:1ms
[STORAGE-TX:12345] commit() | Duration:1ms
```

### BerkeleyJE Sample Log Output
```
[STORAGE-MANAGER] beginTransaction() | Config:BaseTransactionConfig{...} | TxId:67890 | Duration:3ms
[STORAGE-STORE:edgestore] get() | Key:[01 02 03 04] | Result:true | Duration:1ms
[STORAGE-STORE:edgestore] insert() | Key:[01 02 03 04] | Value:32 | TTL:null | Duration:2ms
[STORAGE-STORE:edgestore] getSlice(KVQuery) | Range:[00]-[FF] | Duration:5ms
[STORAGE-TX:67890] commit() | Duration:2ms
```

### KVT Sample Log Output
```
[STORAGE-MANAGER] beginTransaction() | Config:BaseTransactionConfig{...} | TxId:12345 | Duration:3ms
[STORAGE-MANAGER] openDatabase() | Name:edgestore | Created:true | Duration:5ms
[STORAGE-STORE:edgestore] getSlice(KeySliceQuery) | Key:[01 02 03 04 05 06 07 08] | Columns:[09 0A]-[FF] | Limit:100 | ResultCount:5 | Duration:2ms
[STORAGE-STORE:edgestore] mutate() | Key:[01 02 03 04] | Additions:3 | Deletions:1 | Duration:2ms
[STORAGE-TX:12345] commit() | Duration:1ms
```

## Next Steps

1. Implement the logging utility classes
2. Add logging annotations to InMemoryStoreManager
3. Add logging annotations to InMemoryKeyColumnValueStore
4. Add logging annotations to InMemoryTransaction
5. Test logging functionality with various scenarios
6. Measure performance impact
7. Extend to other storage backends (BerkeleyJE, HBase, KVT)
