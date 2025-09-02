# JanusGraph KVT Examples

This directory contains example scripts and programs demonstrating how to use JanusGraph with the KVT backend.

## Quick Start

Run the quick start script to verify your KVT installation:

```bash
./quick_start.sh
```

## Examples

### 1. Social Network Graph (Python)

A Python example showing how to create and query a social network graph.

**Setup:**
```bash
pip install -r requirements.txt
```

**Run:**
```bash
# Create social network and run queries
python3 social_graph.py

# Run performance benchmark
python3 social_graph.py --benchmark --vertices 5000 --edges 20000
```

### 2. Social Graph (Groovy)

A Groovy script that creates a simple social network.

**Run:**
```bash
$JANUSGRAPH_HOME/bin/gremlin.sh -e create_social_graph.groovy
```

### 3. Backend Comparison

Compare performance between KVT and InMemory backends.

**Run:**
```bash
python3 benchmark_comparison.py
```

## Configuration Files

Example configuration files for different scenarios:

- `conf/kvt-composite.properties` - KVT with composite key storage
- `conf/kvt-serialized.properties` - KVT with serialized storage
- `conf/kvt-server.properties` - Server configuration with KVT

## Common Gremlin Queries

Here are some useful Gremlin queries to try:

```groovy
// Count all vertices
g.V().count()

// Find all people
g.V().hasLabel('person').values('name')

// Find friends of a person
g.V().has('person', 'name', 'Alice').out('knows').values('name')

// Find friends of friends
g.V().has('person', 'name', 'Alice').out('knows').out('knows').dedup().values('name')

// Find shortest path between two people
g.V().has('person', 'name', 'Alice').
  repeat(out('knows').simplePath()).
  until(has('name', 'David')).
  path().by('name')

// Count connections per person
g.V().hasLabel('person').
  project('name', 'connections').
  by('name').
  by(bothE('knows').count())

// Find people with most connections
g.V().hasLabel('person').
  order().by(bothE('knows').count(), desc).
  limit(5).
  values('name')
```

## Troubleshooting

If you encounter issues:

1. **Check native library is loaded:**
   ```bash
   export LD_LIBRARY_PATH=$JANUSGRAPH_HOME/janusgraph-kvt/src/main/native:$LD_LIBRARY_PATH
   ```

2. **Verify server is running:**
   ```bash
   ps aux | grep gremlin-server
   ```

3. **Check logs:**
   ```bash
   tail -f $JANUSGRAPH_HOME/logs/gremlin-server.log
   ```

4. **Test connection:**
   ```python
   from gremlin_python.driver.driver_remote_connection import DriverRemoteConnection
   connection = DriverRemoteConnection('ws://localhost:8182/gremlin', 'g')
   ```

## Resources

- [Gremlin Documentation](https://tinkerpop.apache.org/docs/current/reference/)
- [JanusGraph Documentation](https://docs.janusgraph.org/)
- [KVT Backend Documentation](../KVT_README.md)
- [How-To Guide](../How_To.md)