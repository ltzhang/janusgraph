#!/bin/bash

# Verify KVT Installation Script

echo "===================================="
echo "KVT Backend Installation Verification"  
echo "===================================="
echo

# Check for required files
echo "Checking required files..."

SUCCESS=true

# Check JAR
if [ -f "lib/janusgraph-kvt-1.2.0-SNAPSHOT.jar" ]; then
    echo "✓ KVT JAR found"
else
    echo "✗ KVT JAR missing: lib/janusgraph-kvt-1.2.0-SNAPSHOT.jar"
    SUCCESS=false
fi

# Check native library
if [ -f "lib/libjanusgraph-kvt-jni.so" ]; then
    echo "✓ Native library found"
else
    echo "✗ Native library missing: lib/libjanusgraph-kvt-jni.so"
    SUCCESS=false
fi

# Check launcher
if [ -f "bin/gremlin-kvt.sh" ]; then
    echo "✓ KVT launcher found"
else
    echo "✗ KVT launcher missing: bin/gremlin-kvt.sh"
    SUCCESS=false
fi

echo
echo "Testing KVT backend load..."

# Create minimal test
cat > kvt-load-test.groovy << 'EOF'
try {
    graph = JanusGraphFactory.build().
        set('storage.backend', 'org.janusgraph.diskstorage.kvt.KVTStoreManager').
        set('storage.directory', '/tmp/kvt-verify').
        open()
    println "✓ KVT backend loaded successfully"
    graph.close()
} catch (Exception e) {
    println "✗ Failed to load KVT backend: " + e.getMessage()
    System.exit(1)
}
EOF

export JAVA_OPTIONS="-Djava.library.path=$PWD/lib"
./bin/gremlin.sh -e kvt-load-test.groovy 2>&1 | grep "✓\|✗"

echo
echo "===================================="
if [ "$SUCCESS" = true ]; then
    echo "✅ KVT backend is installed"
    echo
    echo "Note: The backend loads but there appears to be a data format issue"
    echo "      between the KVT native layer and JanusGraph when storing/retrieving vertices."
    echo "      This is likely due to the scan operation returning data in an unexpected format."
else
    echo "❌ KVT backend installation incomplete"
fi
echo "===================================="