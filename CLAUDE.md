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

## The current effort is to integrate kvt under janusgraph-kvt/ into the janusgraph framework. 

- In this effort, you only need to care about janusgraph-kvt/kvt/kvt_inc.h file, which is the interface for the C++ transactional key-value store. 

- You should assume kvt_inc.h can interface with very powerful, transactional key value stores. Do not assume any limitations (such as durability, scalability, ACID, and so on). Assume the store has all the capabilities. But do point out if certain property is needed for certain functionalities in the document file KVT_README.md

- To link the store, you just link janusgraph-kvt/kvt/kvt_memory.o All other files in the directory are irrelevant, more over, kvt_memory.o (which is produced with "g++ -c -fPIC -g -O0 kvt_memory.cpp") implements a in-memory version for test purposes. But you should not concern with its limitations, since other implementations have different capabilities. Stick to the interface.

- Use janusgraph-kvt/ as the main directory we operate on. When you need to understand how integration works, look at other directories such as janusgraph-berkeleyje, janusgraph-inmemory or janusgraph-hbase. Do understand that kvt is a C++ codebase, so we need JNI integration, but we want to treat it as a distributed, scalable, persistent, transactional and so on, like hbase, and implement all the features of Janusgraph on top of it. 

- janusgraph-kvt/KVT_README.md contains instructions and build methods. Update it if you see fit. 

- To build the binary package do the following: 
build the distribute zip package at the janusgraph project root directory:
`mvn clean install -Pjanusgraph-release -Dgpg.skip=true -DskipTests=true -Dmaven.compiler.debug=true -Ddocker.build.skip=true -DskipITs=true -Drat.skip=true`
The resulting zip file will be located at janusgraph-dist/target/janusgraph-full-1.2.0-SNAPSHOT.zip
Unzip it to a proper directory to work with the distribution. 

