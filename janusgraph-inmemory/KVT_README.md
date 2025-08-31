# JanusGraph KVT Backend Integration

This directory contains the integration of the KVT (Key-Value Transaction) system as a JanusGraph storage backend. The KVT backend provides a high-performance, transactional, in-memory storage option for JanusGraph.

## Overview

The KVT integration bridges JanusGraph's key-column-value storage model to KVT's transactional key-value store using JNI (Java Native Interface). This allows JanusGraph to leverage KVT's:

- **Full ACID transactions** with pessimistic locking
- **High-performance in-memory storage** with C++ implementation
- **Two storage methods**: composite keys or serialized columns
- **Range queries** for efficient graph traversal
- **Thread-safe concurrent access**

## Architecture

```
┌─────────────────────────────────────────┐
│           JanusGraph Core               │
├─────────────────────────────────────────┤
│     KeyColumnValueStore Interface       │
├─────────────────────────────────────────┤
│         KVT Java Classes                │
│  ┌─────────────┬─────────────────────┐   │
│  │KVTStore     │KVTKeyColumnValue    │   │
│  │Manager      │Store                │   │
│  └─────────────┴─────────────────────┘   │
├─────────────────────────────────────────┤
│              JNI Bridge                 │
├─────────────────────────────────────────┤
│          C++ Implementation             │
│  ┌─────────────┬─────────────────────┐   │
│  │KVT Core     │JanusGraph Adapter   │   │
│  │             │                     │   │
│  └─────────────┴─────────────────────┘   │
└─────────────────────────────────────────┘
```

## Files Structure

```
janusgraph-inmemory/
├── src/main/java/org/janusgraph/diskstorage/kvt/
│   ├── KVTStoreManager.java              # Main storage manager
│   └── KVTKeyColumnValueStore.java       # Store implementation
├── src/main/native/
│   ├── janusgraph_kvt_jni.cpp           # JNI bridge
│   ├── Makefile                         # Native build configuration  
│   └── build.sh                         # Native build script
├── src/test/java/org/janusgraph/diskstorage/kvt/
│   ├── KVTStoreManagerTest.java         # Store manager tests
│   ├── KVTKeyColumnValueStoreTest.java  # Store tests
│   └── KVTIntegrationTest.java          # Integration tests
├── kvt/                                 # KVT C++ implementation
│   ├── kvt_inc.h                        # KVT public API
│   ├── kvt_mem.cpp/.h                   # Memory implementation
│   └── janusgraph/
│       └── janusgraph_kvt_adapter.h     # JanusGraph adapter
├── build-kvt.sh                        # Master build script
└── KVT_README.md                       # This file
```

## Building

### Prerequisites

- **JDK 8+** with JNI headers (development package)
- **C++ compiler** (g++ with C++17 support)
- **Make** build tool
- **JanusGraph dependencies** (built from source)

### Quick Build

```bash
cd janusgraph-inmemory
./build-kvt.sh
```

This script will:
1. Check and build JanusGraph core if needed
2. Build the native KVT JNI library
3. Compile Java KVT classes
4. Run integration tests
5. Show usage instructions

### Manual Build Steps

1. **Build JanusGraph Core:**
   ```bash
   cd janusgraph
   mvn clean compile -DskipTests -pl janusgraph-core,janusgraph-inmemory
   ```

2. **Build Native Library:**
   ```bash
   cd janusgraph-inmemory/src/main/native
   ./build.sh
   ```

3. **Compile Java Classes:**
   ```bash
   cd janusgraph-inmemory
   javac -cp "../../janusgraph-core/target/classes:target/classes" \
         -d target/classes \
         src/main/java/org/janusgraph/diskstorage/kvt/*.java
   ```

## Usage

### Configuration

```java
Configuration conf = new BaseConfiguration();
conf.setProperty("storage.backend", "org.janusgraph.diskstorage.kvt.KVTStoreManager");

// Choose storage method (optional)
conf.setProperty("storage.kvt.use-composite-key", true);  // or false for serialized columns

JanusGraph graph = JanusGraphFactory.open(conf);
```

### Storage Methods

The KVT backend supports two methods for mapping JanusGraph's key-column-value model to KVT's key-value model:

