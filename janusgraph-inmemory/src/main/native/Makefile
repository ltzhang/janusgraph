# Makefile for building JanusGraph KVT JNI library
#
# This builds a shared library that bridges Java JanusGraph code
# to the C++ KVT implementation via JNI

# Detect platform
UNAME_S := $(shell uname -s)
ifeq ($(UNAME_S),Linux)
    PLATFORM = linux
    SHARED_LIB_EXT = so
    JNI_PLATFORM = linux
endif
ifeq ($(UNAME_S),Darwin)
    PLATFORM = darwin
    SHARED_LIB_EXT = dylib
    JNI_PLATFORM = darwin
endif

# Java paths - try to detect automatically
JAVA_HOME ?= $(shell dirname $$(dirname $$(readlink -f $$(which javac))))
JNI_INCLUDE = $(JAVA_HOME)/include
JNI_PLATFORM_INCLUDE = $(JNI_INCLUDE)/$(JNI_PLATFORM)

# Compiler and flags
CXX = g++
CXXFLAGS = -Wall -Wextra -O2 -fPIC -std=c++17
INCLUDES = -I$(JNI_INCLUDE) -I$(JNI_PLATFORM_INCLUDE) -I../../.. -I../../../kvt -I../../../kvt/janusgraph
LDFLAGS = -shared

# Source files
KVT_DIR = ../../../kvt
SOURCES = janusgraph_kvt_jni.cpp \
          $(KVT_DIR)/kvt_mem.cpp

# Object files
OBJECTS = $(SOURCES:.cpp=.o)

# Output library
TARGET = libjanusgraph_kvt.$(SHARED_LIB_EXT)

# Default target
all: $(TARGET)

# Build the shared library
$(TARGET): $(OBJECTS)
	@echo "Linking JNI library: $@"
	$(CXX) $(LDFLAGS) -o $@ $^

# Compile source files
%.o: %.cpp
	@echo "Compiling: $<"
	$(CXX) $(CXXFLAGS) $(INCLUDES) -c $< -o $@

# Generate JNI headers from Java classes
headers:
	@echo "Generating JNI headers..."
	cd ../../.. && \
	javac -cp ".:$(JANUSGRAPH_CORE_JAR)" -h src/main/native \
		src/main/java/org/janusgraph/diskstorage/kvt/KVTStoreManager.java \
		src/main/java/org/janusgraph/diskstorage/kvt/KVTKeyColumnValueStore.java

# Test the library
test: $(TARGET)
	@echo "Testing KVT JNI library..."
	cd ../../../java_test && \
	export LD_LIBRARY_PATH=../src/main/native:$$LD_LIBRARY_PATH && \
	java -Djava.library.path=../src/main/native \
		-cp ".:$(JANUSGRAPH_CORE_JAR):$(JANUSGRAPH_INMEMORY_JAR)" \
		KVTIntegrationTest

# Clean build artifacts
clean:
	@echo "Cleaning build artifacts..."
	rm -f $(OBJECTS) $(TARGET)
	rm -f *.h

# Install library to system location (optional)
install: $(TARGET)
	@echo "Installing library to /usr/local/lib..."
	sudo cp $(TARGET) /usr/local/lib/
	sudo ldconfig

# Debug build
debug: CXXFLAGS += -g -DDEBUG
debug: $(TARGET)

# Print build information
info:
	@echo "Build Information:"
	@echo "  Platform: $(PLATFORM)"
	@echo "  Java Home: $(JAVA_HOME)"
	@echo "  JNI Include: $(JNI_INCLUDE)"
	@echo "  JNI Platform Include: $(JNI_PLATFORM_INCLUDE)"
	@echo "  CXX: $(CXX)"
	@echo "  CXXFLAGS: $(CXXFLAGS)"
	@echo "  Target: $(TARGET)"

# Help target
help:
	@echo "Available targets:"
	@echo "  all      - Build the JNI library (default)"
	@echo "  headers  - Generate JNI headers from Java classes"
	@echo "  test     - Build and test the library"
	@echo "  clean    - Clean build artifacts"
	@echo "  install  - Install library to system location"
	@echo "  debug    - Build with debug symbols"
	@echo "  info     - Show build information"
	@echo "  help     - Show this help message"
	@echo ""
	@echo "Environment variables:"
	@echo "  JAVA_HOME              - Path to Java installation"
	@echo "  JANUSGRAPH_CORE_JAR    - Path to janusgraph-core.jar"
	@echo "  JANUSGRAPH_INMEMORY_JAR - Path to janusgraph-inmemory.jar"

.PHONY: all headers test clean install debug info help