# How to Use KVT Backend with JanusGraph

This guide provides step-by-step instructions for enabling and using the KVT backend with JanusGraph.

## Table of Contents
- [Prerequisites](#prerequisites)
- [Building from Source](#building-from-source)
- [Enabling KVT in Distribution](#enabling-kvt-in-distribution)
- [Using KVT Backend](#using-kvt-backend)
- [Advanced Configuration](#advanced-configuration)
- [Examples](#examples)
- [Troubleshooting](#troubleshooting)

## Prerequisites

- JDK 8 or higher
- C++ compiler (g++ with C++17 support)
- Maven 3.6+
- Built JanusGraph distribution or source code

## Building from Source

### Step 1: Build JanusGraph with KVT Module

```bash
# From JanusGraph root directory
mvn clean install -DskipTests

# Or to build distribution with KVT:
mvn clean install -Pjanusgraph-release -Dgpg.skip=true -DskipTests=true
```

### Step 2: Build Native KVT Library

```bash
cd janusgraph-kvt

# Build KVT object file (if not already built)
cd kvt
g++ -c -fPIC -g -O0 kvt_mem.cpp -o kvt_mem.o
cd ..

# Build JNI bridge
./build-native.sh

# Verify native library was created
ls src/main/native/libjanusgraph-kvt-jni.so
```

## Enabling KVT in Distribution

### Method 1: Quick Setup Script

Create this setup script in your JanusGraph source directory:

```bash
#!/bin/bash
# setup-kvt.sh

DIST_DIR="janusgraph-full-1.2.0-SNAPSHOT"

# Copy required files
cp janusgraph-kvt/target/janusgraph-kvt-1.2.0-SNAPSHOT.jar $DIST_DIR/lib/
cp janusgraph-kvt/src/main/native/libjanusgraph-kvt-jni.so $DIST_DIR/lib/

# Create KVT launcher
cat > $DIST_DIR/bin/gremlin-kvt.sh << 'EOF'
#!/bin/bash
JANUSGRAPH_HOME="$( cd "$( dirname "${BASH_SOURCE[0]}" )/.." && pwd )"
export JAVA_OPTIONS="-Djava.library.path=$JANUSGRAPH_HOME/lib:${JAVA_OPTIONS}"
exec "$JANUSGRAPH_HOME/bin/gremlin.sh" "$@"
EOF
chmod +x $DIST_DIR/bin/gremlin-kvt.sh

# Create KVT configuration
mkdir -p $DIST_DIR/conf/kvt
cat > $DIST_DIR/conf/kvt/janusgraph-kvt.properties << 'EOF'
storage.backend=org.janusgraph.diskstorage.kvt.KVTStoreManager
storage.kvt.storage-method=composite
storage.directory=/tmp/janusgraph-kvt
gremlin.graph=org.janusgraph.core.JanusGraphFactory
EOF

echo "KVT backend setup complete!"
```

Run the setup:
```bash
chmod +x setup-kvt.sh
./setup-kvt.sh
```

### Method 2: Manual Setup

```bash
# 1. Copy JAR file
cp janusgraph-kvt/target/janusgraph-kvt-1.2.0-SNAPSHOT.jar \
   janusgraph-full-1.2.0-SNAPSHOT/lib/

# 2. Copy native library
cp janusgraph-kvt/src/main/native/libjanusgraph-kvt-jni.so \
   janusgraph-full-1.2.0-SNAPSHOT/lib/

# 3. Create launcher (see setup script above)

# 4. Verify installation
ls janusgraph-full-1.2.0-SNAPSHOT/lib/*kvt*
```

## Using KVT Backend

### Gremlin Console

```bash
cd janusgraph-full-1.2.0-SNAPSHOT

# Use the KVT launcher
./bin/gremlin-kvt.sh

# Or set library path manually
export JAVA_OPTIONS="-Djava.library.path=$PWD/lib"
./bin/gremlin.sh
```

In the console:
```groovy
// Option 1: Programmatic configuration
graph = JanusGraphFactory.build().
  set('storage.backend', 'org.janusgraph.diskstorage.kvt.KVTStoreManager').
  set('storage.directory', '/tmp/janusgraph-kvt').
  open()

// Option 2: Using properties file
graph = JanusGraphFactory.open('conf/kvt/janusgraph-kvt.properties')

// Use the graph
g = graph.traversal()
```

### Java Application

```java
import org.janusgraph.core.JanusGraph;
import org.janusgraph.core.JanusGraphFactory;
import org.janusgraph.core.schema.JanusGraphManagement;
import org.apache.commons.configuration2.BaseConfiguration;
import org.apache.commons.configuration2.Configuration;

public class KVTExample {
    public static void main(String[] args) {
        // Set library path
        System.setProperty("java.library.path", "./lib");
        
        // Configure KVT backend
        Configuration config = new BaseConfiguration();
        config.setProperty("storage.backend", 
            "org.janusgraph.diskstorage.kvt.KVTStoreManager");
        config.setProperty("storage.directory", "/tmp/janusgraph-kvt");
        
        // Open graph
        JanusGraph graph = JanusGraphFactory.open(config);
        
        try {
            // Create schema
            JanusGraphManagement mgmt = graph.openManagement();
            mgmt.makePropertyKey("name").dataType(String.class).make();
            mgmt.makeVertexLabel("person").make();
            mgmt.makeEdgeLabel("knows").make();
            mgmt.commit();
            
            // Add data
            graph.addVertex("label", "person", "name", "Alice");
            graph.addVertex("label", "person", "name", "Bob");
            graph.tx().commit();
            
            // Query
            graph.traversal().V().has("name", "Alice")
                .forEachRemaining(v -> 
                    System.out.println("Found: " + v.value("name")));
                    
        } finally {
            graph.close();
        }
    }
}
```

### Gremlin Server

1. **Configure server YAML**:
```yaml
# conf/gremlin-server/gremlin-server-kvt.yaml
host: localhost
port: 8182
graphs: {
  graph: conf/kvt/janusgraph-kvt.properties
}
scriptEngines: {
  gremlin-groovy: {
    plugins: {
      org.janusgraph.graphdb.tinkerpop.plugin.JanusGraphGremlinPlugin: {},
      org.apache.tinkerpop.gremlin.server.jsr223.GremlinServerGremlinPlugin: {},
      org.apache.tinkerpop.gremlin.tinkergraph.jsr223.TinkerGraphGremlinPlugin: {},
      org.apache.tinkerpop.gremlin.jsr223.ImportGremlinPlugin: {
        classImports: [java.lang.Math],
        methodImports: [java.lang.Math#*]
      },
      org.apache.tinkerpop.gremlin.jsr223.ScriptFileGremlinPlugin: {
        files: [scripts/empty-sample.groovy]
      }
    }
  }
}
```

2. **Start server**:
```bash
export JAVA_OPTIONS="-Djava.library.path=$PWD/lib"
./bin/gremlin-server.sh conf/gremlin-server/gremlin-server-kvt.yaml
```

3. **Connect from client**:
```groovy
// In another terminal
./bin/gremlin.sh

:remote connect tinkerpop.server conf/remote.yaml
:remote console
g.V().count()
```

## Advanced Configuration

### Configuration Properties

| Property | Description | Default |
|----------|-------------|---------|
| `storage.backend` | Backend implementation class | Required: `org.janusgraph.diskstorage.kvt.KVTStoreManager` |
| `storage.directory` | Data directory path | `/tmp/janusgraph-kvt` |
| `storage.kvt.storage-method` | Storage method: `composite` or `serialized` | `composite` |
| `cache.db-cache` | Enable database-level cache | `true` |
| `cache.db-cache-size` | Cache size (fraction of heap) | `0.5` |

### Transaction Configuration

```groovy
// Custom transaction configuration
graph = JanusGraphFactory.build().
  set('storage.backend', 'org.janusgraph.diskstorage.kvt.KVTStoreManager').
  set('storage.directory', '/tmp/janusgraph-kvt').
  set('storage.transactions', true).
  set('storage.lock.wait-time', 500).
  set('storage.lock.retry-count', 3).
  open()
```

## Examples

### Example 1: Social Network Graph

```groovy
// Create social network schema
graph = JanusGraphFactory.build().
  set('storage.backend', 'org.janusgraph.diskstorage.kvt.KVTStoreManager').
  set('storage.directory', '/tmp/social-network').
  open()

mgmt = graph.openManagement()

// Properties
name = mgmt.makePropertyKey('name').dataType(String.class).make()
age = mgmt.makePropertyKey('age').dataType(Integer.class).make()
city = mgmt.makePropertyKey('city').dataType(String.class).make()

// Labels
person = mgmt.makeVertexLabel('person').make()
company = mgmt.makeVertexLabel('company').make()

// Edges
knows = mgmt.makeEdgeLabel('knows').multiplicity(MULTI).make()
worksAt = mgmt.makeEdgeLabel('worksAt').multiplicity(MANY2ONE).make()

// Indexes
mgmt.buildIndex('byName', Vertex.class).addKey(name).buildCompositeIndex()

mgmt.commit()

// Add data
g = graph.traversal()

alice = g.addV('person').property('name', 'Alice').property('age', 30).property('city', 'NYC').next()
bob = g.addV('person').property('name', 'Bob').property('age', 25).property('city', 'LA').next()
charlie = g.addV('person').property('name', 'Charlie').property('age', 35).property('city', 'NYC').next()
techCorp = g.addV('company').property('name', 'TechCorp').next()

g.addE('knows').from(alice).to(bob).property('since', 2019).iterate()
g.addE('knows').from(bob).to(charlie).property('since', 2020).iterate()
g.addE('worksAt').from(alice).to(techCorp).property('role', 'Engineer').iterate()
g.addE('worksAt').from(charlie).to(techCorp).property('role', 'Manager').iterate()

graph.tx().commit()

// Queries
println "People in NYC: " + g.V().has('city', 'NYC').values('name').toList()
println "Alice's connections: " + g.V().has('name', 'Alice').both().values('name').toList()
println "TechCorp employees: " + g.V().has('name', 'TechCorp').in('worksAt').values('name').toList()
```

### Example 2: Batch Loading

```groovy
// Batch load data efficiently
graph = JanusGraphFactory.build().
  set('storage.backend', 'org.janusgraph.diskstorage.kvt.KVTStoreManager').
  set('storage.directory', '/tmp/batch-load').
  set('storage.batch-loading', true).
  open()

g = graph.traversal()

// Load 1000 vertices in batches
def batchSize = 100
def totalVertices = 1000

(0..<totalVertices).collate(batchSize).each { batch ->
    batch.each { i ->
        g.addV('item').
          property('id', i).
          property('name', "Item-${i}").
          property('value', Math.random() * 1000).
          iterate()
    }
    graph.tx().commit()
    println "Loaded batch: ${batch.first()}-${batch.last()}"
}

println "Total vertices loaded: " + g.V().count().next()
```

### Example 3: Complex Traversals

```groovy
// Finding paths and patterns
graph = JanusGraphFactory.open('conf/kvt/janusgraph-kvt.properties')
g = graph.traversal()

// Find shortest path
shortestPath = g.V().has('name', 'Alice').
  repeat(out().simplePath()).
  until(has('name', 'Charlie')).
  path().by('name').
  limit(1).next()
println "Shortest path: " + shortestPath

// Find communities (people who know each other)
communities = g.V().hasLabel('person').
  repeat(both('knows').simplePath()).
  times(2).
  path().by('name').
  toList()
communities.each { println "Community: " + it }

// Recommendation engine
recommendations = g.V().has('name', 'Alice').
  out('knows').
  out('knows').
  where(P.neq('Alice')).
  groupCount().by('name').
  order(local).by(values, desc).
  limit(3).next()
println "Recommended connections for Alice: " + recommendations
```

## Troubleshooting

### Debug Mode

Enable debug logging:
```bash
export JAVA_OPTIONS="-Djava.library.path=$PWD/lib -Dlog4j.level=DEBUG"
./bin/gremlin-kvt.sh
```

### Common Issues and Solutions

| Issue | Error Message | Solution |
|-------|--------------|----------|
| Library not found | `UnsatisfiedLinkError` | Use `gremlin-kvt.sh` or set `java.library.path` |
| Class not found | `Could not find implementation class` | Ensure KVT JAR is in lib directory |
| Configuration error | `Could not execute operation` | Use full class name for backend |
| Native init failed | `Failed to initialize KVT` | Rebuild native library |
| Transaction timeout | `Lock wait timeout` | Increase `storage.lock.wait-time` |

### Verify Installation

Run this verification script:
```groovy
// verify-kvt.groovy
try {
    println "Checking KVT backend..."
    
    graph = JanusGraphFactory.build().
      set('storage.backend', 'org.janusgraph.diskstorage.kvt.KVTStoreManager').
      set('storage.directory', '/tmp/kvt-verify').
      open()
    
    println "✓ KVT backend loaded"
    
    g = graph.traversal()
    g.addV('test').property('status', 'working').iterate()
    graph.tx().commit()
    
    result = g.V().has('status', 'working').count().next()
    assert result == 1
    
    println "✓ Basic operations working"
    
    graph.close()
    println "\n✅ SUCCESS: KVT backend is fully operational!"
    
} catch (Exception e) {
    println "❌ ERROR: " + e.getMessage()
    e.printStackTrace()
}
```

Run verification:
```bash
./bin/gremlin-kvt.sh -e verify-kvt.groovy
```

## Performance Tips

1. **Use batch operations** for bulk loading
2. **Configure appropriate cache sizes** based on your heap
3. **Use composite keys** for better column access patterns
4. **Monitor transaction times** and adjust lock timeouts
5. **Use indices** for frequently queried properties

## Next Steps

- Review [KVT_README.md](KVT_README.md) for architecture details
- Check [examples/](../examples/) directory for more code samples
- Refer to [JanusGraph documentation](https://docs.janusgraph.org/) for general usage