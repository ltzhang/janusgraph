#!/bin/bash
# setup-kvt.sh

# Check if distribution zip exists
if [ ! -f "../janusgraph-dist/target/janusgraph-full-1.2.0-SNAPSHOT.zip" ]; then
    echo "Error: Distribution zip not found at ../janusgraph-dist/target/janusgraph-full-1.2.0-SNAPSHOT.zip"
    echo "Please build the distribution first with:"
    echo "  cd .. && mvn clean install -Pjanusgraph-release -Dgpg.skip=true -DskipTests=true -Ddocker.build.skip=true -DskipITs=true -Drat.skip=true"
    exit 1
fi

# Check if KVT jar exists
if [ ! -f "../janusgraph-kvt/target/janusgraph-kvt-1.2.0-SNAPSHOT.jar" ]; then
    echo "Error: KVT jar not found. Building KVT module..."
    (cd .. && mvn -f janusgraph-kvt/pom.xml clean package -DskipTests)
fi

# Check if JNI library exists
if [ ! -f "../janusgraph-kvt/src/main/native/libjanusgraph-kvt-jni.so" ]; then
    echo "Error: KVT JNI library not found. Building native library..."
    (cd ../janusgraph-kvt && ./build-native.sh)
fi

rm -rf janusgraph-full-1.2.0-SNAPSHOT
echo "Extracting distribution..."
unzip -q ../janusgraph-dist/target/janusgraph-full-1.2.0-SNAPSHOT.zip

DIST_DIR="janusgraph-full-1.2.0-SNAPSHOT"

# Create symbolic links to KVT components
# Using absolute paths for the symbolic links to ensure they work correctly
KVT_JAR_PATH="$(cd .. && pwd)/janusgraph-kvt/target/janusgraph-kvt-1.2.0-SNAPSHOT.jar"
KVT_JNI_PATH="$(cd .. && pwd)/janusgraph-kvt/src/main/native/libjanusgraph-kvt-jni.so"

echo "Creating symbolic links..."
ln -sf "$KVT_JAR_PATH" "$DIST_DIR/lib/janusgraph-kvt-1.2.0-SNAPSHOT.jar"
ln -sf "$KVT_JNI_PATH" "$DIST_DIR/lib/libjanusgraph-kvt-jni.so"

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
