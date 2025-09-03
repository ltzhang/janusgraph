# JanusGraph KVT Backend Integration

This directory contains the integration of the KVT (Key-Value Transaction) system as a JanusGraph storage backend. The KVT backend provides a high-performance, distributed, transactional storage backend for JanusGraph.

## Quick Start (For Distribution Users)

If you have a built JanusGraph distribution and want to enable KVT:

```bash
# 1. Copy required files (from JanusGraph source directory)
cp janusgraph-kvt/target/janusgraph-kvt-1.2.0-SNAPSHOT.jar janusgraph-full-1.2.0-SNAPSHOT/lib/
cp janusgraph-kvt/src/main/native/libjanusgraph-kvt-jni.so janusgraph-full-1.2.0-SNAPSHOT/lib/

# 2. Create launcher script
cat > janusgraph-full-1.2.0-SNAPSHOT/bin/gremlin-kvt.sh << 'EOF'
#!/bin/bash
JANUSGRAPH_HOME="$( cd "$( dirname "${BASH_SOURCE[0]}" )/.." && pwd )"
export JAVA_OPTIONS="-Djava.library.path=$JANUSGRAPH_HOME/lib:${JAVA_OPTIONS}"
exec "$JANUSGRAPH_HOME/bin/gremlin.sh" "$@"
EOF
chmod +x janusgraph-full-1.2.0-SNAPSHOT/bin/gremlin-kvt.sh

# 3. Use KVT
cd janusgraph-full-1.2.0-SNAPSHOT
./bin/gremlin-kvt.sh

# In Gremlin console:
graph = JanusGraphFactory.build().
  set('storage.backend', 'org.janusgraph.diskstorage.kvt.KVTStoreManager').
  set('storage.directory', '/tmp/janusgraph-kvt').
  open()
```

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
mvn clean install -Pjanusgraph-release -Dgpg.skip=true -DskipTests=true -Dmaven.compiler.debug=true -Ddocker.build.skip=true -DskipITs=true -Drat.skip=true
Or:
mvn clean install -pl janusgraph-dist -Dgpg.skip=true -DskipTests=true -Dmaven.compiler.debug=true -Ddocker.build.skip=true -DskipITs=true -Drat.skip=true
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

## Usage in JanusGraph Distribution

### Quick Setup for Distribution Package

If you have built and extracted the JanusGraph distribution (`janusgraph-full-1.2.0-SNAPSHOT`), follow these steps:

1. **Copy Required Files**:
```bash
# From the JanusGraph source directory
cp janusgraph-kvt/target/janusgraph-kvt-1.2.0-SNAPSHOT.jar janusgraph-full-1.2.0-SNAPSHOT/lib/
cp janusgraph-kvt/src/main/native/libjanusgraph-kvt-jni.so janusgraph-full-1.2.0-SNAPSHOT/lib/
```

2. **Create KVT Launcher Script**:
```bash
cat > janusgraph-full-1.2.0-SNAPSHOT/bin/gremlin-kvt.sh << 'EOF'
#!/bin/bash
JANUSGRAPH_HOME="$( cd "$( dirname "${BASH_SOURCE[0]}" )/.." && pwd )"
export JAVA_OPTIONS="-Djava.library.path=$JANUSGRAPH_HOME/lib:${JAVA_OPTIONS}"
exec "$JANUSGRAPH_HOME/bin/gremlin.sh" "$@"
EOF
chmod +x janusgraph-full-1.2.0-SNAPSHOT/bin/gremlin-kvt.sh
```

3. **Start Using KVT**:
```bash
cd janusgraph-full-1.2.0-SNAPSHOT
./bin/gremlin-kvt.sh

# In the Gremlin console:
graph = JanusGraphFactory.build().
  set('storage.backend', 'org.janusgraph.diskstorage.kvt.KVTStoreManager').
  set('storage.directory', '/tmp/janusgraph-kvt').
  open()
```

### Configuration

To use the KVT backend in your JanusGraph application:

```java
import org.janusgraph.core.JanusGraph;
import org.janusgraph.core.JanusGraphFactory;
import org.apache.commons.configuration2.BaseConfiguration;

// Create configuration
BaseConfiguration config = new BaseConfiguration();
config.setProperty("storage.backend", "org.janusgraph.diskstorage.kvt.KVTStoreManager");
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
// Use the KVT-enabled launcher
./bin/gremlin-kvt.sh

// In Gremlin console
graph = JanusGraphFactory.build().
  set('storage.backend', 'org.janusgraph.diskstorage.kvt.KVTStoreManager').
  set('storage.directory', '/tmp/janusgraph-kvt').
  open()

// Create schema
mgmt = graph.openManagement()
name = mgmt.makePropertyKey('name').dataType(String.class).make()
mgmt.commit()

// Add data
g = graph.traversal()
v1 = g.addV('person').property('name', 'Alice').next()
v2 = g.addV('person').property('name', 'Bob').next()
g.addE('knows').from(v1).to(v2).iterate()
graph.tx().commit()

// Query
g.V().has('name', 'Alice').out('knows').values('name')
```

