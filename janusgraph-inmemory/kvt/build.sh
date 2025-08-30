#!/bin/bash

# Build script for core KVT library and sample program

set -e  # Exit on any error

echo "Building KVT Core Library and Sample..."
echo "======================================="

CXX_FLAGS="-std=c++17 -Wall -Wextra -O2 -g"

echo "Step 1: Building KVT library..."
g++ $CXX_FLAGS -fPIC -c kvt_mem.cpp -o kvt_mem.o
ar rcs libkvt.a kvt_mem.o
echo "  ✓ Created libkvt.a"

echo "Step 2: Building KVT sample program..."
g++ $CXX_FLAGS kvt_sample.cpp kvt_mem.o -o kvt_sample -pthread
echo "  ✓ Created kvt_sample executable"

echo ""
echo "Build completed successfully!"
echo ""
echo "Usage:"
echo "  ./kvt_sample                    # Run basic KVT sample"
echo "  cd janusgraph && ./build.sh     # Build JanusGraph adapter"
echo ""
echo "Library files:"
echo "  libkvt.a                       # Static library for linking"
echo "  kvt_inc.h                      # Public API header"