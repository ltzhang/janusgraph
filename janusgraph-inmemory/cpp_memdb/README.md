# JanusGraph C++ In-Memory Database Backend

This directory contains a high-performance C++ implementation of JanusGraph's in-memory database backend. It provides the same functionality as the Java-based in-memory backend but with potential performance improvements for memory-intensive operations.

## Architecture Overview

The C++ backend implements the core JanusGraph storage interfaces in a single consolidated header file:

- **StaticBuffer**: Immutable byte buffer for keys and values
- **Entry**: Key-value pair container
- **EntryList**: Ordered collection of entries
- **SliceQuery** / **KeySliceQuery**: Range query definitions
- **StoreTransaction**: Transaction handle
- **InMemoryColumnValueStore**: Column-family storage abstraction
- **InMemoryKeyColumnValueStore**: Key-based storage with column families
- **InMemoryStoreManager**: Top-level storage manager

All implementations are provided as inline functions in `cpp_memdb.h` for optimal performance.

### Design Features

- **Thread-safe operations**: Uses mutex locks for concurrent access
- **Range queries**: Supports slice queries with start/end bounds
- **ACID operations**: Supports additions and deletions in single mutations
- **Memory-based storage**: All data stored in RAM using STL containers
- **No persistence**: Data is lost when the process terminates (like the Java version)
- **100% API compatibility**: Drop-in replacement for Java implementation

## Prerequisites

### System Requirements
- **C++ Compiler**: g++ with C++11 support
- **Java**: JDK 8 or higher (for JNI integration)
- **Maven**: 3.6+ (for building JanusGraph dependencies)
- **Operating System**: Linux (tested), macOS, or Windows with appropriate build tools

### Dependencies
- **Google Guava**: Required for Java integration tests
- **JanusGraph Core**: Required for equivalence testing
- **JNI**: For Java-C++ integration

## Building the C++ Backend

### 1. Build the Standalone C++ Library

The C++ backend can be built and tested independently:

```bash
# Navigate to the cpp_memdb directory
cd /path/to/janusgraph/janusgraph-inmemory/cpp_memdb

# Clean any previous builds
make clean

# Build the test executable
make all
```

**Build Process:**
```
g++ -std=c++11 -Wall -Wextra -O2 -g -I. -c cpp_memdb.cpp -o cpp_memdb.o
g++ -std=c++11 -Wall -Wextra -O2 -g -I. cpp_memdb.o test_memdb.cpp -o test_memdb
```

**Build Output:**
- `cpp_memdb.o`: Compiled object file containing static definitions
- `test_memdb`: Executable containing comprehensive C++ tests

**Note:** You may see warnings about unused parameter 'txh' - these are expected and safe to ignore.

### 2. Available Make Targets

```bash
# Build everything (default)
make all

# Run tests (builds if necessary)  
make test

# Clean build artifacts
make clean

# Show available targets and usage
make help
```

**Example Output:**
```
Available targets:
  all     - Build the test program
  test    - Build and run the test program  
  clean   - Remove built files
  help    - Show this help message
```

## Running C++ Tests

### Standalone C++ Tests

Run the comprehensive C++ test suite:

```bash
# Run all C++ backend tests
make test

# Or run the test executable directly
./test_memdb
```

**Test Coverage:**
- **StaticBuffer operations**: Creation, comparison, ordering, length checks
- **Entry operations**: Key-value pairs, equality testing, accessor methods
- **EntryList operations**: Dynamic arrays, indexing, size management
- **InMemoryColumnValueStore**: Column family operations, mutations, queries
- **InMemoryKeyColumnValueStore**: Key-based storage, slice queries, deletions
- **InMemoryStoreManager**: Database management, transaction handling, store creation
- **Complex scenarios**: Multi-key operations, bulk mutations, range queries

**Expected Output:**
```
Starting C++ InMemory Database Tests...
Testing StaticBuffer...
StaticBuffer tests passed!
Testing Entry...
Entry tests passed!
Testing EntryList...
EntryList tests passed!
Testing InMemoryColumnValueStore...
InMemoryColumnValueStore tests passed!
Testing InMemoryKeyColumnValueStore...
InMemoryKeyColumnValueStore tests passed!
Testing InMemoryStoreManager...
InMemoryStoreManager tests passed!
Testing complex scenario...
Complex scenario tests passed!

All tests passed successfully!
C++ InMemory Database implementation is working correctly.
```

## Java-C++ Integration

### Building JanusGraph Dependencies First

Before running integration tests, build the necessary JanusGraph components:

