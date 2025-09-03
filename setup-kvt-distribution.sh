#!/bin/bash

# JanusGraph KVT Distribution Setup Script
# This script sets up KVT backend support in any JanusGraph distribution
# Usage: ./setup-kvt-distribution.sh [target_directory]

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored output
print_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
print_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
print_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# Get target directory (default to current directory)
TARGET_DIR="${1:-$(pwd)}"

# Convert to absolute path
TARGET_DIR=$(cd "$TARGET_DIR" 2>/dev/null && pwd || echo "$TARGET_DIR")

print_info "Setting up KVT backend in: $TARGET_DIR"

# Check if this is a JanusGraph distribution
if [ ! -f "$TARGET_DIR/bin/gremlin.sh" ]; then
    print_error "This doesn't appear to be a JanusGraph distribution directory"
    print_error "Expected to find: $TARGET_DIR/bin/gremlin.sh"
    exit 1
fi

# Find the source directory (where this script is located)
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

print_info "Source directory: $SCRIPT_DIR"

# Check for KVT files in source directory
KVT_JAR=""
KVT_NATIVE=""

# Look for KVT JAR
if [ -f "$SCRIPT_DIR/janusgraph-kvt/target/janusgraph-kvt-1.2.0-SNAPSHOT.jar" ]; then
    KVT_JAR="$SCRIPT_DIR/janusgraph-kvt/target/janusgraph-kvt-1.2.0-SNAPSHOT.jar"
elif [ -f "$SCRIPT_DIR/janusgraph-kvt-1.2.0-SNAPSHOT.jar" ]; then
    KVT_JAR="$SCRIPT_DIR/janusgraph-kvt-1.2.0-SNAPSHOT.jar"
else
    print_error "Cannot find KVT JAR file"
    print_info "Looking for: janusgraph-kvt-1.2.0-SNAPSHOT.jar"
    print_info "Expected locations:"
    print_info "  - $SCRIPT_DIR/janusgraph-kvt/target/"
    print_info "  - $SCRIPT_DIR/"
    exit 1
fi

# Look for native library
if [ -f "$SCRIPT_DIR/janusgraph-kvt/src/main/native/libjanusgraph-kvt-jni.so" ]; then
    KVT_NATIVE="$SCRIPT_DIR/janusgraph-kvt/src/main/native/libjanusgraph-kvt-jni.so"
elif [ -f "$SCRIPT_DIR/libjanusgraph-kvt-jni.so" ]; then
    KVT_NATIVE="$SCRIPT_DIR/libjanusgraph-kvt-jni.so"
else
    print_error "Cannot find KVT native library"
    print_info "Looking for: libjanusgraph-kvt-jni.so"
    print_info "Expected locations:"
    print_info "  - $SCRIPT_DIR/janusgraph-kvt/src/main/native/"
    print_info "  - $SCRIPT_DIR/"
    exit 1
fi

print_info "Found KVT JAR: $KVT_JAR"
print_info "Found native library: $KVT_NATIVE"

# Copy files to distribution
print_info "Copying KVT files to distribution..."

cp "$KVT_JAR" "$TARGET_DIR/lib/"
if [ $? -eq 0 ]; then
    print_info "✓ Copied KVT JAR to $TARGET_DIR/lib/"
else
    print_error "Failed to copy KVT JAR"
    exit 1
fi

cp "$KVT_NATIVE" "$TARGET_DIR/lib/"
if [ $? -eq 0 ]; then
    print_info "✓ Copied native library to $TARGET_DIR/lib/"
else
    print_error "Failed to copy native library"
    exit 1
fi

# Create KVT launcher script
print_info "Creating KVT launcher script..."

cat > "$TARGET_DIR/bin/gremlin-kvt.sh" << 'EOF'
#!/bin/bash
# JanusGraph Gremlin Console with KVT Support

# Get the directory where this script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
JANUSGRAPH_HOME="$( cd "$SCRIPT_DIR/.." && pwd )"

