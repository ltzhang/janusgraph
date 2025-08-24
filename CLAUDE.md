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