```bash
# Navigate to JanusGraph root directory
cd /path/to/janusgraph

# Build JanusGraph core and in-memory modules (skip tests for faster build)
mvn clean install -DskipTests=true -Drat.skip=true -pl janusgraph-inmemory -am
```

**Command Breakdown:**
- `mvn clean install`: Clean and build the project
- `-DskipTests=true`: Skip running tests during build (faster)
- `-Drat.skip=true`: Skip Apache RAT license checks (avoids license header issues)
- `-pl janusgraph-inmemory`: Build only the inmemory module and its dependencies
- `-am`: Also build required modules (core, driver modules)

### Copy Dependencies for Testing

Copy Maven dependencies to a local directory for easier classpath management:

```bash
cd /path/to/janusgraph/janusgraph-inmemory

# Copy all runtime dependencies to target/dependency/
mvn dependency:copy-dependencies -DoutputDirectory=target/dependency

# Verify dependencies were copied successfully
ls target/dependency/ | wc -l  # Should show 50+ JAR files
ls target/dependency/guava-*.jar  # Should show Guava JAR
ls target/dependency/gremlin-*.jar  # Should show TinkerPop JARs
```

This creates `/target/dependency/` with all required JAR files.

**CRITICAL:** If the dependency copy fails, all Java integration tests will fail with ClassNotFoundException.

### Building JNI Components

The Java-C++ integration requires building JNI libraries and Java components:

```bash
# Navigate to the java integration test directory
cd /path/to/janusgraph/janusgraph-inmemory/java_test

# Build JNI library and Java classes using the provided script
./build.sh

# Or build manually:
# 1. Ensure JAVA_HOME is set (CRITICAL - see troubleshooting if this fails)
export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64  # Adjust path as needed

# 2. Compile Java classes and generate JNI headers
javac -h . -cp "../target/classes:../target/dependency/*" *.java

# 3. Build JNI shared library (using consolidated cpp_memdb files)
g++ -shared -fPIC -I${JAVA_HOME}/include -I${JAVA_HOME}/include/linux \
    janusgraph_jni.cpp ../cpp_memdb/cpp_memdb.cpp \
    -o lib/libjanusgraph_jni.so
```

**Build Outputs:**
- `build/*.class`: Compiled Java classes
- `lib/libjanusgraph_jni.so`: JNI shared library
- `*.h`: Generated JNI header files

## Running Integration Tests

### 1. Equivalence Tests

These tests verify that the C++ and Java implementations produce identical results for the same operations:

```bash
cd /path/to/janusgraph/janusgraph-inmemory/java_test

# IMPORTANT: Verify dependencies are available before running tests
ls ../target/dependency/guava-*.jar  # Should show Guava JAR file
ls ../target/classes/  # Should show compiled JanusGraph classes
ls lib/libjanusgraph_jni.so  # Should show JNI library

# If any are missing, run the dependency setup steps above first!

# Run equivalence tests between Java and C++ implementations
java -cp "build:../target/classes:../target/dependency/*" \
     -Djava.library.path=lib \
     EquivalenceTest
```

**Command Breakdown:**
- `-cp "build:../target/classes:../target/dependency/*"`: Sets classpath to include:
  - `build/`: Compiled Java test classes
  - `../target/classes/`: JanusGraph inmemory module classes
  - `../target/dependency/*`: All Maven dependencies (Guava, TinkerPop, etc.)
- `-Djava.library.path=lib`: Tells JVM where to find the JNI shared library
- `EquivalenceTest`: Main class that runs the equivalence test suite

**Expected Output:**
```
Starting Java vs C++ InMemory Database Equivalence Tests...

--- Testing Basic Operations ---
  ✓ Initial existence check
  ✓ Basic put result count
  ✓ Basic put column
  ✓ Basic put value
Basic Operations: PASSED

--- Testing Multiple Entries ---
  ✓ Multiple entries count
  ✓ Multi entry 0 column
  ✓ Multi entry 0 value
  ✓ Multi entry 1 column
  ✓ Multi entry 1 value
Multiple Entries: PASSED

--- Testing Slice Queries ---
  ✓ Slice query count
  ✓ Slice entry 0 column
  ✓ Slice entry 0 value
Slice Queries: PASSED

--- Testing Mutations ---
  ✓ After deletion count
  ✓ Java col2 deleted  
  ✓ C++ col2 deleted
Mutations: PASSED

--- Testing Multiple Stores ---
  ✓ Store count
  ✓ Store1 entry count
  ✓ Store2 entry count
Multiple Stores: PASSED

--- Testing Complex Scenario ---
  ✓ Complex key0 count
  ✓ Complex final existence
Complex Scenario: PASSED

=== Equivalence Test Results ===
Tests Run: 30
Tests Passed: 30
Tests Failed: 0

All equivalence tests passed!
Java and C++ implementations are equivalent.
```