# Set library path for KVT native library
export JAVA_OPTIONS="-Djava.library.path=$JANUSGRAPH_HOME/lib:${JAVA_OPTIONS}"

# Execute the original gremlin.sh with all arguments
exec "$JANUSGRAPH_HOME/bin/gremlin.sh" "$@"
EOF

chmod +x "$TARGET_DIR/bin/gremlin-kvt.sh"
print_info "✓ Created launcher script: $TARGET_DIR/bin/gremlin-kvt.sh"

# Create KVT configuration
print_info "Creating KVT configuration..."

mkdir -p "$TARGET_DIR/conf/kvt"

cat > "$TARGET_DIR/conf/kvt/janusgraph-kvt.properties" << 'EOF'
# KVT Backend Configuration
storage.backend=org.janusgraph.diskstorage.kvt.KVTStoreManager
storage.kvt.storage-method=composite
storage.directory=/tmp/janusgraph-kvt

# Graph configuration
gremlin.graph=org.janusgraph.core.JanusGraphFactory
graph.graphname=kvt_graph

# Cache settings
cache.db-cache=true
cache.db-cache-size=0.5
EOF

print_info "✓ Created configuration: $TARGET_DIR/conf/kvt/janusgraph-kvt.properties"

# Create test scripts
print_info "Creating test scripts..."

# Simple test script
cat > "$TARGET_DIR/test-kvt-simple.groovy" << 'EOF'
// Simple KVT Test
println "Testing KVT backend..."

try {
    graph = JanusGraphFactory.build()
        .set('storage.backend', 'org.janusgraph.diskstorage.kvt.KVTStoreManager')
        .set('storage.directory', '/tmp/janusgraph-kvt-test-simple')
        .open()
    
    println "✓ Graph opened with KVT backend"
    
    // Add a test vertex
    v = graph.addVertex("name", "test")
    graph.tx().commit()
    println "✓ Added and committed test vertex"
    
    // Query it back
    g = graph.traversal()
    count = g.V().has("name", "test").count().next()
    assert count == 1
    println "✓ Successfully queried vertex"
    
    graph.close()
    println "\n✅ KVT backend is working correctly!"
    
} catch (Exception e) {
    println "❌ Error: " + e.getMessage()
    e.printStackTrace()
    System.exit(1)
}
EOF

# Comprehensive test script
cat > "$TARGET_DIR/test-kvt-full.groovy" << 'EOF'
// Comprehensive KVT Test
println "Running comprehensive KVT test..."

try {
    // 1. Open graph
    graph = JanusGraphFactory.build()
        .set('storage.backend', 'org.janusgraph.diskstorage.kvt.KVTStoreManager')
        .set('storage.directory', '/tmp/janusgraph-kvt-test-full')
        .open()
    
    println "✓ Graph opened"
    
    // 2. Create schema
    mgmt = graph.openManagement()
    name = mgmt.makePropertyKey('name').dataType(String.class).make()
    age = mgmt.makePropertyKey('age').dataType(Integer.class).make()
    person = mgmt.makeVertexLabel('person').make()
    knows = mgmt.makeEdgeLabel('knows').make()
    mgmt.commit()
    println "✓ Schema created"
    
    // 3. Add data
    g = graph.traversal()
    alice = g.addV('person').property('name', 'Alice').property('age', 30).next()
    bob = g.addV('person').property('name', 'Bob').property('age', 25).next()
    charlie = g.addV('person').property('name', 'Charlie').property('age', 35).next()
    
    g.addE('knows').from(alice).to(bob).iterate()
    g.addE('knows').from(bob).to(charlie).iterate()
    graph.tx().commit()
    println "✓ Data added"
    
    // 4. Query data
    personCount = g.V().hasLabel('person').count().next()
    assert personCount == 3
    println "✓ Found $personCount people"
    
    aliceKnows = g.V().has('name', 'Alice').out('knows').values('name').toList()
    assert aliceKnows == ['Bob']
    println "✓ Alice knows: $aliceKnows"
    
    // 5. Path query
    path = g.V().has('name', 'Alice')
        .repeat(out('knows')).times(2)
        .path().by('name').next()
    println "✓ Path from Alice: $path"
    
    // 6. Transaction test
    tx = graph.newTransaction()
    gtx = tx.traversal()
    gtx.addV('person').property('name', 'Dave').iterate()
    tx.rollback()
    
    daveCount = g.V().has('name', 'Dave').count().next()
    assert daveCount == 0
    println "✓ Transaction rollback worked"
    
    // 7. Clean up
    graph.close()
    
    println "\n✅ All KVT tests passed successfully!"
    
} catch (Exception e) {
    println "❌ Test failed: " + e.getMessage()
    e.printStackTrace()
    System.exit(1)
}
EOF

