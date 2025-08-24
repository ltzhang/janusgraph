# Java Test for JanusGraph InMemory Database

This directory contains a comprehensive Java test program for the JanusGraph InMemory Database implementation. The test program validates all core functionality without requiring external test frameworks like JUnit.

## Files

- **InMemoryDatabaseTest.java** - Main test program with comprehensive test coverage
- **Makefile** - GNU Make build system for compiling and running tests
- **build.sh** - Shell script alternative for building and running tests
- **README.md** - This documentation file

## Prerequisites

Before running the Java tests, you need to build the required JanusGraph components:

### 1. Build JanusGraph Core
```bash
cd ../..  # Go to janusgraph root directory
mvn clean install -DskipTests
```

### 2. Compile InMemory Module
```bash
cd ..  # Go to janusgraph-inmemory directory
mvn compile
```

## Running the Tests

You have several options for building and running the tests:

### Option 1: Using Make
```bash
make test     # Build and run tests
make build    # Build only
make run      # Run tests (builds if needed)
make clean    # Clean build artifacts
make help     # Show help
```

### Option 2: Using Build Script
```bash
./build.sh test    # Build and run tests (default)
./build.sh build   # Build only
./build.sh run     # Run tests (builds if needed)
./build.sh clean   # Clean build artifacts
./build.sh help    # Show help
```

### Option 3: Manual Compilation
```bash
# Create build directory
mkdir -p build/classes

# Compile (adjust paths as needed)
javac -cp "../target/classes:../../janusgraph-core/target/janusgraph-core-1.2.0-SNAPSHOT.jar" \
      -d build/classes \
      InMemoryDatabaseTest.java

# Run
java -cp "build/classes:../target/classes:../../janusgraph-core/target/janusgraph-core-1.2.0-SNAPSHOT.jar" \
     InMemoryDatabaseTest
```

## Test Coverage

The test program covers the following functionality:

### Core Components
- **StaticBuffer Operations** - Buffer creation, equality, comparison
- **Entry Operations** - Entry creation, column/value access
- **InMemoryStoreManager** - Store creation, management, lifecycle
- **KeyColumnValueStore** - Basic storage operations

### Advanced Features
- **Mutations** - Adding and deleting entries
- **Slice Queries** - Range queries with start/end bounds
- **Multiple Stores** - Store isolation and management
- **Complex Scenarios** - Batch mutations across multiple stores
- **Snapshot Functionality** - Storage clearing and management

### Test Methods

1. **testStaticBufferOperations()** - Tests buffer creation and comparison
2. **testEntryOperations()** - Tests entry creation and access
3. **testInMemoryStoreManager()** - Tests store manager functionality
4. **testKeyColumnValueStore()** - Tests basic store operations
5. **testMutations()** - Tests data modifications
6. **testSliceQueries()** - Tests range queries
7. **testMultipleStores()** - Tests store isolation
8. **testComplexScenario()** - Tests batch operations
9. **testSnapshotFunctionality()** - Tests storage management

## Expected Output

When successful, the tests will output:
```
Starting JanusGraph InMemory Database Tests...

--- Testing StaticBuffer Operations ---
  ✓ StaticBuffer equality
  ✓ StaticBuffer inequality
  ✓ StaticBuffer length
  ✓ StaticBuffer comparison
StaticBuffer operations: PASSED

... (more test sections) ...

=== Test Results ===
Tests Run: XX
Tests Passed: XX
Tests Failed: 0

All tests passed successfully!
JanusGraph InMemory Database is working correctly.
```

## Troubleshooting

### Common Issues

**"JanusGraph core JAR not found"**
- Build the core project: `cd ../.. && mvn clean install -DskipTests`

**"InMemory classes not found"**
- Compile the inmemory module: `cd .. && mvn compile`

**"java.lang.ClassNotFoundException"**
- Ensure all dependencies are built and classpaths are correct
- Try cleaning and rebuilding: `make clean && make test`

**"java.lang.NoClassDefFoundError"**
- May indicate missing transitive dependencies
- Consider using Maven for complex dependency management

### Environment Variables

- **JAVA_HOME** - Java installation directory (auto-detected if not set)

### Limitations

This simple build system:
- Does not handle transitive dependencies automatically
- Assumes standard Maven directory layout
- May require manual classpath adjustments for complex setups
- Does not support different Java versions or profiles

For production builds, consider using Maven or Gradle instead of this simplified build system.

## Architecture

The test program is structured as follows:

```
InMemoryDatabaseTest
├── testStaticBufferOperations()
├── testEntryOperations()  
├── testInMemoryStoreManager()
├── testKeyColumnValueStore()
├── testMutations()
├── testSliceQueries()
├── testMultipleStores()
├── testComplexScenario()
└── testSnapshotFunctionality()
```

Each test method is independent and includes its own setup/cleanup. The test uses simple assertion methods for validation without external dependencies.

## Integration with Existing Tests

This test program complements the existing JUnit-based tests in the `src/test/java` directory by:

- Providing a framework-independent test suite
- Offering simple command-line execution
- Demonstrating API usage patterns
- Serving as integration test examples

The test validates the same core functionality tested by the existing JUnit tests but in a more accessible, dependency-free format.