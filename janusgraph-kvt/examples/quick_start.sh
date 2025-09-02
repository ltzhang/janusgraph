#!/bin/bash
# Quick start script for JanusGraph with KVT backend
# This script sets up and runs a simple test to verify KVT is working

set -e

echo "========================================="
echo "JanusGraph KVT Backend - Quick Start"
echo "========================================="

# Check if JANUSGRAPH_HOME is set
if [ -z "$JANUSGRAPH_HOME" ]; then
    echo "Error: JANUSGRAPH_HOME is not set!"
    echo "Please set it to your JanusGraph installation directory:"
    echo "  export JANUSGRAPH_HOME=/path/to/janusgraph"
    exit 1
fi

echo "Using JanusGraph at: $JANUSGRAPH_HOME"

# Step 1: Create configuration directory
echo -e "\n1. Setting up configuration..."
mkdir -p $JANUSGRAPH_HOME/conf/kvt

# Step 2: Create KVT configuration
echo "2. Creating KVT configuration..."
cat > $JANUSGRAPH_HOME/conf/kvt/janusgraph-kvt.properties << 'EOF'
# KVT Backend Configuration
storage.backend=org.janusgraph.diskstorage.kvt.KVTStoreManager
storage.kvt.storage-method=composite

# Cache settings
cache.db-cache=true
cache.db-cache-size=0.5
EOF

echo "   Configuration created at: conf/kvt/janusgraph-kvt.properties"

# Step 3: Set up native library path
echo "3. Setting up native library path..."
export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:$JANUSGRAPH_HOME/janusgraph-kvt/src/main/native
echo "   LD_LIBRARY_PATH updated"

# Step 4: Create a simple test script
echo "4. Creating test script..."
cat > /tmp/kvt_test.groovy << 'EOF'
import org.janusgraph.core.JanusGraphFactory

println "Opening graph with KVT backend..."
graph = JanusGraphFactory.open('conf/kvt/janusgraph-kvt.properties')
g = graph.traversal()

println "Creating test vertices..."
v1 = g.addV('test').property('name', 'Test1').property('value', 100).next()
v2 = g.addV('test').property('name', 'Test2').property('value', 200).next()
g.V(v1).addE('connects').to(v2).iterate()

graph.tx().commit()

println "Querying graph..."
count = g.V().count().next()
println "  Total vertices: " + count

edges = g.E().count().next()
println "  Total edges: " + edges

// Test a traversal
println "  Test traversal: " + g.V().has('name', 'Test1').out('connects').values('name').next()

println "Cleaning up test data..."
g.V().hasLabel('test').drop().iterate()
graph.tx().commit()

graph.close()
println "Test completed successfully!"
EOF

# Step 5: Run the test
echo "5. Running KVT test..."
echo "---"
$JANUSGRAPH_HOME/bin/gremlin.sh -e /tmp/kvt_test.groovy

echo -e "\n========================================="
echo "Quick Start Complete!"
echo "========================================="
echo ""
echo "KVT backend is working correctly!"
echo ""
echo "Next steps:"
echo "1. Start Gremlin Server:"
echo "   $JANUSGRAPH_HOME/bin/gremlin-server.sh conf/kvt/janusgraph-kvt.properties"
echo ""
echo "2. Connect with Gremlin Console:"
echo "   $JANUSGRAPH_HOME/bin/gremlin.sh"
echo ""
echo "3. Try the examples:"
echo "   cd $JANUSGRAPH_HOME/janusgraph-kvt/examples"
echo "   python3 social_graph.py"
echo ""
echo "For more information, see How_To.md"