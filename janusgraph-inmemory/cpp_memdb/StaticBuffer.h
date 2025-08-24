#ifndef STATIC_BUFFER_H
#define STATIC_BUFFER_H

#include <vector>
#include <string>
#include <cstring>
#include <cstdint>
#include <algorithm>

class StaticBuffer {
private:
    std::vector<uint8_t> data;

public:
    StaticBuffer() = default;
    
    StaticBuffer(const std::vector<uint8_t>& d) : data(d) {}
    
    StaticBuffer(const uint8_t* bytes, size_t length) : data(bytes, bytes + length) {}
    
    StaticBuffer(const std::string& str) : data(str.begin(), str.end()) {}
    
    size_t length() const {
        return data.size();
    }
    
    const uint8_t* getData() const {
        return data.data();
    }
    
    const std::vector<uint8_t>& getBytes() const {
        return data;
    }
    
    bool operator<(const StaticBuffer& other) const {
        return data < other.data;
    }
    
    bool operator==(const StaticBuffer& other) const {
        return data == other.data;
    }
    
    bool operator!=(const StaticBuffer& other) const {
        return !(*this == other);
    }
};

#endif