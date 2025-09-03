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
