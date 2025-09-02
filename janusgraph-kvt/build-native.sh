#!/bin/bash

# Build script for JanusGraph KVT JNI native library

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NATIVE_DIR="$SCRIPT_DIR/src/main/native"
KVT_DIR="$SCRIPT_DIR/kvt"

echo "Building JanusGraph KVT JNI native library..."

# Find Java include directories
if [ -z "$JAVA_HOME" ]; then
    echo "JAVA_HOME not set, trying to detect..."
    if command -v java &> /dev/null; then
        JAVA_HOME=$(java -XshowSettings:properties -version 2>&1 | grep 'java.home' | awk '{print $3}')
        if [ -d "$JAVA_HOME/../include" ]; then
            JAVA_HOME="$JAVA_HOME/.."
        fi
    fi
fi

if [ -z "$JAVA_HOME" ] || [ ! -d "$JAVA_HOME/include" ]; then
    echo "Error: Could not find Java include directory. Please set JAVA_HOME."
    exit 1
fi

echo "Using JAVA_HOME: $JAVA_HOME"

# Detect platform
OS_NAME=$(uname -s)
case "$OS_NAME" in
    Linux*)
        PLATFORM="linux"
        LIB_EXT="so"
        JNI_MD_DIR="$JAVA_HOME/include/linux"
        ;;
    Darwin*)
        PLATFORM="darwin"
        LIB_EXT="dylib"
        JNI_MD_DIR="$JAVA_HOME/include/darwin"
        ;;
    CYGWIN*|MINGW*|MSYS*)
        PLATFORM="win32"
        LIB_EXT="dll"
        JNI_MD_DIR="$JAVA_HOME/include/win32"
        ;;
    *)
        echo "Unsupported platform: $OS_NAME"
        exit 1
        ;;
esac

# Check if kvt_mem.o exists
if [ ! -f "$KVT_DIR/kvt_mem.o" ]; then
    echo "Error: kvt_mem.o not found in $KVT_DIR"
    echo "Please build kvt_mem.o first with: g++ -c -fPIC -g -O0 kvt_memory.cpp"
    exit 1
fi

# Compile JNI wrapper
echo "Compiling JNI wrapper..."
cd "$NATIVE_DIR"

g++ -c -fPIC -std=c++17 -O2 \
    -I"$JAVA_HOME/include" \
    -I"$JNI_MD_DIR" \
    -I"$KVT_DIR" \
    janusgraph_kvt_jni.cpp \
    -o janusgraph_kvt_jni.o

# Link native library
echo "Linking native library..."
g++ -shared -fPIC \
    janusgraph_kvt_jni.o \
    "$KVT_DIR/kvt_mem.o" \
    -o "libjanusgraph-kvt-jni.$LIB_EXT"

echo "Native library built successfully: $NATIVE_DIR/libjanusgraph-kvt-jni.$LIB_EXT"

# Clean up object files
rm -f janusgraph_kvt_jni.o

echo "Build complete!"