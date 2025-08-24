# Makefile for JanusGraph InMemory Database Test
# Simple command-line build without Maven

JAVA_HOME ?= $(shell readlink -f /usr/bin/javac | sed "s:bin/javac::")
JAVA_BIN = $(JAVA_HOME)/bin
JAVAC = $(JAVA_BIN)/javac
JAVA = $(JAVA_BIN)/java

# Project paths
PROJECT_ROOT = ../..
INMEMORY_ROOT = ..
TARGET_DIR = $(INMEMORY_ROOT)/target
CORE_TARGET = $(PROJECT_ROOT)/janusgraph-core/target

# Source directories
SRC_MAIN = $(INMEMORY_ROOT)/src/main/java
SRC_TEST = .

# Required JAR files - get classpath from Maven  
CLASSPATH_FILE = $(PROJECT_ROOT)/janusgraph-core/classpath.txt
MAVEN_CLASSPATH = $(shell if [ -f "$(CLASSPATH_FILE)" ]; then cat $(CLASSPATH_FILE); else echo ""; fi)
JARS = $(TARGET_DIR)/classes:$(CORE_TARGET)/janusgraph-core-1.2.0-SNAPSHOT.jar:$(MAVEN_CLASSPATH)

# Build directory
BUILD_DIR = build
CLASSES_DIR = $(BUILD_DIR)/classes

# Main class
MAIN_CLASS = InMemoryDatabaseTest

.PHONY: all clean test run help check-jars setup jni eqv_test stress_eqv_test

all: setup $(CLASSES_DIR)/$(MAIN_CLASS).class

setup:
	@echo "Setting up build environment..."
	@mkdir -p $(CLASSES_DIR)
	@echo "Java Home: $(JAVA_HOME)"
	@echo "Using javac: $(JAVAC)"

check-jars:
	@echo "Checking for required JAR files..."
	@if [ ! -f "$(CORE_TARGET)/janusgraph-core-1.2.0-SNAPSHOT.jar" ]; then \
		echo "ERROR: JanusGraph core JAR not found. Please build the project first with:"; \
		echo "  cd $(PROJECT_ROOT) && mvn clean install -DskipTests"; \
		exit 1; \
	fi
	@if [ ! -d "$(TARGET_DIR)/classes" ]; then \
		echo "ERROR: InMemory classes not found. Please build janusgraph-inmemory first with:"; \
		echo "  cd $(INMEMORY_ROOT) && mvn compile"; \
		exit 1; \
	fi
	@if [ ! -f "$(CLASSPATH_FILE)" ]; then \
		echo "Generating Maven classpath..."; \
		cd $(PROJECT_ROOT) && mvn dependency:build-classpath -Dmdep.outputFile=janusgraph-core/classpath.txt -pl janusgraph-core -q; \
	fi
	@echo "✓ All required dependencies found"

$(CLASSES_DIR)/$(MAIN_CLASS).class: $(SRC_TEST)/$(MAIN_CLASS).java check-jars
	@echo "Compiling Java test program..."
	@$(JAVAC) -cp "$(JARS)" -d $(CLASSES_DIR) $(SRC_TEST)/$(MAIN_CLASS).java
	@echo "✓ Compilation successful"

test: run

run: $(CLASSES_DIR)/$(MAIN_CLASS).class
	@echo "Running JanusGraph InMemory Database Tests..."
	@echo "================================================"
	@$(JAVA) -cp "$(CLASSES_DIR):$(JARS)" $(MAIN_CLASS)

clean:
	@echo "Cleaning build artifacts..."
	@rm -rf $(BUILD_DIR)
	@echo "✓ Clean complete"

jni:
	@echo "Building JNI library and equivalence test..."
	@./build-jni.sh

eqv_test: jni
	@echo "Running equivalence test..."
	@export LD_LIBRARY_PATH="$$PWD/lib:$$LD_LIBRARY_PATH" && \
	 $(JAVA) -cp "build:$(JARS)" -Djava.library.path="$$PWD/lib" EquivalenceTest

stress_eqv_test: jni
	@echo "Running stress equivalence test (default 2min single + 3min multi)..."
	@export LD_LIBRARY_PATH="$$PWD/lib:$$LD_LIBRARY_PATH" && \
	 $(JAVA) -cp "build:$(JARS)" -Djava.library.path="$$PWD/lib" StressEquivalenceTest

stress_quick: jni
	@echo "Running quick stress test (30sec single + 30sec multi)..."
	@export LD_LIBRARY_PATH="$$PWD/lib:$$LD_LIBRARY_PATH" && \
	 $(JAVA) -cp "build:$(JARS)" -Djava.library.path="$$PWD/lib" StressEquivalenceTest 0.5 0.5 2

debug_stress: jni
	@echo "Running debug stress test with detailed mismatch analysis..."
	@export LD_LIBRARY_PATH="$$PWD/lib:$$LD_LIBRARY_PATH" && \
	 $(JAVA) -cp "build:$(JARS)" -Djava.library.path="$$PWD/lib" StressEquivalenceTest 0.5 0.5 2 debug

help:
	@echo "JanusGraph InMemory Database Test Makefile"
	@echo "==========================================="
	@echo ""
	@echo "Available targets:"
	@echo "  all              - Build the test program (default)"
	@echo "  test             - Build and run the test program"
	@echo "  run              - Run the test program (builds if needed)"
	@echo "  jni              - Build JNI library and equivalence test"
	@echo "  eqv_test         - Build and run equivalence test (Java vs C++)"
	@echo "  stress_eqv_test  - Run stress equivalence test (2min + 3min default)"
	@echo "  stress_quick     - Run quick stress test (30sec + 30sec)"
	@echo "  debug_stress     - Run debug stress test with detailed mismatch analysis"
	@echo "  clean            - Remove build artifacts"
	@echo "  check-jars       - Verify required dependencies are available"
	@echo "  help             - Show this help message"
	@echo ""
	@echo "Prerequisites:"
	@echo "  - JanusGraph core must be built: cd ../.. && mvn clean install -DskipTests"
	@echo "  - InMemory module must be compiled: cd .. && mvn compile"
	@echo ""
	@echo "JNI Prerequisites:"
	@echo "  - C++ compiler (g++) with C++11 support"
	@echo "  - JNI headers (usually in JAVA_HOME/include)"
	@echo ""
	@echo "Stress test usage:"
	@echo "  make stress_eqv_test [ARGS]  - Run with custom args: singleMinutes multiMinutes threadCount"
	@echo "  Example: make stress_eqv_test ARGS='1 2 8'  # 1min single, 2min multi, 8 threads"
	@echo ""
	@echo "Environment variables:"
	@echo "  JAVA_HOME  - Java installation directory (auto-detected: $(JAVA_HOME))"