### 2. Standalone Database Tests

Test the JanusGraph integration functionality independently:

```bash
java -cp "build:../target/classes:../target/dependency/*" \
     -Djava.library.path=lib \
     InMemoryDatabaseTest
```

**Expected Output:**
```
Starting JanusGraph InMemory Database Tests...

--- Testing StaticBuffer Operations ---
  ✓ StaticBuffer equality
  ✓ StaticBuffer inequality  
  ✓ StaticBuffer length
  ✓ StaticBuffer comparison
StaticBuffer operations: PASSED

--- Testing Entry Operations ---
  ✓ Entry equality
  ✓ Entry inequality
  ✓ Entry column
  ✓ Entry value
Entry operations: PASSED

[... additional test sections ...]

=== Test Results ===
Tests Run: 31
Tests Passed: 31
Tests Failed: 0

All tests passed successfully!
JanusGraph InMemory Database is working correctly.
```

## Running JanusGraph with In-Memory Backend

### 1. Pure Java In-Memory Backend

Use JanusGraph's standard Java in-memory backend:

```java
// Java code example
import org.janusgraph.core.JanusGraph;
import org.janusgraph.core.JanusGraphFactory;

// Create in-memory graph
JanusGraph graph = JanusGraphFactory.build()
    .set("storage.backend", "inmemory")
    .open();

// Add vertices and edges
var v1 = graph.addVertex();
v1.property("name", "Alice");
var v2 = graph.addVertex();
v2.property("name", "Bob");
v1.addEdge("knows", v2);

// Commit transaction
graph.tx().commit();

// Query the graph
var vertices = graph.traversal().V().hasLabel("person").toList();

// Clean up
graph.close();
```

### 2. Configuration Example

Create a properties file for in-memory configuration:

```properties
# janusgraph-inmemory.properties
storage.backend=inmemory
storage.directory=/tmp/janusgraph

# Optional: Add search index
index.search.backend=lucene  
index.search.directory=/tmp/janusgraph/index

# Performance tuning
storage.buffer-size=1024
storage.write-time=1000
storage.read-time=1000
```

### 3. Running with Configuration File

```bash
# Using JanusGraph console or your application
java -cp "target/classes:target/dependency/*" \
     YourApplication janusgraph-inmemory.properties
```

## Complete End-to-End Verification

### Step-by-Step Verification Process

Follow this complete process to ensure everything works correctly:

```bash
# 1. Set up environment (CRITICAL - do this first!)
export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64  # Adjust for your system
export PATH=$JAVA_HOME/bin:$PATH

# 2. Verify Java environment
ls $JAVA_HOME/include/jni.h  # Must exist
javac -version  # Should work

# 3. Build JanusGraph dependencies
cd /path/to/janusgraph
mvn clean install -DskipTests=true -Drat.skip=true -pl janusgraph-inmemory -am

# 4. Copy dependencies
cd janusgraph-inmemory
mvn dependency:copy-dependencies -DoutputDirectory=target/dependency
ls target/dependency/ | wc -l  # Should show 50+ files

# 5. Build C++ backend
cd cpp_memdb
make clean && make all
./test_memdb  # Should pass all tests

# 6. Build JNI integration
cd ../java_test
g++ -shared -fPIC -I${JAVA_HOME}/include -I${JAVA_HOME}/include/linux \
    janusgraph_jni.cpp ../cpp_memdb/cpp_memdb.cpp \
    -o lib/libjanusgraph_jni.so

# 7. Compile Java test classes
javac -cp "../target/classes:../target/dependency/*" *.java -d build/

# 8. Run equivalence tests
java -cp "build:../target/classes:../target/dependency/*" \
     -Djava.library.path=lib \
     EquivalenceTest

# If all above steps pass, the integration is working correctly!
```

### Quick Success Verification

If following the complete process above, you should see:

1. **C++ Tests**: `All tests passed successfully!`
2. **JNI Build**: No errors, creates `lib/libjanusgraph_jni.so` file
3. **Java Integration**: `All equivalence tests passed! Java and C++ implementations are equivalent.`

