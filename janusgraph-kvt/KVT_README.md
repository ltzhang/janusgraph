# JanusGraph KVT Backend Integration

This directory contains the integration of the KVT (Key-Value Transaction) system as a JanusGraph storage backend. The KVT backend provides a high-performance, distributed, transactional storage backend for JanusGraph.

## Overview

The KVT integration bridges JanusGraph's key-column-value storage model to KVT's transactional key-value store using JNI (Java Native Interface). This allows JanusGraph to leverage KVT's powerful capabilities:

### Core Capabilities
- **Full ACID transactions** with pessimistic locking (2PL)
- **High-performance storage** with C++ implementation
- **Distributed architecture** with scalability support
- **Persistent storage** with durability guarantees
- **Range queries** for efficient graph traversal
- **Thread-safe concurrent access**
- **Batch operations** for improved performance

### Required Properties for Full Feature Support
While the KVT interface (`kvt_inc.h`) provides a flexible foundation, certain JanusGraph features require specific backend properties:

- **Transactions**: Full ACID compliance with commit/rollback support
- **Durability**: Data persistence across process restarts
- **Scalability**: Ability to handle large datasets and concurrent operations
- **Consistency**: Strong consistency guarantees for graph operations
- **Range Scans**: Ordered key iteration for efficient traversals

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
janusgraph-kvt/
├── src/main/java/org/janusgraph/diskstorage/kvt/
│   ├── KVTStoreManager.java              # Main storage manager
│   ├── KVTKeyColumnValueStore.java       # Store implementation
│   ├── KVTTransaction.java               # Transaction handling
│   └── KVTConfigOptions.java             # Configuration options
├── src/main/native/
│   └── janusgraph_kvt_jni.cpp           # JNI bridge implementation
├── src/test/java/org/janusgraph/diskstorage/kvt/
│   └── KVTStoreTest.java                # Basic unit tests
├── kvt/                                 # KVT C++ implementation (read-only)
│   ├── kvt_inc.h                        # KVT public API
│   ├── kvt_mem.cpp/.h                   # Memory implementation
│   └── kvt_mem.o                        # Compiled KVT library
├── kvt_adaptor/                         # Adapter utilities
│   └── janusgraph_kvt_adapter.h        # JanusGraph adapter
├── build-native.sh                     # Native library build script
├── pom.xml                             # Maven configuration
└── KVT_README.md                       # This file
```

## Changes to JanusGraph Codebase

### Modifications Outside janusgraph-kvt Directory

To integrate the KVT backend into JanusGraph, the following change was made to the main JanusGraph project:

1. **Modified `/pom.xml`** - Added KVT module to the parent POM:
   ```xml
   <modules>
       ...
       <module>janusgraph-inmemory</module>
       <module>janusgraph-kvt</module>  <!-- Added this line -->
       <module>janusgraph-berkeleyje</module>
       ...
   </modules>
   ```
   Location: Between `janusgraph-inmemory` and `janusgraph-berkeleyje` modules (around line 130)

That's the only change required to the original JanusGraph codebase. All other code is contained within the `janusgraph-kvt` directory.

## Building

### Prerequisites

- **JDK 8+** with JNI headers (development package)
- **C++ compiler** (g++ with C++17 support)
- **Make** build tool
- **JanusGraph dependencies** (built from source)

### Complete Build Instructions

```bash
# Step 2: Build JanusGraph core dependencies first
mvn clean install -pl janusgraph-core -am -DskipTests

# Step 3: Build KVT native library
cd janusgraph-kvt

# Build the C++ KVT object file (if not already built)
cd kvt
g++ -c -fPIC -g -O0 kvt_mem.cpp -o kvt_mem.o
cd ..

# Build the JNI native library
./build-native.sh

```bash
mvn clean install -Pjanusgraph-release -Dgpg.skip=true -DskipTests=true -Dmaven.compiler.debug=true -Ddocker.build.skip=true -DskipITs=true -Drat.skip=tru
Or:
mvn clean install -pl janusgraph-dist -Dgpg.skip=true -DskipTests=true -Dmaven.compiler.debug=true -Ddocker.build.skip=true -DskipITs=true -Drat.skip=tru
```

# Step 4: Build the complete KVT module
cd ..
mvn clean install -pl janusgraph-kvt -am -DskipTests
```

### Quick Build (if JanusGraph core is already built)

```bash
# From janusgraph-kvt directory
cd janusgraph-kvt

# Ensure kvt_mem.o is built
cd kvt
g++ -c -fPIC -g -O0 kvt_mem.cpp -o kvt_mem.o
cd ..

# Build native JNI library
./build-native.sh

# Build with Maven
cd ..
mvn compile -pl janusgraph-kvt -DskipTests
# Note: License and checkstyle checks are skipped by default for the KVT module
```


### Manual Build Steps

1. **Build KVT Object File** (if not already built):
```bash
cd janusgraph-kvt/kvt
g++ -c -fPIC -g -O0 kvt_mem.cpp -o kvt_mem.o
```

2. **Build Native JNI Library**:
```bash
cd janusgraph-kvt
./build-native.sh
# This creates: src/main/native/libjanusgraph-kvt-jni.so (or .dylib on Mac)
```

3. **Build Java Classes with Maven**:
```bash
# From JanusGraph root directory
mvn clean install -pl janusgraph-kvt -am -DskipTests
# Note: License and checkstyle checks are skipped by default for the KVT module

