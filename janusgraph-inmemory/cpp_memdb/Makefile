CXX = g++
CXXFLAGS = -std=c++11 -Wall -Wextra -O2 -g
INCLUDES = -I.

SOURCES = cpp_memdb.cpp
OBJECTS = $(SOURCES:.cpp=.o)

TARGET = test_memdb
TEST_SOURCE = test_memdb.cpp

.PHONY: all clean test

all: $(TARGET)

$(TARGET): $(OBJECTS) $(TEST_SOURCE)
	$(CXX) $(CXXFLAGS) $(INCLUDES) $(OBJECTS) $(TEST_SOURCE) -o $(TARGET)

%.o: %.cpp
	$(CXX) $(CXXFLAGS) $(INCLUDES) -c $< -o $@

test: $(TARGET)
	./$(TARGET)

clean:
	rm -f $(OBJECTS) $(TARGET) cpp_memdb.o

install:
	@echo "No installation required for this test program"

help:
	@echo "Available targets:"
	@echo "  all     - Build the test program"
	@echo "  test    - Build and run the test program"  
	@echo "  clean   - Remove built files"
	@echo "  help    - Show this help message"