#!/bin/bash

# Build script for JNI library and equivalence test

set -e

echo "Building JNI Library and Equivalence Test"
echo "========================================="

# Configuration
JAVA_HOME="${JAVA_HOME:-$(readlink -f $(which javac) | sed 's:/bin/javac::')}"
BUILD_DIR="build"
LIB_DIR="lib"
CPP_MEMDB_DIR="../cpp_memdb"

echo "Using Java Home: $JAVA_HOME"
echo "Build Directory: $BUILD_DIR"
echo "Library Directory: $LIB_DIR"

# Create build directories
mkdir -p "$BUILD_DIR" "$LIB_DIR"

# Step 1: Compile Java classes to generate JNI headers
echo ""
echo "Step 1: Compiling Java classes..."

# Set up classpath for Java compilation (reuse from existing build system)
PROJECT_ROOT="../.."
INMEMORY_ROOT=".."
TARGET_DIR="$INMEMORY_ROOT/target"
CORE_TARGET="$PROJECT_ROOT/janusgraph-core/target"
CLASSPATH_FILE="$PROJECT_ROOT/janusgraph-core/classpath.txt"

# Build classpath
CLASSPATH="$TARGET_DIR/classes:$CORE_TARGET/janusgraph-core-1.2.0-SNAPSHOT.jar"
if [ -f "$CLASSPATH_FILE" ]; then
    MAVEN_CLASSPATH=$(cat "$CLASSPATH_FILE")
    CLASSPATH="$CLASSPATH:$MAVEN_CLASSPATH"
fi

echo "Compiling Java classes with classpath..."
javac -cp "$CLASSPATH" -d "$BUILD_DIR" -h "$BUILD_DIR" \
    NativeInMemoryDB.java EquivalenceTest.java InMemoryDatabaseTest.java StressEquivalenceTest.java

echo "✓ Java compilation successful"

# Step 2: Compile C++ JNI library
echo ""
echo "Step 2: Compiling C++ JNI library..."

# Compile C++ memdb objects first (with -fPIC for shared library)
echo "Compiling C++ memdb objects..."
cd "$CPP_MEMDB_DIR"
make clean
g++ -std=c++11 -Wall -Wextra -O2 -g -fPIC -I. -c EntryList.cpp -o EntryList.o
cd - > /dev/null

echo "Compiling JNI library..."

# Find JNI headers
JNI_INCLUDES="-I$JAVA_HOME/include"
if [ -d "$JAVA_HOME/include/linux" ]; then
    JNI_INCLUDES="$JNI_INCLUDES -I$JAVA_HOME/include/linux"
elif [ -d "$JAVA_HOME/include/darwin" ]; then
    JNI_INCLUDES="$JNI_INCLUDES -I$JAVA_HOME/include/darwin"
fi

# Compile JNI shared library
g++ -std=c++11 -fPIC -shared \
    $JNI_INCLUDES \
    -I"$CPP_MEMDB_DIR" \
    janusgraph_jni.cpp \
    "$CPP_MEMDB_DIR/EntryList.o" \
    -o "$LIB_DIR/libjanusgraph_jni.so"

echo "✓ JNI library compilation successful"

# Step 3: Test the build
echo ""
echo "Step 3: Testing compilation..."

# Check if library was created
if [ ! -f "$LIB_DIR/libjanusgraph_jni.so" ]; then
    echo "ERROR: JNI library was not created"
    exit 1
fi

# Check if Java classes were created
if [ ! -f "$BUILD_DIR/NativeInMemoryDB.class" ]; then
    echo "ERROR: Java classes were not compiled"
    exit 1
fi

echo "✓ Build verification successful"

# Step 4: Run equivalence test
echo ""
echo "Step 4: Running equivalence test..."

# Set library path for JNI
export LD_LIBRARY_PATH="$PWD/$LIB_DIR:$LD_LIBRARY_PATH"

# Run the test
java -cp "$BUILD_DIR:$CLASSPATH" \
     -Djava.library.path="$PWD/$LIB_DIR" \
     EquivalenceTest

echo ""
echo "JNI build and test completed successfully!"