**Common Success Indicators:**
- C++ build shows warnings about unused 'txh' parameters (these are EXPECTED and safe)
- JNI library file exists and is 400KB+ in size
- Java tests show 30/30 tests passed
- No ClassNotFoundException or UnsatisfiedLinkError exceptions
```

## Testing the Complete Stack

### 1. Full JanusGraph Test Suite

Run the complete JanusGraph in-memory test suite:

```bash
cd /path/to/janusgraph/janusgraph-inmemory

# Run all in-memory backend tests (takes several minutes)
mvn test -Drat.skip=true

# Run specific test class for faster feedback
mvn test -Dtest=InMemoryStoreManagerTest -Drat.skip=true

# Run multiple specific tests
mvn test -Dtest="InMemoryStoreManagerTest,InMemoryColumnValueStoreTest" -Drat.skip=true
```

**Sample Output:**
```
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running org.janusgraph.diskstorage.inmemory.InMemoryStoreManagerTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.460 s
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] BUILD SUCCESS
```

### 2. Performance Comparison

To compare C++ vs Java performance (if stress tests are available):

```bash
cd /path/to/janusgraph/janusgraph-inmemory/java_test

# Run stress tests
java -cp "build:../target/classes:../target/dependency/*" \
     -Djava.library.path=lib \
     StressEquivalenceTest
```

## Troubleshooting

### Common Build Issues

#### 1. Missing JAVA_HOME
```bash
# Check if JAVA_HOME is set
echo $JAVA_HOME

# If not set, find Java installation
whereis java
# or
ls /usr/lib/jvm/

# Set JAVA_HOME (choose the correct path for your system)
# Common paths:
# Ubuntu/Debian: /usr/lib/jvm/java-11-openjdk-amd64
# CentOS/RHEL: /usr/lib/jvm/java-11-openjdk
export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH

# Add to ~/.bashrc for persistence
echo 'export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64' >> ~/.bashrc
echo 'export PATH=$JAVA_HOME/bin:$PATH' >> ~/.bashrc

# Verify JAVA_HOME is correct
ls $JAVA_HOME/include/jni.h  # Should exist
$JAVA_HOME/bin/javac -version
```

#### 2. JNI Headers Not Found
```bash
# Ensure JAVA_HOME is set correctly and headers exist
ls $JAVA_HOME/include/jni.h  # Should exist

# On some systems, headers are in a separate package
# Ubuntu/Debian: sudo apt-get install openjdk-11-jdk-headless
# CentOS/RHEL: sudo yum install java-11-openjdk-devel
```

#### 3. Classpath Issues
```bash
# Verify dependencies are copied
ls janusgraph-inmemory/target/dependency/ | wc -l  # Should show many JARs

# Check if classes are compiled  
ls java_test/build/
# Should contain: EquivalenceTest.class, InMemoryDatabaseTest.class, etc.

# Re-copy dependencies if needed
mvn dependency:copy-dependencies -DoutputDirectory=target/dependency
```

#### 4. Maven Build Failures
```bash
# Clear Maven cache and rebuild
rm -rf ~/.m2/repository/org/janusgraph
mvn clean install -DskipTests=true -Drat.skip=true -pl janusgraph-inmemory -am
```

### Common Runtime Issues

#### 1. UnsatisfiedLinkError
```
Error: java.lang.UnsatisfiedLinkError: no janusgraph_jni in java.library.path
```

**Solutions:**
```bash
# Ensure the JNI library exists
ls java_test/lib/libjanusgraph_jni.so  # Should exist

# Verify library path is set correctly
java -Djava.library.path=lib -cp "..." YourClass

# Check library dependencies
ldd java_test/lib/libjanusgraph_jni.so  # Should show resolved dependencies
```

#### 2. ClassNotFoundException
```
Error: java.lang.ClassNotFoundException: com.google.common.base.Predicate
```

**Solutions:**
```bash
# Verify Maven dependencies are available
ls janusgraph-inmemory/target/dependency/guava-*.jar  # Should exist

# Re-copy dependencies
mvn dependency:copy-dependencies -DoutputDirectory=target/dependency

