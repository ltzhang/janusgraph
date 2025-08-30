# KVT - Key-Value Transaction Store

A lightweight, transactional key-value store implementation with ACID properties, designed for use as a storage backend for graph databases like JanusGraph.

## Directory Structure

```
kvt/
├── README.md                      # This file
├── kvt_inc.h                      # KVT public API interface
├── kvt_mem.h/.cpp                 # KVT memory-based implementation
├── kvt_sample.cpp                 # Basic usage example
├── build.sh                       # Build script for KVT core
├── Makefile                       # Make-based build system
└── janusgraph/                    # JanusGraph adapter
    ├── README.md                  # Detailed adapter documentation
    ├── janusgraph_kvt_adapter.h   # JanusGraph key-column-value adapter
    ├── janusgraph_kvt_test.cpp    # Comprehensive test suite
    ├── build.sh                   # Build script for adapter
    └── Makefile                   # Make-based build for adapter
```

## Quick Start

### Building KVT Core

```bash
# Using build script
./build.sh

# Or using Make
make all

# Run basic sample
./kvt_sample
```

### Building JanusGraph Adapter

```bash
# From kvt directory
make test-janusgraph

# Or manually
cd janusgraph
./build.sh
./janusgraph_kvt_test
```

## Core Features

- **Transactional Operations**: Full ACID compliance with begin/commit/rollback
- **Table Management**: Support for hash and range partitioned tables
- **Range Scans**: Efficient range queries on sorted data
- **Thread-Safe**: Concurrent transaction support with proper locking
- **Simple API**: Clean C++ interface for easy integration

## API Overview

```cpp
#include "kvt_inc.h"

// Initialize system
kvt_initialize();

// Create table
uint64_t table_id = kvt_create_table("my_table", "hash", error);

// Start transaction
uint64_t tx_id = kvt_start_transaction(error);

// Perform operations
kvt_set(tx_id, "my_table", "key1", "value1", error);
kvt_get(tx_id, "my_table", "key1", value, error);

// Commit
kvt_commit_transaction(tx_id, error);

// Cleanup
kvt_shutdown();
```

## JanusGraph Integration

The `janusgraph/` subdirectory contains a complete adapter that bridges KVT's simple key-value interface with JanusGraph's key-column-value requirements. See `janusgraph/README.md` for detailed documentation.

## Build Options

### Make Targets
- `make all` - Build library and sample
- `make libkvt.a` - Build just the library
- `make kvt_sample` - Build just the sample
- `make sample` - Build and run sample
- `make janusgraph` - Build JanusGraph adapter
- `make test-janusgraph` - Build and test adapter
- `make clean` - Clean build artifacts
- `make help` - Show all available targets

### Manual Build
```bash
# Build library
g++ -std=c++17 -Wall -Wextra -O2 -g -fPIC -c kvt_mem.cpp -o kvt_mem.o
ar rcs libkvt.a kvt_mem.o

# Build sample
g++ -std=c++17 -Wall -Wextra -O2 -g kvt_sample.cpp kvt_mem.o -o kvt_sample -pthread
```

## Files

### Core KVT Files
- **kvt_inc.h**: Public API header with all function declarations
- **kvt_mem.h/cpp**: Memory-based implementation with transaction support
- **kvt_sample.cpp**: Example program demonstrating basic KVT usage

### Build System
- **build.sh**: Shell script for quick building
- **Makefile**: Complete Make-based build system with multiple targets

### JanusGraph Integration
- **janusgraph/**: Complete adapter for JanusGraph integration (see subfolder README)

## Requirements

- C++17 compatible compiler (g++ recommended)
- POSIX threads support
- Standard C++ library

## License

Part of the JanusGraph project - see main project license.