# To run tests:
mvn test -pl janusgraph-kvt
```

### Verifying the Build

After building, verify the installation:

```bash
# Check that the native library was built
ls janusgraph-kvt/src/main/native/libjanusgraph-kvt-jni.so  # Linux
# or
ls janusgraph-kvt/src/main/native/libjanusgraph-kvt-jni.dylib  # macOS

# Check that the Java classes were compiled
ls janusgraph-kvt/target/classes/org/janusgraph/diskstorage/kvt/

# Verify the module is recognized by Maven
mvn dependency:tree -pl janusgraph-kvt | grep kvt
```

## Maven Build Commands Explained

### Understanding Maven Commands

Maven is the build tool used by JanusGraph. Here's what the common commands mean:

#### Basic Command Structure
```bash
mvn [phase] [options]
```

#### Common Phases
- **`clean`** - Deletes the `target/` directory (like `make clean`). Forces complete rebuild.
- **`compile`** - Compiles source code only
- **`test`** - Compiles and runs tests
- **`package`** - Compiles, tests, and creates JAR files
- **`install`** - Does everything package does, plus installs JARs to local Maven repository (`~/.m2/repository/`)

#### Common Options
- **`-DskipTests`** - Skips running tests (still compiles test code)
- **`-pl <module>`** - Build specific module (e.g., `-pl janusgraph-kvt`)
- **`-am`** - Also make dependencies (builds required modules first)

### Incremental vs Clean Builds

**Maven supports incremental builds!** You don't need to clean every time:

```bash
# Full rebuild (like: make clean && make)
mvn clean install -DskipTests

# Incremental build (like: make) - only recompiles changed files
mvn install -DskipTests

# Even faster - just compile, don't package or install
mvn compile -DskipTests
```

**To build the distribution archive:**
mvn clean install -Pjanusgraph-release -Dgpg.skip=true -DskipTests=true
or 
mvn clean install -Pjanusgraph-debug -Dgpg.skip=true -DskipTests=true

### Development Workflow for KVT

For efficient development, use incremental builds:

```bash
# First time or after major changes (full rebuild)
mvn clean install -DskipTests -pl janusgraph-kvt -am

# Subsequent builds - Java changes only (much faster!)
mvn compile -pl janusgraph-kvt -DskipTests

# If you changed native C++ code
cd janusgraph-kvt
./build-native.sh
cd ..
mvn compile -pl janusgraph-kvt -DskipTests

# Just compile without packaging (fastest for testing compilation)
mvn compile -pl janusgraph-kvt

# Run tests after changes
mvn test -pl janusgraph-kvt
```

## Usage

### Configuration

To use the KVT backend in your JanusGraph application:

```java
import org.janusgraph.core.JanusGraph;
import org.janusgraph.core.JanusGraphFactory;
import org.apache.commons.configuration2.BaseConfiguration;

// Create configuration
BaseConfiguration config = new BaseConfiguration();
config.setProperty("storage.backend", "kvt");
config.setProperty("storage.directory", "/tmp/janusgraph-kvt");

// Open graph
JanusGraph graph = JanusGraphFactory.open(config);

// Use the graph
graph.addVertex("name", "Test Vertex");
graph.tx().commit();

// Close when done
graph.close();
```

### Gremlin Console Usage

```groovy
// In Gremlin console
graph = JanusGraphFactory.build().
  set('storage.backend', 'kvt').
  set('storage.directory', '/tmp/janusgraph-kvt').
  open()

// Create schema
mgmt = graph.openManagement()
name = mgmt.makePropertyKey('name').dataType(String.class).make()
mgmt.commit()

// Add data
g = graph.traversal()
v1 = g.addV().property('name', 'vertex1').next()
v2 = g.addV().property('name', 'vertex2').next()
g.addE('connects').from(v1).to(v2).iterate()
graph.tx().commit()

// Query
g.V().has('name', 'vertex1').out('connects').values('name')
```

### Storage Method

The KVT backend uses a **Composite Key Method** for mapping JanusGraph's key-column-value model to KVT's key-value model:

- **Storage Format**: Each column is stored as a separate KVT key-value pair
- **Key Structure**: `original_key + '\0' + column_name`
- **Benefits**: 
  - Efficient column-level operations
  - Natural support for range queries
  - Fine-grained locking at column level
  - Optimal for graph traversals
- **Use Cases**: 
  - Graph databases with sparse adjacency lists
  - Queries requiring column-specific access
  - Range scans over property keys

## Troubleshooting

### Common Issues

1. **UnsatisfiedLinkError**: Native library not found
   - Solution: Ensure `libjanusgraph-kvt-jni.so` is in library path or resources
   - Check: `export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:janusgraph-kvt/src/main/native`

2. **KVT Initialization Failed**: 
   - Solution: Check that kvt_mem.o was compiled with compatible compiler
   - Verify: File exists at `janusgraph-kvt/kvt/kvt_mem.o`

3. **Transaction Conflicts**:
   - KVT uses pessimistic locking, deadlocks may occur
   - Solution: Implement retry logic or adjust transaction scope

## Future Enhancements

- Support for additional KVT backend implementations beyond kvt_memory
- Configuration options for tuning transaction isolation levels
- Metrics and monitoring integration
- Support for KVT-specific features like batch operations optimization