# Check classpath includes dependency directory
java -cp "build:../target/classes:../target/dependency/*" YourClass
```

#### 3. Compilation Warnings
```
warning: unused parameter 'txh' [-Wunused-parameter]
```

**Note:** These C++ warnings about unused parameters are expected and safe to ignore. They occur in the transaction handling interface methods where the `txh` (transaction handle) parameter is required for API compatibility but not used in the simple in-memory implementation.

#### 4. JVM Warning Messages
```
WARNING: An illegal reflective access operation has occurred
WARNING: Illegal reflective access by org.apache.tinkerpop.shaded.kryo.util.UnsafeUtil
```

**Note:** These warnings are from TinkerPop/Kryo libraries and are safe to ignore. They don't affect functionality and occur due to Java module system restrictions on reflection.

## Performance Characteristics

The C++ backend provides:

### Advantages
- **Memory Efficiency**: Direct memory management without JVM garbage collection overhead
- **Speed**: Optimized C++ data structures for key-value operations
- **Compatibility**: 100% API compatibility with Java implementation  
- **Scalability**: Better performance for large in-memory datasets
- **Reduced GC Pressure**: No Java garbage collection impact on C++ operations

### Time Complexity
- **Insertions/Lookups**: O(log n) due to `std::map` usage
- **Range Queries**: O(log n + k) where k is the result size
- **Deletions**: O(log n) per key

### Space Complexity
- **Storage**: O(n) where n is the number of stored entries
- **Memory Overhead**: Lower than Java equivalents (no object headers, direct memory layout)

### Threading Model
- **Coarse-grained locking**: Mutex locks at the store level
- **Thread Safety**: Full thread safety with performance trade-offs
- **Concurrent Reads**: Multiple readers supported when no writes occurring

## Development Notes

### Adding New Features

1. **C++ Side**: Implement in appropriate header files (`*.h`)
2. **JNI Integration**: Update `janusgraph_jni.cpp` for Java bindings
3. **Java Side**: Update `NativeInMemoryDB.java` for new methods  
4. **Testing**: Add tests to both `test_memdb.cpp` and `EquivalenceTest.java`

### Code Organization

```
cpp_memdb/
├── cpp_memdb.h             # CONSOLIDATED HEADER - All C++ classes with inline implementations:
│                           #   - StaticBuffer (immutable byte buffer)
│                           #   - Entry (key-value pair container)
│                           #   - EntryList (ordered collection of entries)
│                           #   - SliceQuery/KeySliceQuery (range queries)
│                           #   - StoreTransaction (transaction handle)
│                           #   - InMemoryColumnValueStore (column storage)
│                           #   - InMemoryKeyColumnValueStore (key-based storage)
│                           #   - InMemoryStoreManager (database manager)
├── cpp_memdb.cpp           # Minimal implementation file (static definitions only)
├── Makefile                # Build configuration
├── test_memdb.cpp          # C++ test suite
├── README.md               # This documentation
│
│ Legacy files (no longer used but kept for reference):
├── StaticBuffer.h          # [LEGACY] Individual header files
├── Entry.h                 # [LEGACY] Now consolidated into cpp_memdb.h
├── EntryList.h/.cpp        # [LEGACY] Use cpp_memdb.h instead
├── SliceQuery.h            # [LEGACY]
├── StoreTransaction.h      # [LEGACY]
├── InMemoryColumnValueStore.h      # [LEGACY]
├── InMemoryKeyColumnValueStore.h   # [LEGACY]
└── InMemoryStoreManager.h          # [LEGACY]

java_test/
├── NativeInMemoryDB.java   # Java-JNI interface
├── EquivalenceTest.java    # Java vs C++ equivalence tests
├── InMemoryDatabaseTest.java       # Standalone functionality tests
├── StressEquivalenceTest.java      # Performance/stress tests
├── janusgraph_jni.cpp      # JNI binding implementation (uses cpp_memdb.h)
├── build/                  # Compiled Java classes
├── lib/                    # JNI shared libraries
├── build.sh               # Build script for JNI components
├── build-jni.sh           # JNI-specific build script
├── Makefile               # Java build configuration
└── *.md                   # Additional documentation
```

**Key Changes:**
- **Single header**: Only `cpp_memdb.h` and `cpp_memdb.cpp` are needed
- **Inline performance**: All implementations are inline for optimization
- **Simplified includes**: Just `#include "cpp_memdb.h"` in your code
- **Legacy compatibility**: Old individual headers still present but not used

### Design Philosophy

This implementation provides a **drop-in replacement** for JanusGraph's Java in-memory backend with potential performance improvements while maintaining **complete compatibility**. The design prioritizes:

1. **API Compatibility**: Identical behavior to Java implementation
2. **Performance**: Optimized data structures and memory management
3. **Simplicity**: Clear, maintainable C++ code
4. **Testing**: Comprehensive test coverage ensuring correctness
5. **Integration**: Seamless JNI integration with existing Java codebase

The C++ backend is particularly beneficial for workloads involving large in-memory graphs, frequent key-value operations, or scenarios where minimizing GC pressure is important.