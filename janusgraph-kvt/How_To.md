# JanusGraph KVT Backend - How To Guide

This guide walks you through using JanusGraph with the KVT backend, from basic setup to running benchmarks. No prior graph database knowledge required!

## Table of Contents
1. [What is a Graph Database?](#what-is-a-graph-database)
2. [Quick Start](#quick-start)
3. [Running JanusGraph with KVT](#running-janusgraph-with-kvt)
4. [Basic Graph Operations](#basic-graph-operations)
5. [Running Benchmarks](#running-benchmarks)
6. [Comparing KVT with InMemory Backend](#comparing-kvt-with-inmemory-backend)
7. [Example Applications](#example-applications)

## What is a Graph Database?

A graph database stores data as nodes (vertices) and relationships (edges). Think of it like:
- **Nodes**: People, places, or things (e.g., users, products, cities)
- **Edges**: Relationships between nodes (e.g., "follows", "purchased", "lives in")
- **Properties**: Attributes on nodes and edges (e.g., name, age, weight)

JanusGraph is a scalable graph database that can handle billions of vertices and edges.

## Prerequisites

Ensure you have:
- Java 8 or 11 installed
- Python 3.6+ (for example scripts)
- Maven 3.6+
- Built JanusGraph with KVT support (see KVT_README.md)

## Quick Start

### Step 1: Set Up Your Environment

```bash
#first create a work dir
mkdir work_dir
cd work_dir
unzip janusgraph-full-1.2.0-SNAPSHOT.zip
# Navigate to JanusGraph root directory
cd /path/to/janusgraph/work_dir/janusgraph-full-1.2.0-SNAPSHOT

# Set JANUSGRAPH_HOME
export JANUSGRAPH_HOME=$(pwd)

# Ensure KVT native library is accessible
export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:$JANUSGRAPH_HOME/janusgraph-kvt/src/main/native
```

### Step 2: Start JanusGraph Server with KVT

Create a configuration file for KVT backend:

```bash
# Create config directory
mkdir -p $JANUSGRAPH_HOME/conf/kvt

# Create KVT configuration
cat > $JANUSGRAPH_HOME/conf/kvt/janusgraph-kvt-server.properties << 'EOF'
# KVT Backend Configuration
storage.backend=org.janusgraph.diskstorage.kvt.KVTStoreManager
# Note: KVT currently uses default configurations

# Server settings
gremlin.graph=org.janusgraph.core.JanusGraphFactory
graph.graphname=kvt_graph

# Optional: Index backend (if needed)
# index.search.backend=lucene
# index.search.directory=/tmp/janusgraph-index

# Cache settings
cache.db-cache=true
cache.db-cache-size=0.5
EOF
```

Start the Gremlin Server:

```bash
# Start server with KVT configuration
$JANUSGRAPH_HOME/bin/janusgraph-server.sh $JANUSGRAPH_HOME/conf/kvt/janusgraph-kvt-server.properties
```

### Step 3: Connect and Test

Open another terminal and start the Gremlin Console:

```bash
$JANUSGRAPH_HOME/bin/gremlin.sh
```

In the console, connect to your server:

```groovy
:remote connect tinkerpop.server conf/remote.yaml
:remote console
// Now you're connected to the KVT-backed JanusGraph!
```

## Basic Graph Operations

### Creating Your First Graph

Save this as `examples/create_social_graph.groovy`:

```groovy
// Create a simple social network graph
import org.janusgraph.core.JanusGraphFactory

graph = JanusGraphFactory.open('conf/kvt/janusgraph-kvt-server.properties')
g = graph.traversal()

// Create vertices (people)
alice = g.addV('person').property('name', 'Alice').property('age', 28).next()
bob = g.addV('person').property('name', 'Bob').property('age', 32).next()
charlie = g.addV('person').property('name', 'Charlie').property('age', 24).next()
david = g.addV('person').property('name', 'David').property('age', 29).next()

// Create edges (relationships)
g.V(alice).addE('knows').to(bob).property('since', 2018).iterate()
g.V(alice).addE('knows').to(charlie).property('since', 2020).iterate()
g.V(bob).addE('knows').to(charlie).property('since', 2019).iterate()
g.V(charlie).addE('knows').to(david).property('since', 2021).iterate()

// Commit the transaction
graph.tx().commit()

println "Graph created successfully!"

// Some queries
println "\n=== Friends of Alice ==="
g.V().has('name', 'Alice').out('knows').values('name').each { println it }

println "\n=== People who know Charlie ==="
g.V().has('name', 'Charlie').in('knows').values('name').each { println it }

println "\n=== Friends of friends of Alice ==="
g.V().has('name', 'Alice').out('knows').out('knows').dedup().values('name').each { println it }

graph.close()
```

Run it:
```bash
$JANUSGRAPH_HOME/bin/gremlin.sh -e examples/create_social_graph.groovy
```

### Python Example

Install the Gremlin Python client:

```bash
pip install gremlinpython
```

Save this as `examples/social_graph.py`:

```python
#!/usr/bin/env python3
"""
Simple social network graph example using JanusGraph with KVT backend
"""

from gremlin_python.driver import client, serializer
from gremlin_python.driver.driver_remote_connection import DriverRemoteConnection
from gremlin_python.process.anonymous_traversal import traversal
from gremlin_python.process.graph_traversal import __
from gremlin_python.structure.graph import Graph
import sys

def create_social_network():
    """Create a simple social network graph"""
    
    # Connect to JanusGraph server
    print("Connecting to JanusGraph server...")
    connection = DriverRemoteConnection('ws://localhost:8182/gremlin', 'g')
    g = traversal().withRemote(connection)
    
    try:
        # Clear existing data (be careful in production!)
        print("Clearing existing data...")
        g.V().drop().iterate()
        
        # Create people (vertices)
        print("Creating people...")
        alice = g.addV('person').property('name', 'Alice').property('age', 28).property('city', 'New York').next()
        bob = g.addV('person').property('name', 'Bob').property('age', 32).property('city', 'Boston').next()
        charlie = g.addV('person').property('name', 'Charlie').property('age', 24).property('city', 'Chicago').next()
        david = g.addV('person').property('name', 'David').property('age', 29).property('city', 'Denver').next()
        eve = g.addV('person').property('name', 'Eve').property('age', 26).property('city', 'New York').next()
        
        # Create relationships (edges)
        print("Creating relationships...")
        g.V(alice).addE('knows').to(bob).property('since', 2018).iterate()
        g.V(alice).addE('knows').to(charlie).property('since', 2020).iterate()
        g.V(bob).addE('knows').to(charlie).property('since', 2019).iterate()
        g.V(charlie).addE('knows').to(david).property('since', 2021).iterate()
        g.V(david).addE('knows').to(eve).property('since', 2022).iterate()
        g.V(eve).addE('knows').to(alice).property('since', 2017).iterate()
        
        print("Social network created successfully!\n")
        
        # Run some queries
        print("=== Query Examples ===\n")
        
        # 1. Find all people
        print("All people in the network:")
        people = g.V().hasLabel('person').values('name').toList()
        for person in people:
            print(f"  - {person}")
        
        # 2. Find Alice's friends
        print("\nAlice's friends:")
        friends = g.V().has('person', 'name', 'Alice').out('knows').values('name').toList()
        for friend in friends:
            print(f"  - {friend}")
        
        # 3. Find people in New York
        print("\nPeople in New York:")
        ny_people = g.V().has('person', 'city', 'New York').values('name').toList()
        for person in ny_people:
            print(f"  - {person}")
        
        # 4. Find friends of friends of Alice (2 hops)
        print("\nFriends of friends of Alice:")
        fof = g.V().has('person', 'name', 'Alice').out('knows').out('knows').dedup().values('name').toList()
        for person in fof:
            print(f"  - {person}")
        
        # 5. Count relationships per person
        print("\nNumber of connections per person:")
        counts = g.V().hasLabel('person').project('name', 'connections').by('name').by(__.bothE('knows').count()).toList()
        for item in counts:
            print(f"  - {item['name']}: {item['connections']} connections")
        
        # 6. Find shortest path between Alice and David
        print("\nShortest path from Alice to David:")
        path = g.V().has('person', 'name', 'Alice').repeat(__.out('knows').simplePath()).until(__.has('name', 'David')).path().by('name').toList()
        if path:
            print(f"  Path: {' -> '.join(path[0])}")
        
    finally:
        connection.close()
        print("\nConnection closed.")

def benchmark_operations(num_vertices=1000, num_edges=5000):
    """Run benchmark operations"""
    import time
    
    print(f"\n=== Running Benchmark ===")
    print(f"Creating {num_vertices} vertices and {num_edges} edges...\n")
    
    connection = DriverRemoteConnection('ws://localhost:8182/gremlin', 'g')
    g = traversal().withRemote(connection)
    
    try:
        # Clear existing data
        g.V().drop().iterate()
        
        # Benchmark vertex creation
        start = time.time()
        vertex_ids = []
        for i in range(num_vertices):
            v = g.addV('user').property('id', i).property('name', f'User{i}').next()
            vertex_ids.append(v)
            if (i + 1) % 100 == 0:
                print(f"  Created {i + 1} vertices...")
        vertex_time = time.time() - start
        print(f"Vertex creation: {vertex_time:.2f} seconds ({num_vertices/vertex_time:.0f} vertices/sec)")
        
        # Benchmark edge creation
        import random
        start = time.time()
        for i in range(num_edges):
            v1 = vertex_ids[random.randint(0, num_vertices-1)]
            v2 = vertex_ids[random.randint(0, num_vertices-1)]
            if v1 != v2:
                g.V(v1).addE('follows').to(v2).iterate()
            if (i + 1) % 500 == 0:
                print(f"  Created {i + 1} edges...")
        edge_time = time.time() - start
        print(f"Edge creation: {edge_time:.2f} seconds ({num_edges/edge_time:.0f} edges/sec)")
        
        # Benchmark traversal queries
        print("\nRunning traversal benchmarks...")
        
        # 1-hop traversal
        start = time.time()
        for i in range(10):
            g.V().has('user', 'id', i).out('follows').count().next()
        one_hop_time = (time.time() - start) / 10
        print(f"1-hop traversal: {one_hop_time*1000:.2f} ms average")
        
        # 2-hop traversal
        start = time.time()
        for i in range(10):
            g.V().has('user', 'id', i).out('follows').out('follows').dedup().count().next()
        two_hop_time = (time.time() - start) / 10
        print(f"2-hop traversal: {two_hop_time*1000:.2f} ms average")
        
        # Vertex lookup
        start = time.time()
        for i in range(100):
            g.V().has('user', 'id', i).next()
        lookup_time = (time.time() - start) / 100
        print(f"Vertex lookup: {lookup_time*1000:.2f} ms average")
        
    finally:
        connection.close()

if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description='JanusGraph KVT Example')
    parser.add_argument('--benchmark', action='store_true', help='Run benchmark instead of example')
    parser.add_argument('--vertices', type=int, default=1000, help='Number of vertices for benchmark')
    parser.add_argument('--edges', type=int, default=5000, help='Number of edges for benchmark')
    
    args = parser.parse_args()
    
    if args.benchmark:
        benchmark_operations(args.vertices, args.edges)
    else:
        create_social_network()
```

Run the example:
```bash
# Run social network example
python3 examples/social_graph.py

# Run benchmark
python3 examples/social_graph.py --benchmark --vertices 5000 --edges 20000
```

## Running Benchmarks

### JanusGraph Built-in Benchmarks

JanusGraph includes performance benchmarks using JMH (Java Microbenchmark Harness).

#### Step 1: Build Benchmark JAR

```bash
cd $JANUSGRAPH_HOME
mvn clean install -DskipTests -Pbenchmarks
```

#### Step 2: Run Benchmarks

Create benchmark configurations:

```bash
# KVT Configuration
cat > conf/benchmark-kvt.properties << 'EOF'
storage.backend=org.janusgraph.diskstorage.kvt.KVTStoreManager
cache.db-cache=true
EOF

# InMemory Configuration (for comparison)
cat > conf/benchmark-inmemory.properties << 'EOF'
storage.backend=inmemory
EOF
```

Run the benchmarks:

```bash
# Run KVT benchmark
java -jar janusgraph-benchmark/target/janusgraph-benchmark-*.jar \
  -p config=conf/benchmark-kvt.properties \
  -rf json -rff kvt-results.json

# Run InMemory benchmark for comparison
java -jar janusgraph-benchmark/target/janusgraph-benchmark-*.jar \
  -p config=conf/benchmark-inmemory.properties \
  -rf json -rff inmemory-results.json
```

### Custom Benchmark Script

Save this as `examples/benchmark_comparison.py`:

```python
#!/usr/bin/env python3
"""
Benchmark comparison between KVT and InMemory backends
"""

import time
import statistics
import subprocess
import os
import json
from typing import Dict, List, Tuple

class JanusGraphBenchmark:
    def __init__(self, config_file: str, backend_name: str):
        self.config_file = config_file
        self.backend_name = backend_name
        self.results = {}
    
    def setup_config(self):
        """Create configuration file for the backend"""
        configs = {
            'kvt': '''
storage.backend=org.janusgraph.diskstorage.kvt.KVTStoreManager
cache.db-cache=true
''',
            'inmemory': '''
storage.backend=inmemory
cache.db-cache=true
'''
        }
        
        with open(self.config_file, 'w') as f:
            f.write(configs[self.backend_name])
    
    def run_groovy_benchmark(self, script_content: str) -> float:
        """Run a Groovy script and measure execution time"""
        script_file = f'/tmp/benchmark_{self.backend_name}.groovy'
        
        # Add configuration loading to script
        full_script = f'''
import org.janusgraph.core.JanusGraphFactory
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource

graph = JanusGraphFactory.open('{self.config_file}')
g = graph.traversal()

startTime = System.currentTimeMillis()

{script_content}

endTime = System.currentTimeMillis()
println "BENCHMARK_TIME:" + (endTime - startTime)

graph.close()
'''
        
        with open(script_file, 'w') as f:
            f.write(full_script)
        
        # Run the script
        result = subprocess.run(
            [f'{os.environ["JANUSGRAPH_HOME"]}/bin/gremlin.sh', '-e', script_file],
            capture_output=True,
            text=True
        )
        
        # Extract timing
        for line in result.stdout.split('\n'):
            if 'BENCHMARK_TIME:' in line:
                return float(line.split(':')[1]) / 1000.0  # Convert to seconds
        
        return -1
    
    def benchmark_vertex_creation(self, num_vertices: int = 1000) -> float:
        """Benchmark vertex creation"""
        script = f'''
// Create {num_vertices} vertices
for (i in 1..{num_vertices}) {{
    g.addV('benchmark_vertex')
     .property('id', i)
     .property('name', 'Vertex' + i)
     .property('value', i * 1.5)
     .iterate()
}}
graph.tx().commit()
'''
        return self.run_groovy_benchmark(script)
    
    def benchmark_edge_creation(self, num_vertices: int = 100, edges_per_vertex: int = 5) -> float:
        """Benchmark edge creation"""
        script = f'''
// Create vertices first
vertices = []
for (i in 1..{num_vertices}) {{
    v = g.addV('node').property('id', i).next()
    vertices.add(v)
}}

// Create edges
random = new Random()
for (i in 0..{num_vertices-1}) {{
    for (j in 1..{edges_per_vertex}) {{
        target = random.nextInt({num_vertices})
        g.V(vertices[i]).addE('connects').to(vertices[target]).iterate()
    }}
}}
graph.tx().commit()
'''
        return self.run_groovy_benchmark(script)
    
    def benchmark_traversals(self) -> Dict[str, float]:
        """Benchmark various traversal operations"""
        traversal_scripts = {
            '1-hop': '''
// Setup: Create a small graph
v1 = g.addV('root').property('id', 0).next()
for (i in 1..100) {
    v2 = g.addV('child').property('id', i).next()
    g.V(v1).addE('connects').to(v2).iterate()
}
graph.tx().commit()

// Benchmark: 1-hop traversal 100 times
for (i in 1..100) {
    g.V().has('id', 0).out('connects').toList()
}
''',
            '2-hop': '''
// Benchmark: 2-hop traversal
for (i in 1..50) {
    g.V().has('id', 0).out('connects').out('connects').dedup().toList()
}
''',
            'filter': '''
// Benchmark: Filtered traversal
import org.apache.tinkerpop.gremlin.process.traversal.P
for (i in 1..100) {
    g.V().has('id', P.gte(0)).has('id', P.lt(50)).toList()
}
'''
        }
        
        results = {}
        for name, script in traversal_scripts.items():
            results[name] = self.run_groovy_benchmark(script)
        
        return results
    
    def run_full_benchmark(self) -> Dict:
        """Run complete benchmark suite"""
        print(f"\n=== Running benchmarks for {self.backend_name} ===")
        
        self.setup_config()
        
        # Vertex creation
        print("Testing vertex creation...")
        vertex_times = []
        for size in [100, 500, 1000]:
            t = self.benchmark_vertex_creation(size)
            vertex_times.append((size, t))
            print(f"  {size} vertices: {t:.3f}s ({size/t:.0f} vertices/sec)")
        
        # Edge creation
        print("Testing edge creation...")
        edge_times = []
        for size in [50, 100, 200]:
            t = self.benchmark_edge_creation(size, 5)
            edge_times.append((size, t))
            print(f"  {size*5} edges: {t:.3f}s ({size*5/t:.0f} edges/sec)")
        
        # Traversals
        print("Testing traversals...")
        traversal_times = self.benchmark_traversals()
        for name, t in traversal_times.items():
            print(f"  {name}: {t:.3f}s")
        
        return {
            'backend': self.backend_name,
            'vertex_creation': vertex_times,
            'edge_creation': edge_times,
            'traversals': traversal_times
        }

def compare_backends():
    """Compare KVT and InMemory backends"""
    
    backends = [
        ('kvt', 'conf/bench-kvt.properties'),
        ('inmemory', 'conf/bench-inmemory.properties')
    ]
    
    all_results = []
    
    for backend_name, config_file in backends:
        benchmark = JanusGraphBenchmark(config_file, backend_name)
        results = benchmark.run_full_benchmark()
        all_results.append(results)
    
    # Print comparison
    print("\n" + "="*60)
    print("PERFORMANCE COMPARISON SUMMARY")
    print("="*60)
    
    print("\n1. Vertex Creation (vertices/second):")
    print("-" * 40)
    print(f"{'Backend':<20} {'100v':<10} {'500v':<10} {'1000v':<10}")
    print("-" * 40)
    for result in all_results:
        backend = result['backend']
        row = f"{backend:<20}"
        for size, time in result['vertex_creation']:
            rate = size / time if time > 0 else 0
            row += f"{rate:<10.0f}"
        print(row)
    
    print("\n2. Edge Creation (edges/second):")
    print("-" * 40)
    print(f"{'Backend':<20} {'250e':<10} {'500e':<10} {'1000e':<10}")
    print("-" * 40)
    for result in all_results:
        backend = result['backend']
        row = f"{backend:<20}"
        for size, time in result['edge_creation']:
            rate = (size * 5) / time if time > 0 else 0
            row += f"{rate:<10.0f}"
        print(row)
    
    print("\n3. Traversal Performance (seconds):")
    print("-" * 40)
    print(f"{'Backend':<20} {'1-hop':<10} {'2-hop':<10} {'filter':<10}")
    print("-" * 40)
    for result in all_results:
        backend = result['backend']
        row = f"{backend:<20}"
        for op in ['1-hop', '2-hop', 'filter']:
            time = result['traversals'].get(op, 0)
            row += f"{time:<10.3f}"
        print(row)
    
    # Save results to JSON
    with open('benchmark_results.json', 'w') as f:
        json.dump(all_results, f, indent=2)
    print("\nDetailed results saved to benchmark_results.json")

if __name__ == "__main__":
    compare_backends()
```

Run the comparison:

```bash
# Make sure JANUSGRAPH_HOME is set
export JANUSGRAPH_HOME=/path/to/janusgraph

# Run the benchmark comparison
python3 examples/benchmark_comparison.py
```

## Comparing KVT with InMemory Backend

### Performance Comparison Test

This test compares KVT and InMemory backends for common operations:

```bash
# Step 1: Create test runner script
cat > examples/compare_backends.sh << 'EOF'
#!/bin/bash

echo "JanusGraph Backend Comparison: KVT vs InMemory"
echo "=============================================="

JANUSGRAPH_HOME=${JANUSGRAPH_HOME:-$(pwd)}

# Function to run test with specific backend
run_test() {
    local backend=$1
    local config_file=$2
    
    echo -e "\n--- Testing with $backend backend ---"
    
    # Create config
    cat > $config_file << CONFIG
storage.backend=$backend
cache.db-cache=true
CONFIG
    
    # Run test
    $JANUSGRAPH_HOME/bin/gremlin.sh -e << 'GROOVY'
import org.janusgraph.core.JanusGraphFactory
import java.lang.System

config = "$config_file"
graph = JanusGraphFactory.open(config)
g = graph.traversal()

// Test 1: Vertex Creation
start = System.currentTimeMillis()
for (i in 1..1000) {
    g.addV('test').property('id', i).iterate()
}
graph.tx().commit()
vertexTime = System.currentTimeMillis() - start
println "Vertex creation (1000): ${vertexTime}ms"

// Test 2: Edge Creation
vertices = g.V().hasLabel('test').limit(100).toList()
start = System.currentTimeMillis()
for (i in 0..99) {
    for (j in 0..4) {
        if (i != j) {
            g.V(vertices[i]).addE('connects').to(vertices[j % 100]).iterate()
        }
    }
}
graph.tx().commit()
edgeTime = System.currentTimeMillis() - start
println "Edge creation (500): ${edgeTime}ms"

// Test 3: Traversal
start = System.currentTimeMillis()
for (i in 1..100) {
    g.V().has('id', i).out('connects').toList()
}
traversalTime = System.currentTimeMillis() - start
println "Traversal queries (100): ${traversalTime}ms"

// Test 4: Property Updates
start = System.currentTimeMillis()
g.V().hasLabel('test').limit(100).property('updated', true).iterate()
graph.tx().commit()
updateTime = System.currentTimeMillis() - start
println "Property updates (100): ${updateTime}ms"

graph.close()
GROOVY
}

# Run tests
run_test "org.janusgraph.diskstorage.kvt.KVTStoreManager" "/tmp/kvt-config.properties"
run_test "inmemory" "/tmp/inmemory-config.properties"

echo -e "\nComparison complete!"
EOF

chmod +x examples/compare_backends.sh
./examples/compare_backends.sh
```

### Expected Results

You should see output comparing:
- **Vertex Creation Speed**: KVT may be slightly slower than InMemory for small datasets
- **Edge Creation**: Similar performance for both
- **Traversal Queries**: KVT with composite keys excels at targeted column access
- **Property Updates**: KVT performs well with its efficient column-based storage

## Configuration Options

### KVT-Specific Settings

```properties
# KVT Backend Configuration
storage.backend=org.janusgraph.diskstorage.kvt.KVTStoreManager

# Standard JanusGraph configurations apply:
cache.db-cache=true                   # Enable database-level cache
cache.db-cache-size=0.5              # Cache size (fraction of heap)
```

### KVT Backend Features

**Current Implementation**:
- Full ACID transactions with pessimistic locking (2PL)
- Efficient key-value operations
- Support for all JanusGraph features
- Native C++ implementation via JNI for performance

The KVT backend automatically handles storage optimization internally and doesn't require storage method selection.

## Troubleshooting

### Common Issues and Solutions

1. **Native Library Not Found**
   ```
   Error: java.lang.UnsatisfiedLinkError: no janusgraph-kvt-jni in java.library.path
   ```
   Solution:
   ```bash
   export LD_LIBRARY_PATH=$JANUSGRAPH_HOME/janusgraph-kvt/src/main/native:$LD_LIBRARY_PATH
   # Or copy the library to a system path
   sudo cp $JANUSGRAPH_HOME/janusgraph-kvt/src/main/native/libjanusgraph-kvt-jni.so /usr/local/lib/
   sudo ldconfig
   ```

2. **Connection Refused**
   ```
   Error: Connection refused (Connection refused)
   ```
   Solution: Ensure Gremlin Server is running:
   ```bash
   ps aux | grep gremlin-server
   # If not running, start it:
   $JANUSGRAPH_HOME/bin/janusgraph-server.sh start
   ```

3. **Out of Memory**
   ```
   Error: java.lang.OutOfMemoryError: Java heap space
   ```
   Solution: Increase heap size in `bin/janusgraph-server.sh`:
   ```bash
   export JAVA_OPTIONS="-Xms2g -Xmx4g"
   ```

4. **Transaction Timeout**
   ```
   Error: Transaction timed out
   ```
   Solution: Increase JanusGraph's transaction timeout:
   ```properties
   storage.lock.wait-time=120000  # 2 minutes
   ```

## Advanced Topics

### Using with Apache Spark

```scala
// Load graph data into Spark
import org.apache.spark.sql.SparkSession
import org.janusgraph.spark.computer.SparkJanusGraphComputer

val spark = SparkSession.builder()
  .appName("JanusGraph-KVT-Spark")
  .master("local[*]")
  .getOrCreate()

// Configure JanusGraph with KVT
val conf = new BaseConfiguration()
conf.setProperty("storage.backend", "org.janusgraph.diskstorage.kvt.KVTStoreManager")
conf.setProperty("cache.db-cache", "true")

// Run graph computations
val graph = JanusGraphFactory.open(conf)
val result = graph.compute(SparkJanusGraphComputer.class)
  .program(PageRankVertexProgram.build().create())
  .submit()
  .get()
```

### Production Deployment

For production use:

1. **Use Environment Variables**:
   ```bash
   export JANUSGRAPH_STORAGE_BACKEND=kvt
   export JANUSGRAPH_CACHE_SIZE=0.5
   ```

2. **Monitor Performance**:
   ```bash
   # Enable JMX monitoring
   export JAVA_OPTIONS="$JAVA_OPTIONS -Dcom.sun.management.jmxremote"
   ```

3. **Set up Logging**:
   ```properties
   # In log4j.properties
   log4j.logger.org.janusgraph.diskstorage.kvt=DEBUG
   ```

## Next Steps

1. **Explore Graph Algorithms**: Try PageRank, shortest path, community detection
2. **Build Applications**: Create REST APIs, web interfaces, or analytics pipelines
3. **Scale Up**: Test with larger datasets and distributed deployments
4. **Optimize**: Profile and tune for your specific use case

## Resources

- [JanusGraph Documentation](https://docs.janusgraph.org/)
- [Gremlin Query Language](https://tinkerpop.apache.org/gremlin.html)
- [Graph Database Concepts](https://neo4j.com/developer/graph-database/)
- KVT Backend Documentation: See KVT_README.md

## Support

For issues specific to the KVT backend:
1. Check the logs in `$JANUSGRAPH_HOME/logs/`
2. Enable debug logging for detailed information
3. Report issues with configuration, error messages, and steps to reproduce