### Using with Gremlin Server

1. **Create KVT Configuration File** (if not already present):
```properties
# conf/kvt/janusgraph-kvt-server.properties
storage.backend=org.janusgraph.diskstorage.kvt.KVTStoreManager
storage.kvt.storage-method=composite
gremlin.graph=org.janusgraph.core.JanusGraphFactory
graph.graphname=kvt_graph
```

2. **Configure Gremlin Server**:
Edit `conf/gremlin-server/gremlin-server.yaml`:
```yaml
graphs: {
  graph: conf/kvt/janusgraph-kvt-server.properties
}
```

3. **Start Server with KVT Support**:
```bash
export JAVA_OPTIONS="-Djava.library.path=$PWD/lib"
./bin/gremlin-server.sh conf/gremlin-server/gremlin-server.yaml
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
   ```
   java.lang.UnsatisfiedLinkError: no janusgraph-kvt-jni in java.library.path
   ```
   **Solutions**:
   - Use the provided launcher script: `./bin/gremlin-kvt.sh`
   - Or set the library path manually:
     ```bash
     export JAVA_OPTIONS="-Djava.library.path=$PWD/lib"
     ./bin/gremlin.sh
     ```
   - Verify the native library exists:
     ```bash
     ls lib/libjanusgraph-kvt-jni.so
     ```

2. **Class Not Found**: KVTStoreManager not found
   ```
   Could not find implementation class: org.janusgraph.diskstorage.kvt.KVTStoreManager
   ```
   **Solution**: Ensure the KVT JAR is in the lib directory:
   ```bash
   ls lib/janusgraph-kvt-*.jar
   # If missing, copy it:
   cp janusgraph-kvt/target/janusgraph-kvt-1.2.0-SNAPSHOT.jar janusgraph-full-1.2.0-SNAPSHOT/lib/
   ```

3. **Configuration Errors**: Null configuration or backend exception
   ```
   Could not execute operation due to backend exception
   ```
   **Solution**: Ensure you're using the full class name for the backend:
   ```groovy
   // Correct:
   set('storage.backend', 'org.janusgraph.diskstorage.kvt.KVTStoreManager')
   
   // Incorrect (won't work):
   set('storage.backend', 'kvt')
   ```

4. **KVT Initialization Failed**: 
   - Solution: Check that kvt_mem.o was compiled with compatible compiler
   - Verify: File exists at `janusgraph-kvt/kvt/kvt_mem.o`
   - Rebuild if needed:
     ```bash
     cd janusgraph-kvt/kvt
     g++ -c -fPIC -g -O0 kvt_mem.cpp -o kvt_mem.o
     cd ../..
     ./janusgraph-kvt/build-native.sh
     ```

5. **Transaction Conflicts**:
   - KVT uses pessimistic locking, deadlocks may occur
   - Solution: Implement retry logic or adjust transaction scope

## Verifying the Installation

To verify KVT is working correctly:

```groovy
// 1. Open a KVT-backed graph
graph = JanusGraphFactory.build().
  set('storage.backend', 'org.janusgraph.diskstorage.kvt.KVTStoreManager').
  set('storage.directory', '/tmp/janusgraph-kvt-test').
  open()

// 2. Create a simple schema
mgmt = graph.openManagement()
name = mgmt.makePropertyKey('name').dataType(String.class).make()
age = mgmt.makePropertyKey('age').dataType(Integer.class).make()
mgmt.commit()

// 3. Add test data
g = graph.traversal()
alice = g.addV('person').property('name', 'Alice').property('age', 30).next()
bob = g.addV('person').property('name', 'Bob').property('age', 25).next()
g.addE('knows').from(alice).to(bob).property('since', 2020).iterate()
graph.tx().commit()

// 4. Query the data
println "People in graph: " + g.V().hasLabel('person').count().next()
println "Alice knows: " + g.V().has('name', 'Alice').out('knows').values('name').toList()

// 5. Clean up
graph.close()
println "SUCCESS: KVT backend is working!"
```

Expected output:
```
People in graph: 2
Alice knows: [Bob]
SUCCESS: KVT backend is working!
```

## Performance Considerations

The current implementation uses the in-memory KVT backend (`kvt_mem.o`). For production use:

1. **Memory Usage**: Data is stored in memory, so ensure adequate heap space
2. **Persistence**: Current implementation does not persist data across restarts
3. **Concurrency**: KVT uses pessimistic locking (2PL) which may impact concurrent write performance
4. **Batch Operations**: Use batch mutations when possible for better performance

## Future Enhancements

- Support for additional KVT backend implementations beyond kvt_memory
- Configuration options for tuning transaction isolation levels
- Metrics and monitoring integration
- Support for KVT-specific features like batch operations optimization
- Integration with persistent KVT implementations

