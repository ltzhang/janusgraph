# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

JanusGraph is a distributed graph database optimized for processing massive-scale graphs across machine clusters. It provides Apache TinkerPop compatibility and supports various storage backends (Cassandra, HBase, BerkeleyDB) and index backends (Elasticsearch, Solr, Lucene).

## Build System

This project uses Maven as the build system. Key commands:

### Basic Build Commands
- **Build without tests**: `mvn clean install -DskipTests=true`
- **Build with default tests**: `mvn clean install`
- **Build with TinkerPop tests**: `mvn clean install -Dtest.skip.tp=false`
- **Build distribution**: `mvn clean install -Pjanusgraph-release -Dgpg.skip=true -DskipTests=true`

### Code Quality
- **Checkstyle validation**: `mvn checkstyle:check`
- **License validation**: `mvn apache-rat:check`

## Testing Framework

The project uses JUnit 5 with specialized test categories controlled by Maven properties:

### Test Categories
- **Default tests**: Run with `mvn test` (enabled by default)
- **Memory tests**: `mvn test -Dtest.skip.mem=false` (stress memory structures)
- **Performance tests**: `mvn test -Dtest.skip.perf=false` (speed benchmarks)

### Running Single Tests
```bash
mvn test -Dtest=ClassName#methodName
mvn test -Dtest=MemoryTestClass -Dtest.skip.mem=false
```

### Backend-Specific Testing
- **Cassandra/CQL**: `mvn clean install -pl janusgraph-cql -Pcassandra3-murmur` (requires Docker)
- **HBase**: `mvn clean install -pl janusgraph-hbase` (requires Docker)
- **Elasticsearch**: `mvn clean install -pl janusgraph-es` (requires Docker)
- **Solr**: `mvn clean install -pl janusgraph-solr` (requires Docker)

## Architecture Overview

### Module Structure
- **janusgraph-core**: Core graph database implementation
- **janusgraph-server**: Gremlin server integration
- **janusgraph-driver**: Client driver for remote connections
- **Storage backends**: janusgraph-cql, janusgraph-hbase, janusgraph-berkeleyje, janusgraph-inmemory
- **Index backends**: janusgraph-es, janusgraph-solr, janusgraph-lucene
- **janusgraph-hadoop**: MapReduce integration for bulk operations
- **janusgraph-examples**: Example configurations and usage patterns

### Key Packages
- `org.janusgraph.core`: Public API interfaces (JanusGraph, JanusGraphTransaction, etc.)
- `org.janusgraph.graphdb`: Internal graph database implementation
- `org.janusgraph.diskstorage`: Storage abstraction layer
- `org.janusgraph.example`: Reference implementations like GraphOfTheGodsFactory

### Storage Layer
JanusGraph uses a pluggable storage architecture:
- **KeyColumnValueStore**: Primary storage abstraction
- **IndexProvider**: Search index abstraction
- **Backend**: Combines storage and index providers

## Development Notes

### Java Version
- Supports Java 8+ (default target)
- Java 11 profile available: `mvn clean install -Pjava-11`

### Documentation
- Build docs: `mkdocs build` (requires Python dependencies from requirements.txt)
- Serve locally: `mkdocs serve`
- Generate config reference: `mvn --quiet clean install -DskipTests=true -pl janusgraph-doc -am`

### Multi-Backend Support
Each storage backend module provides its own test configurations and may require external services (Docker containers) for integration testing.


## The current effort is to integrate kvt under janusgraph-kvt/ into the janusgraph framework. 

- In this effort, you only need to care about janusgraph-kvt/kvt/kvt_inc.h file, which is the interface for the C++ transactional key-value store. 

- You should assume kvt_inc.h can interface with very powerful, transactional key value stores. Do not assume any limitations (such as durability, scalability, ACID, and so on). Assume the store has all the capabilities. But do point out if certain property is needed for certain functionalities in the document file KVT_README.md

- To link the store, you just link janusgraph-kvt/kvt/kvt_memory.o All other files in the directory are irrelevant, more over, kvt_memory.o (which is produced with "g++ -c -fPIC -g -O0 kvt_memory.cpp") implements a in-memory version for test purposes. But you should not concern with its limitations, since other implementations have different capabilities. Stick to the interface.

- Treat kvt/ directory as read only. Do not add/delete/modify any files in that directory. 

- You should use janusgraph-kvt/ as the root directory we operate on and focus on this directory in most cases. When you need to understand how integration works, look at other directories such as janusgraph-berkeleyje, janusgraph-inmemory or janusgraph-hbase and janusgraph-bigtable. Do understand that kvt is a C++ codebase, so we need JNI integration, but we want to treat it as a distributed, scalable, persistent, transactional and so on, like hbase or bigtable, and implement all the features of Janusgraph on top of it. 

- janusgraph-kvt/KVT_README.md contains instructions and build methods. Carefully read it and refer to it. Update it if you see fit. 

Now that the work is done. We will focus on testing and tuning. After startup wait for instructions. 