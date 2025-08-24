# C++ In-Memory Database Implementation

This is a C++ implementation of the Java in-memory database from JanusGraph's `janusgraph-inmemory` module. It provides the same core functionality as the Java version while using `std::map` and `std::unordered_map` for data storage.

## Architecture

The implementation consists of several key classes that mirror the Java structure:

### Core Classes

- **StaticBuffer**: Represents immutable byte arrays, similar to Java's StaticBuffer
- **Entry**: Represents a column-value pair
- **EntryList**: A collection of entries with iterator support
- **SliceQuery**: Defines a range query for columns
- **KeySliceQuery**: Combines a key with a slice query
- **StoreTransaction**: Represents a database transaction (simplified)

### Storage Classes

- **InMemoryColumnValueStore**: Stores column-value mappings for a single key using `std::map<StaticBuffer, StaticBuffer>`
- **InMemoryKeyColumnValueStore**: Manages multiple column-value stores, one per key
- **InMemoryStoreManager**: Top-level manager that handles multiple named stores

## Features

- **Thread-safe operations**: Uses mutex locks for concurrent access
- **Range queries**: Supports slice queries with start/end bounds
- **ACID operations**: Supports additions and deletions in single mutations
- **Memory-based storage**: All data stored in RAM using STL containers
- **No persistence**: Data is lost when the process terminates (like the Java version)

## Building

The project uses a simple Makefile:

```bash
make          # Build the test program
make test     # Build and run tests
make clean    # Clean build artifacts
make help     # Show available targets
```

## Testing

The `test_memdb.cpp` file contains comprehensive tests covering:

- Basic StaticBuffer operations
- Entry creation and comparison
- EntryList management
- Column-value store operations
- Key-column-value store operations
- Store manager functionality
- Complex scenarios with multiple keys and operations

Run tests with:
```bash
make test
```

## Usage Example

```cpp
#include "InMemoryStoreManager.h"

// Create store manager
InMemoryStoreManager manager;

// Open a database
auto store = manager.openDatabase("mystore");

// Begin transaction
auto txh = manager.beginTransaction();

// Add data
StaticBuffer key("user1");
std::vector<Entry> additions;
additions.emplace_back(StaticBuffer("name"), StaticBuffer("John"));
additions.emplace_back(StaticBuffer("age"), StaticBuffer("30"));

std::vector<StaticBuffer> deletions;
store->mutate(key, additions, deletions, *txh);

// Query data
SliceQuery slice(StaticBuffer("age"), StaticBuffer("name"));
KeySliceQuery keySlice(key, slice);
EntryList result = store->getSlice(keySlice, *txh);
```

## Design Decisions

1. **std::map over std::unordered_map**: Used `std::map` for ordered iteration, which is important for range queries
2. **Shared pointers**: Used for automatic memory management of store objects
3. **Mutex-based threading**: Simple mutex locks for thread safety (similar to Java's ReentrantLock)
4. **STL containers**: Leveraged standard library containers instead of custom paging/buffering logic
5. **Simplified transactions**: Basic transaction support without full ACID guarantees

## Performance Characteristics

- **Time Complexity**: O(log n) for insertions/lookups due to `std::map`
- **Space Complexity**: O(n) where n is the number of stored entries
- **Thread Safety**: Coarse-grained locking at the store level

## Limitations

- No persistence (in-memory only)
- No advanced features like fragmentation reports or snapshots
- Simplified locking model compared to Java version
- No page-based memory management

## Compatibility

- C++11 or later
- Standard library containers
- POSIX threads (for mutex support)