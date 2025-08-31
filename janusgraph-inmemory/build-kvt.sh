#!/bin/bash

# Build and test script for JanusGraph KVT integration
#
# This script builds the KVT JNI library and runs integration tests

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}JanusGraph KVT Integration Build & Test${NC}"
echo "========================================"

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NATIVE_DIR="$SCRIPT_DIR/src/main/native"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

echo "Script dir: $SCRIPT_DIR"
echo "Native dir: $NATIVE_DIR"
echo "Root dir: $ROOT_DIR"

# Step 1: Build JanusGraph core if needed
echo -e "\n${BLUE}Step 1: Checking JanusGraph build...${NC}"
if [[ ! -d "$ROOT_DIR/janusgraph-core/target/classes" ]]; then
    echo -e "${YELLOW}JanusGraph core not built, building now...${NC}"
    cd "$ROOT_DIR"
    mvn clean compile -DskipTests -pl janusgraph-core,janusgraph-inmemory
else
    echo -e "${GREEN}JanusGraph core already built${NC}"
fi

# Step 2: Build KVT native library
echo -e "\n${BLUE}Step 2: Building KVT JNI library...${NC}"
cd "$NATIVE_DIR"

if [[ ! -f "build.sh" ]]; then
    echo -e "${RED}Error: build.sh not found in $NATIVE_DIR${NC}"
    exit 1
fi

# Set JanusGraph paths for native build
export JANUSGRAPH_HOME="$ROOT_DIR"
export JANUSGRAPH_CORE_JAR="$ROOT_DIR/janusgraph-core/target/classes"
export JANUSGRAPH_INMEMORY_JAR="$ROOT_DIR/janusgraph-inmemory/target/classes"

echo "Running native build..."
./build.sh

if [[ ! -f libjanusgraph_kvt.* ]]; then
    echo -e "${RED}Error: Native library not created${NC}"
    exit 1
fi

LIBRARY=$(ls libjanusgraph_kvt.* | head -n1)
echo -e "${GREEN}Native library created: $LIBRARY${NC}"

# Step 3: Compile Java KVT classes  
echo -e "\n${BLUE}Step 3: Compiling Java KVT classes...${NC}"
cd "$SCRIPT_DIR"

# Create output directory
mkdir -p target/classes target/test-classes

# Compile main classes
echo "Compiling main classes..."
javac -cp "$JANUSGRAPH_CORE_JAR:$ROOT_DIR/janusgraph-inmemory/target/classes" \
      -d target/classes \
      src/main/java/org/janusgraph/diskstorage/kvt/*.java

# Compile test classes
echo "Compiling test classes..."
JUNIT_JAR=$(find ~/.m2/repository/org/junit/jupiter -name "*.jar" 2>/dev/null | head -n5 | tr '\n' ':')
if [[ -z "$JUNIT_JAR" ]]; then
    echo -e "${YELLOW}Warning: JUnit not found in local Maven repository${NC}"
    echo "Skipping test compilation"
else
    javac -cp "$JANUSGRAPH_CORE_JAR:$ROOT_DIR/janusgraph-inmemory/target/classes:target/classes:$JUNIT_JAR" \
          -d target/test-classes \
          src/test/java/org/janusgraph/diskstorage/kvt/*.java 2>/dev/null || {
        echo -e "${YELLOW}Warning: Test compilation failed (may need additional dependencies)${NC}"
    }
fi

# Step 4: Run basic functionality test
echo -e "\n${BLUE}Step 4: Running basic integration test...${NC}"

# Set library path
export LD_LIBRARY_PATH="$NATIVE_DIR:$LD_LIBRARY_PATH"
export DYLD_LIBRARY_PATH="$NATIVE_DIR:$DYLD_LIBRARY_PATH"

# Run the integration test
echo "Running KVT integration test..."
cd "$SCRIPT_DIR"

java -Djava.library.path="$NATIVE_DIR" \
     -cp "$JANUSGRAPH_CORE_JAR:$ROOT_DIR/janusgraph-inmemory/target/classes:target/classes" \
     org.janusgraph.diskstorage.kvt.KVTIntegrationTest

# Step 5: Show usage information
echo -e "\n${BLUE}Step 5: Build complete!${NC}"
echo -e "${GREEN}Success! KVT backend is ready to use.${NC}"
echo ""
echo "Usage:"
echo "======"
echo ""
echo "1. Library Location:"
echo "   $NATIVE_DIR/$LIBRARY"
echo ""
echo "2. Environment Variables:"
echo "   export LD_LIBRARY_PATH=\"$NATIVE_DIR:\$LD_LIBRARY_PATH\""
echo "   export DYLD_LIBRARY_PATH=\"$NATIVE_DIR:\$DYLD_LIBRARY_PATH\"  # macOS"
echo ""
echo "3. Java System Property:"
echo "   -Djava.library.path=\"$NATIVE_DIR\""
echo ""
echo "4. Using KVT in JanusGraph:"
echo "   Configuration conf = new BaseConfiguration();"
echo "   conf.setProperty(\"storage.backend\", \"org.janusgraph.diskstorage.kvt.KVTStoreManager\");"
echo "   conf.setProperty(\"storage.kvt.use-composite-key\", true);  // or false"
echo "   JanusGraph graph = JanusGraphFactory.open(conf);"
echo ""
echo "5. Running Tests:"
echo "   cd $SCRIPT_DIR"
echo "   java -Djava.library.path=\"$NATIVE_DIR\" \\"
echo "        -cp \"\$CLASSPATH:target/classes\" \\"
echo "        org.janusgraph.diskstorage.kvt.KVTIntegrationTest"

echo -e "\n${GREEN}Build and test completed successfully!${NC}"