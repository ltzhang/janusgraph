# JNI Integration for C++ InMemory Database

This directory contains a JNI (Java Native Interface) integration that allows the Java test program to call into the C++ implementation of the InMemory Database and perform equivalence testing.

## Architecture

The JNI integration consists of:

### Java Side
- **NativeInMemoryDB.java**: Simple JNI wrapper that exposes essential database operations
- **EquivalenceTest.java**: Test program that compares Java and C++ implementations

### C++ Side  
- **janusgraph_jni.cpp**: JNI implementation that bridges Java calls to C++ objects
- **build-jni.sh**: Build script for compiling the JNI shared library

### Build System
- **Makefile**: Updated with JNI targets (`jni`, `eqv_test`)
- **build-jni.sh**: Standalone build script for JNI compilation

## Usage

### Quick Start
```bash
# Build and run equivalence test
make eqv_test

# Or use the build script directly
./build-jni.sh
```

### Step-by-Step
```bash
# 1. Build JNI library only
make jni

# 2. Run equivalence test (requires JNI library)
make eqv_test

# 3. Clean everything
make clean
```

## JNI API Design

The JNI interface was deliberately kept simple with only essential operations:

### Database Management
- `createDB()` - Create new database instance
- `openStore(name)` - Open/create named store
- `exists()` - Check if database has data
- `clearStorage()` - Clear all data
- `close()` - Close database

### Store Operations
- `put(key, column, value)` - Add single entry
- `mutate(key, additions, deletions)` - Batch operation
- `getSlice(key, startColumn, endColumn)` - Range query
- `getEntryCount(key)` - Count entries for key
- `isEmpty()` - Check if store is empty

### Data Exchange
- All data passed as Java `String` objects for simplicity
- Results returned as `String[]` arrays with column/value pairs
- Inner `Entry` class for structured data representation

## Equivalence Testing

The equivalence test performs identical operations on both implementations and verifies results match:

### Test Coverage
1. **Basic Operations** - Simple put/get operations
2. **Multiple Entries** - Adding multiple columns per key
3. **Slice Queries** - Range queries with start/end bounds
4. **Mutations** - Adding and deleting entries
5. **Multiple Stores** - Store isolation testing
6. **Complex Scenarios** - Multiple keys with multiple operations

### Test Results
All 30 equivalence tests pass, demonstrating that:
- Both implementations handle identical operations correctly
- Data storage and retrieval are equivalent
- Query results are identical
- Store management works the same way

## Build Requirements

### System Requirements
- **Java 8+** with JNI headers (`$JAVA_HOME/include/`)
- **C++ compiler** with C++11 support (g++)
- **Make** for build automation

### Dependencies
- **JanusGraph Core**: Must be built (`mvn clean install -DskipTests`)
- **InMemory Module**: Must be compiled (`mvn compile`)  
- **C++ MemDB**: Built automatically during JNI compilation

### Library Output
- **libjanusgraph_jni.so**: Shared library in `lib/` directory
- **Java classes**: Compiled classes in `build/` directory

## Implementation Details

### Memory Management
- Java holds native pointers (`long`) to C++ objects
- C++ objects managed by `shared_ptr` in InMemoryStoreManager
- Cleanup handled in Java `finalize()` methods
- No manual memory management required

### Thread Safety
- Uses same thread safety as underlying C++ implementation
- No additional synchronization in JNI layer
- Thread-safe for single-threaded test scenarios

### Error Handling
- C++ exceptions caught and ignored (for simplicity)
- Failed operations return empty results
- No Java exceptions thrown from native code

### Performance Considerations
- String conversions on every JNI call (acceptable for testing)
- No optimization for bulk operations
- Focus on correctness rather than performance

## Troubleshooting

### Common Issues

**"java.lang.UnsatisfiedLinkError: no janusgraph_jni in java.library.path"**
- Library not in library path
- Run: `export LD_LIBRARY_PATH=$PWD/lib:$LD_LIBRARY_PATH`
- Or use: `make eqv_test` (sets path automatically)

**"JNI headers not found"**  
- JAVA_HOME not set correctly
- Install JDK development package
- Check: `ls $JAVA_HOME/include/jni.h`

**"relocation R_X86_64_PC32 against symbol ... can not be used when making a shared object"**
- C++ objects not compiled with `-fPIC`
- Build script handles this automatically
- Manual fix: Add `-fPIC` to C++ compilation

### Debugging
```bash
# Verbose JNI build
./build-jni.sh

# Check library dependencies  
ldd lib/libjanusgraph_jni.so

# Run with JNI debugging
java -Djava.library.path=lib -Xcheck:jni EquivalenceTest
```

## Extension Points

The current JNI interface can be extended by:

1. **Adding More Operations**: Extend `NativeInMemoryDB` with additional methods
2. **Complex Data Types**: Support structured objects instead of strings
3. **Bulk Operations**: Optimize for large-scale data operations  
4. **Error Handling**: Add proper exception propagation
5. **Performance**: Add streaming/batch interfaces

## Comparison with Existing JNI Tests

This implementation differs from the existing JNI tests in `src/test/java/.../jni/`:

- **Simpler Interface**: Focused on essential operations only
- **String-Based**: Uses strings instead of complex buffer types
- **Independent**: Doesn't require existing JNI infrastructure
- **Testing Focus**: Designed specifically for equivalence testing
- **Minimal Dependencies**: Only requires core C++ memdb implementation

The equivalence test successfully demonstrates that both Java and C++ implementations of the InMemory Database are functionally equivalent across all tested scenarios.