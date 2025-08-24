#!/bin/bash

# Simple build and test script for JanusGraph InMemory Database Test
# Alternative to Makefile for systems that prefer shell scripts

set -e  # Exit on any error

echo "JanusGraph InMemory Database Test Builder"
echo "========================================"

# Configuration
PROJECT_ROOT="../.."
INMEMORY_ROOT=".."
TARGET_DIR="$INMEMORY_ROOT/target"
CORE_TARGET="$PROJECT_ROOT/janusgraph-core/target"
BUILD_DIR="build"
CLASSES_DIR="$BUILD_DIR/classes"
MAIN_CLASS="InMemoryDatabaseTest"

# Find Java
if [ -n "$JAVA_HOME" ]; then
    JAVAC="$JAVA_HOME/bin/javac"
    JAVA="$JAVA_HOME/bin/java"
else
    JAVAC=$(which javac)
    JAVA=$(which java)
    JAVA_HOME=$(readlink -f $JAVAC | sed "s:bin/javac::")
fi

echo "Using Java Home: $JAVA_HOME"
echo "Using javac: $JAVAC"
echo "Using java: $JAVA"

# Function to check dependencies
check_dependencies() {
    echo ""
    echo "Checking dependencies..."
    
    if [ ! -f "$CORE_TARGET/janusgraph-core-1.2.0-SNAPSHOT.jar" ]; then
        echo "ERROR: JanusGraph core JAR not found at $CORE_TARGET/janusgraph-core-1.2.0-SNAPSHOT.jar"
        echo "Please build the project first with:"
        echo "  cd $PROJECT_ROOT && mvn clean install -DskipTests"
        exit 1
    fi
    
    if [ ! -d "$TARGET_DIR/classes" ]; then
        echo "ERROR: InMemory classes not found at $TARGET_DIR/classes"
        echo "Please build janusgraph-inmemory first with:"
        echo "  cd $INMEMORY_ROOT && mvn compile"
        exit 1
    fi
    
    echo "✓ All required dependencies found"
}

# Function to build
build() {
    echo ""
    echo "Building test program..."
    
    # Create build directory
    mkdir -p "$CLASSES_DIR"
    
    # Set up classpath using Maven-generated classpath
    CLASSPATH="$TARGET_DIR/classes:$CORE_TARGET/janusgraph-core-1.2.0-SNAPSHOT.jar"
    
    # Add Maven dependencies if classpath file exists
    CLASSPATH_FILE="$PROJECT_ROOT/janusgraph-core/classpath.txt"
    if [ -f "$CLASSPATH_FILE" ]; then
        MAVEN_CLASSPATH=$(cat "$CLASSPATH_FILE")
        CLASSPATH="$CLASSPATH:$MAVEN_CLASSPATH"
    else
        echo "Generating Maven classpath..."
        cd "$PROJECT_ROOT" && mvn dependency:build-classpath -Dmdep.outputFile=janusgraph-core/classpath.txt -pl janusgraph-core -q
        if [ -f "$CLASSPATH_FILE" ]; then
            MAVEN_CLASSPATH=$(cat "$CLASSPATH_FILE")
            CLASSPATH="$CLASSPATH:$MAVEN_CLASSPATH"
        fi
        cd - > /dev/null
    fi
    
    echo "Classpath: $CLASSPATH"
    
    # Compile
    "$JAVAC" -cp "$CLASSPATH" -d "$CLASSES_DIR" "$MAIN_CLASS.java"
    
    echo "✓ Build successful"
}

# Function to run tests
run_tests() {
    echo ""
    echo "Running tests..."
    echo "================"
    
    # Set up runtime classpath
    RUNTIME_CLASSPATH="$CLASSES_DIR:$TARGET_DIR/classes:$CORE_TARGET/janusgraph-core-1.2.0-SNAPSHOT.jar"
    
    # Add Maven dependencies if classpath file exists
    CLASSPATH_FILE="$PROJECT_ROOT/janusgraph-core/classpath.txt"
    if [ -f "$CLASSPATH_FILE" ]; then
        MAVEN_CLASSPATH=$(cat "$CLASSPATH_FILE")
        RUNTIME_CLASSPATH="$RUNTIME_CLASSPATH:$MAVEN_CLASSPATH"
    fi
    
    # Run the test
    "$JAVA" -cp "$RUNTIME_CLASSPATH" "$MAIN_CLASS"
}

# Function to clean
clean() {
    echo ""
    echo "Cleaning build artifacts..."
    rm -rf "$BUILD_DIR"
    echo "✓ Clean complete"
}

# Function to show help
show_help() {
    echo ""
    echo "Usage: $0 [command]"
    echo ""
    echo "Commands:"
    echo "  build    - Build the test program"
    echo "  test     - Build and run the test program (default)"
    echo "  run      - Run the test program (builds if needed)"
    echo "  clean    - Remove build artifacts"
    echo "  check    - Check dependencies only"
    echo "  help     - Show this help message"
    echo ""
    echo "Prerequisites:"
    echo "  - JanusGraph core must be built: cd ../.. && mvn clean install -DskipTests"
    echo "  - InMemory module must be compiled: cd .. && mvn compile"
}

# Main logic
case "${1:-test}" in
    "build")
        check_dependencies
        build
        ;;
    "test")
        check_dependencies
        build
        run_tests
        ;;
    "run")
        if [ ! -f "$CLASSES_DIR/$MAIN_CLASS.class" ]; then
            echo "Class file not found, building first..."
            check_dependencies
            build
        fi
        run_tests
        ;;
    "clean")
        clean
        ;;
    "check")
        check_dependencies
        ;;
    "help"|"-h"|"--help")
        show_help
        ;;
    *)
        echo "Unknown command: $1"
        show_help
        exit 1
        ;;
esac

echo ""
echo "Done."