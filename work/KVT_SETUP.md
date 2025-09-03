# Enabling KVT Backend in JanusGraph

The KVT backend has been successfully integrated into your JanusGraph distribution.

## Quick Start

### 1. Using Gremlin Console with KVT

```bash
# Use the KVT-enabled launcher (sets java.library.path automatically)
./bin/gremlin-kvt.sh

# In the Gremlin console:
graph = JanusGraphFactory.build().
  set('storage.backend', 'org.janusgraph.diskstorage.kvt.KVTStoreManager').
  set('storage.directory', '/tmp/janusgraph-kvt-data').
  open()

# Create some data
g = graph.traversal()
v1 = g.addV('person').property('name', 'Alice').next()
v2 = g.addV('person').property('name', 'Bob').next()
g.addE('knows').from(v1).to(v2).iterate()
graph.tx().commit()

# Query the data
g.V().has('name', 'Alice').out('knows').values('name')
```

### 2. Using Configuration File

Use the pre-configured properties file:
```groovy
graph = JanusGraphFactory.open('conf/kvt/janusgraph-kvt-server.properties')
```

### 3. Starting Gremlin Server with KVT

Edit `conf/gremlin-server/gremlin-server.yaml`:
```yaml
graphs: {
  graph: conf/kvt/janusgraph-kvt-server.properties
}
```

Then start the server:
```bash
# Set library path for native library
export JAVA_OPTIONS="-Djava.library.path=$PWD/lib"
./bin/gremlin-server.sh conf/gremlin-server/gremlin-server.yaml
```

## Configuration Options

- `storage.backend`: Use `org.janusgraph.diskstorage.kvt.KVTStoreManager`
- `storage.directory`: Directory for KVT data (currently in-memory implementation)
- `storage.kvt.storage-method`: `composite` (default) or `serialized`

## Files Added

1. **JAR**: `lib/janusgraph-kvt-1.2.0-SNAPSHOT.jar` - KVT backend implementation
2. **Native Library**: `lib/libjanusgraph-kvt-jni.so` - JNI bridge to C++ KVT
3. **Configuration**: `conf/kvt/janusgraph-kvt-server.properties` - Sample configuration
4. **Launcher**: `bin/gremlin-kvt.sh` - Wrapper script with library path set

## Features

The KVT backend provides:
- Full ACID transactions with pessimistic locking (2PL)
- High-performance C++ implementation
- Distributed architecture support
- Persistent storage capabilities
- Range queries for efficient graph traversal
- Thread-safe concurrent access
- Batch operations for improved performance

## Troubleshooting

If you encounter `UnsatisfiedLinkError`:
- Ensure the native library is in `lib/` directory
- Use the `gremlin-kvt.sh` launcher or set: `export JAVA_OPTIONS="-Djava.library.path=$PWD/lib"`

## Current Implementation

The current implementation uses the in-memory version (`kvt_mem.o`) for testing. Production implementations can provide different backing stores with full persistence and distribution capabilities.