#### 1. Composite Key Method (`use-composite-key = true`)
- Stores each column as a separate KVT key-value pair
- Key format: `original_key + '\0' + column_name`
- Benefits: Efficient column-level operations, natural range queries
- Use case: Sparse data, frequent column-specific queries

#### 2. Serialized Column Method (`use-composite-key = false`)  
- Stores all columns for a key as a single serialized KVT value
- Key format: `original_key` (unchanged)
- Benefits: Atomic multi-column operations, space efficiency for dense data
- Use case: Dense data, frequent multi-column operations

### Environment Setup

Before running applications using KVT:

```bash
# Linux
export LD_LIBRARY_PATH="/path/to/janusgraph-inmemory/src/main/native:$LD_LIBRARY_PATH"

# macOS  
export DYLD_LIBRARY_PATH="/path/to/janusgraph-inmemory/src/main/native:$DYLD_LIBRARY_PATH"

# Java system property
java -Djava.library.path="/path/to/janusgraph-inmemory/src/main/native" YourApp
```

## Testing

### Unit Tests

```bash
cd janusgraph-inmemory
mvn test -Dtest="*KVT*"
```

### Integration Tests

```bash
cd janusgraph-inmemory
java -Djava.library.path="src/main/native" \
     -cp "target/classes:../../janusgraph-core/target/classes" \
     org.janusgraph.diskstorage.kvt.KVTIntegrationTest
```

### Manual Testing

```java
// Create KVT store manager
KVTStoreManager manager = new KVTStoreManager();

// Open a store
KeyColumnValueStore store = manager.openDatabase("test");

// Begin transaction
StoreTransaction tx = manager.beginTransaction(config);

// Perform operations
StaticBuffer key = StaticArrayBuffer.of("vertex:1".getBytes());
StaticBuffer column = StaticArrayBuffer.of("name".getBytes());
StaticBuffer value = StaticArrayBuffer.of("Alice".getBytes());

List<Entry> additions = Arrays.asList(new StaticArrayEntry(column, value));
store.mutate(key, additions, Collections.emptyList(), tx);

// Query data
SliceQuery slice = new SliceQuery(startColumn, endColumn);
KeySliceQuery query = new KeySliceQuery(key, slice);
EntryList results = store.getSlice(query, tx);

// Cleanup
manager.close();
```

## Performance Characteristics

| Feature | Composite Key | Serialized Column |
|---------|---------------|-------------------|
| Column lookup | O(log k) where k=keys | O(c) where c=columns per key |
| Column update | O(log k) | O(c) read + serialize |
| Range query | O(log k + results) | O(keys) scan |
| Memory usage | Higher (key overhead) | Lower (shared keys) |
| Concurrency | Column-level | Key-level |

## Limitations

- **In-memory only**: Data is not persisted to disk
- **Single JVM**: Cannot be shared across processes
- **Memory bounded**: Limited by available heap space
- **Transaction isolation**: Currently uses auto-commit; full transaction isolation pending

## Troubleshooting

### Common Issues

1. **Library Load Error**
   ```
   java.lang.UnsatisfiedLinkError: no janusgraph_kvt in java.library.path
   ```
   **Solution**: Set `LD_LIBRARY_PATH` and `-Djava.library.path`

2. **JNI Header Not Found**
   ```
   fatal error: jni.h: No such file or directory
   ```
   **Solution**: Install JDK development package and set `JAVA_HOME`

3. **Missing KVT Sources**
   ```
   Error: KVT source files not found
   ```
   **Solution**: Ensure `kvt/` directory contains required C++ files

4. **Build Failures**
   - Check compiler version (need C++17 support)
   - Verify JanusGraph core is built first
   - Check file permissions on build scripts

### Debug Mode

Build with debug symbols:
```bash
cd src/main/native
make debug
```

Enable JNI debug:
```bash
java -Xcheck:jni -verbose:jni YourApp
```

## Future Enhancements

- **Disk persistence**: Add option to persist data
- **Distributed mode**: Support for multi-node clusters
- **Full transaction isolation**: Implement proper ACID semantics
- **Performance optimization**: Fine-tune serialization and memory management
- **Monitoring**: Add metrics and monitoring capabilities

## Contributing

1. Follow JanusGraph coding standards
2. Add tests for new functionality
3. Update documentation
4. Ensure backward compatibility
5. Test on multiple platforms

For questions or issues, please refer to the JanusGraph community channels.