#!/bin/bash

# Simple KVT Backend Test
# This script tests that the KVT backend is properly installed and working

# Get the directory where this script is located  
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Set the library path for the native library
export JAVA_OPTIONS="-Djava.library.path=$SCRIPT_DIR/lib"

echo "===================================="
echo "KVT Backend Installation Test"
echo "===================================="
echo

# Create a simple test
cat > "$SCRIPT_DIR/kvt-test.groovy" << 'EOF'
println "Starting KVT backend test..."
println ""

success = true
errors = []

try {
    // Test 1: Can we load the backend?
    print "1. Loading KVT backend... "
    graph = JanusGraphFactory.build().
        set('storage.backend', 'org.janusgraph.diskstorage.kvt.KVTStoreManager').
        set('storage.directory', '/tmp/kvt-test-' + System.currentTimeMillis()).
        open()
    println "✓"
    
    // Test 2: Can we add vertices?
    print "2. Adding vertices... "
    g = graph.traversal()
    v1 = graph.addVertex("test", "vertex1")
    v2 = graph.addVertex("test", "vertex2")
    graph.tx().commit()
    println "✓"
    
    // Test 3: Can we query vertices?
    print "3. Querying vertices... "
    count = g.V().count().next()
    if (count != 2) {
        throw new Exception("Expected 2 vertices, got " + count)
    }
    println "✓"
    
    // Test 4: Can we add edges?
    print "4. Adding edges... "
    // Get fresh vertex references after commit
    g = graph.traversal()
    v1Fresh = g.V().has("test", "vertex1").next()
    v2Fresh = g.V().has("test", "vertex2").next()
    v1Fresh.addEdge("connected", v2Fresh, "weight", 1.0)
    graph.tx().commit()
    println "✓"
    
    // Test 5: Can we traverse edges?
    print "5. Traversing edges... "
    g = graph.traversal()  // Get fresh traversal
    edgeCount = g.E().count().next()
    if (edgeCount != 1) {
        throw new Exception("Expected 1 edge, got " + edgeCount)
    }
    // Also verify edge properties
    edge = g.E().next()
    weight = edge.value("weight")
    if (weight != 1.0) {
        throw new Exception("Expected edge weight 1.0, got " + weight)
    }
    println "✓"
    
    // Test 6: Can we use transactions?
    print "6. Testing transactions... "
    tx = graph.newTransaction()
    tx.addVertex("temp", "should-not-persist")
    tx.rollback()
    
    // Verify rollback worked
    g = graph.traversal()
    tempCount = g.V().has("temp", "should-not-persist").count().next()
    if (tempCount != 0) {
        throw new Exception("Transaction rollback failed")
    }
    println "✓"
    
    // Clean up
    graph.close()
    
} catch (Exception e) {
    success = false
    println "✗"
    errors.add(e.getMessage())
    println "Error: " + e.getMessage()
    e.printStackTrace()
}

println ""
println "===================================="
if (success) {
    println "✅ ALL TESTS PASSED"
    println "KVT backend is working correctly!"
} else {
    println "❌ TESTS FAILED"
    errors.each { println "  - " + it }
}
println "===================================="

System.exit(success ? 0 : 1)
EOF

# Run the test
"$SCRIPT_DIR/bin/gremlin.sh" -e kvt-test.groovy

EXIT_CODE=$?

echo
if [ $EXIT_CODE -eq 0 ]; then
    echo "✅ KVT backend is properly installed and working!"
else
    echo "❌ KVT backend test failed. Please check the errors above."
fi

exit $EXIT_CODE