print_info "✓ Created test scripts"

# Create a test runner script
cat > "$TARGET_DIR/test-kvt.sh" << 'EOF'
#!/bin/bash
# KVT Test Runner

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

echo "===================================="
echo "JanusGraph KVT Backend Test Suite"
echo "===================================="
echo

# Run simple test
echo "Running simple test..."
"$SCRIPT_DIR/bin/gremlin-kvt.sh" -e test-kvt-simple.groovy
if [ $? -ne 0 ]; then
    echo "Simple test failed!"
    exit 1
fi

echo
echo "Running comprehensive test..."
"$SCRIPT_DIR/bin/gremlin-kvt.sh" -e test-kvt-full.groovy
if [ $? -ne 0 ]; then
    echo "Comprehensive test failed!"
    exit 1
fi

echo
echo "===================================="
echo "✅ All KVT tests passed!"
echo "===================================="
EOF

chmod +x "$TARGET_DIR/test-kvt.sh"
print_info "✓ Created test runner: $TARGET_DIR/test-kvt.sh"

# Create uninstall script
cat > "$TARGET_DIR/uninstall-kvt.sh" << 'EOF'
#!/bin/bash
# Uninstall KVT backend

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

echo "Removing KVT backend..."

# Remove files
rm -f "$SCRIPT_DIR/lib/janusgraph-kvt-1.2.0-SNAPSHOT.jar"
rm -f "$SCRIPT_DIR/lib/libjanusgraph-kvt-jni.so"
rm -f "$SCRIPT_DIR/bin/gremlin-kvt.sh"
rm -rf "$SCRIPT_DIR/conf/kvt"
rm -f "$SCRIPT_DIR/test-kvt-simple.groovy"
rm -f "$SCRIPT_DIR/test-kvt-full.groovy"
rm -f "$SCRIPT_DIR/test-kvt.sh"
rm -f "$SCRIPT_DIR/uninstall-kvt.sh"

echo "✓ KVT backend removed"
EOF

chmod +x "$TARGET_DIR/uninstall-kvt.sh"

# Print summary
echo
print_info "===================================="
print_info "KVT Backend Setup Complete!"
print_info "===================================="
echo
print_info "Files installed:"
print_info "  - lib/janusgraph-kvt-1.2.0-SNAPSHOT.jar"
print_info "  - lib/libjanusgraph-kvt-jni.so"
print_info "  - bin/gremlin-kvt.sh (launcher)"
print_info "  - conf/kvt/janusgraph-kvt.properties"
print_info "  - test-kvt.sh (test runner)"
print_info "  - test-kvt-simple.groovy"
print_info "  - test-kvt-full.groovy"
print_info "  - uninstall-kvt.sh"
echo
print_info "To test KVT backend:"
print_info "  cd $TARGET_DIR"
print_info "  ./test-kvt.sh"
echo
print_info "To use KVT backend:"
print_info "  cd $TARGET_DIR"
print_info "  ./bin/gremlin-kvt.sh"
echo
print_info "To uninstall KVT backend:"
print_info "  cd $TARGET_DIR"
print_info "  ./uninstall-kvt.sh"
echo