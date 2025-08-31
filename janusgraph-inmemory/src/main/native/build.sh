#!/bin/bash

# Build script for JanusGraph KVT JNI integration
# This script compiles the C++ KVT code and creates the JNI library

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}JanusGraph KVT JNI Build Script${NC}"
echo "================================"

# Check if we're in the right directory
if [[ ! -f "Makefile" ]] || [[ ! -f "janusgraph_kvt_jni.cpp" ]]; then
    echo -e "${RED}Error: Must be run from the native source directory${NC}"
    echo "Expected files: Makefile, janusgraph_kvt_jni.cpp"
    exit 1
fi

# Detect Java installation
if [[ -z "$JAVA_HOME" ]]; then
    echo -e "${YELLOW}Warning: JAVA_HOME not set, attempting to detect...${NC}"
    
    # Try to find Java
    if command -v javac &> /dev/null; then
        JAVA_HOME=$(dirname $(dirname $(readlink -f $(which javac))))
        export JAVA_HOME
        echo -e "${GREEN}Detected JAVA_HOME: $JAVA_HOME${NC}"
    else
        echo -e "${RED}Error: Java not found. Please install JDK and set JAVA_HOME${NC}"
        exit 1
    fi
else
    echo -e "${GREEN}Using JAVA_HOME: $JAVA_HOME${NC}"
fi

# Check JNI headers
JNI_INCLUDE="$JAVA_HOME/include"
if [[ ! -d "$JNI_INCLUDE" ]]; then
    echo -e "${RED}Error: JNI headers not found at $JNI_INCLUDE${NC}"
    echo "Please ensure you have a JDK installed (not just JRE)"
    exit 1
fi

# Detect platform-specific JNI headers
PLATFORM=$(uname -s | tr '[:upper:]' '[:lower:]')
JNI_PLATFORM_INCLUDE="$JNI_INCLUDE/$PLATFORM"
if [[ ! -d "$JNI_PLATFORM_INCLUDE" ]]; then
    echo -e "${RED}Error: Platform-specific JNI headers not found at $JNI_PLATFORM_INCLUDE${NC}"
    exit 1
fi

echo -e "${GREEN}JNI headers found:${NC}"
echo "  Generic: $JNI_INCLUDE"
echo "  Platform: $JNI_PLATFORM_INCLUDE"

# Check KVT source files
KVT_DIR="../../../kvt"
if [[ ! -f "$KVT_DIR/kvt_inc.h" ]] || [[ ! -f "$KVT_DIR/kvt_mem.cpp" ]]; then
    echo -e "${RED}Error: KVT source files not found${NC}"
    echo "Expected files:"
    echo "  $KVT_DIR/kvt_inc.h"
    echo "  $KVT_DIR/kvt_mem.cpp"
    echo "  $KVT_DIR/janusgraph/janusgraph_kvt_adapter.h"
    exit 1
fi

echo -e "${GREEN}KVT source files found${NC}"

# Check for required tools
if ! command -v g++ &> /dev/null; then
    echo -e "${RED}Error: g++ compiler not found${NC}"
    echo "Please install build-essential or equivalent package"
    exit 1
fi

if ! command -v make &> /dev/null; then
    echo -e "${RED}Error: make not found${NC}"
    echo "Please install build-essential or equivalent package"
    exit 1
fi

echo -e "${GREEN}Build tools found${NC}"

# Set up JanusGraph JARs for compilation (if available)
JANUSGRAPH_HOME=${JANUSGRAPH_HOME:-"$(cd ../../../../.. && pwd)"}
CORE_JAR="$JANUSGRAPH_HOME/janusgraph-core/target/classes"
INMEMORY_JAR="$JANUSGRAPH_HOME/janusgraph-inmemory/target/classes"

if [[ -d "$CORE_JAR" ]]; then
    export JANUSGRAPH_CORE_JAR="$CORE_JAR"
    echo -e "${GREEN}Found JanusGraph core classes: $CORE_JAR${NC}"
else
    echo -e "${YELLOW}Warning: JanusGraph core classes not found${NC}"
    echo "You may need to build JanusGraph first: mvn clean install -DskipTests"
fi

if [[ -d "$INMEMORY_JAR" ]]; then
    export JANUSGRAPH_INMEMORY_JAR="$INMEMORY_JAR"
    echo -e "${GREEN}Found JanusGraph inmemory classes: $INMEMORY_JAR${NC}"
fi

# Clean previous build
echo -e "${BLUE}Cleaning previous build...${NC}"
make clean

# Generate JNI headers if Java classes are available
if [[ -n "$JANUSGRAPH_CORE_JAR" ]]; then
    echo -e "${BLUE}Generating JNI headers...${NC}"
    if ! make headers; then
        echo -e "${YELLOW}Warning: Could not generate JNI headers${NC}"
        echo "This is expected if Java classes haven't been compiled yet"
    fi
fi

# Build the library
echo -e "${BLUE}Building KVT JNI library...${NC}"
if make all; then
    echo -e "${GREEN}Build successful!${NC}"
    
    # Check if library was created
    if [[ -f "libjanusgraph_kvt.so" ]] || [[ -f "libjanusgraph_kvt.dylib" ]]; then
        LIBRARY=$(ls libjanusgraph_kvt.* | head -n1)
        echo -e "${GREEN}Created library: $LIBRARY${NC}"
        
        # Show library info
        echo -e "${BLUE}Library information:${NC}"
        file "$LIBRARY" || true
        ls -la "$LIBRARY"
        
        # Test if library loads (basic test)
        echo -e "${BLUE}Testing library load...${NC}"
        if ldd "$LIBRARY" &>/dev/null || otool -L "$LIBRARY" &>/dev/null; then
            echo -e "${GREEN}Library dependencies look good${NC}"
        else
            echo -e "${YELLOW}Warning: Could not check library dependencies${NC}"
        fi
        
    else
        echo -e "${RED}Error: Library was not created${NC}"
        exit 1
    fi
else
    echo -e "${RED}Build failed!${NC}"
    exit 1
fi

echo ""
echo -e "${BLUE}Build Summary:${NC}"
echo "=============="
echo "Library: $(pwd)/libjanusgraph_kvt.*"
echo ""
echo -e "${GREEN}Next steps:${NC}"
echo "1. Add the library directory to your LD_LIBRARY_PATH (Linux) or DYLD_LIBRARY_PATH (macOS)"
echo "2. Use -Djava.library.path=$(pwd) when running Java applications"
echo "3. Run integration tests with the KVT backend"
echo ""
echo "Example:"
echo "  export LD_LIBRARY_PATH=$(pwd):\$LD_LIBRARY_PATH"
echo "  java -Djava.library.path=$(pwd) -cp <classpath> YourJanusGraphApp"