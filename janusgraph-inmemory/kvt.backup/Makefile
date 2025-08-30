# Makefile for Core KVT Library

CXX = g++
CXXFLAGS = -std=c++17 -Wall -Wextra -O2 -g

# Targets
TARGETS = kvt_sample libkvt.a
OBJECTS = kvt_mem.o

# Default target
all: $(TARGETS)

# Build the KVT library
libkvt.a: $(OBJECTS)
	ar rcs $@ $^

# Build KVT object file
kvt_mem.o: kvt_mem.cpp kvt_mem.h kvt_inc.h
	$(CXX) $(CXXFLAGS) -fPIC -c $< -o $@

# Build the KVT sample program
kvt_sample: kvt_sample.cpp kvt_mem.o kvt_inc.h
	$(CXX) $(CXXFLAGS) kvt_sample.cpp kvt_mem.o -o $@ -pthread

# Run sample
sample: kvt_sample
	./kvt_sample

# Build JanusGraph adapter (in subdirectory)
janusgraph:
	cd janusgraph && $(MAKE) all

# Test JanusGraph adapter (in subdirectory)
test-janusgraph:
	cd janusgraph && $(MAKE) test

# Clean build artifacts
clean:
	rm -f $(TARGETS) $(OBJECTS)
	cd janusgraph && $(MAKE) clean 2>/dev/null || true

# Clean all including subdirectories
clean-all: clean

# Help
help:
	@echo "Available targets:"
	@echo "  all              - Build KVT library and sample"
	@echo "  libkvt.a         - Build just the KVT library"
	@echo "  kvt_sample       - Build just the sample program"
	@echo "  sample           - Build and run the KVT sample"
	@echo "  janusgraph       - Build JanusGraph adapter"
	@echo "  test-janusgraph  - Build and test JanusGraph adapter"
	@echo "  clean            - Remove build artifacts"
	@echo "  help             - Show this help message"

# Phony targets
.PHONY: all sample janusgraph test-janusgraph clean clean-all help