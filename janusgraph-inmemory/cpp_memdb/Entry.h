#ifndef ENTRY_H
#define ENTRY_H

#include "StaticBuffer.h"

class Entry {
private:
    StaticBuffer column;
    StaticBuffer value;

public:
    Entry() = default;
    
    Entry(const StaticBuffer& col, const StaticBuffer& val) : column(col), value(val) {}
    
    const StaticBuffer& getColumn() const {
        return column;
    }
    
    const StaticBuffer& getValue() const {
        return value;
    }
    
    size_t length() const {
        return column.length() + value.length();
    }
    
    bool operator<(const Entry& other) const {
        return column < other.column;
    }
    
    bool operator==(const Entry& other) const {
        return column == other.column && value == other.value;
    }
    
    bool operator!=(const Entry& other) const {
        return !(*this == other);
    }
};

#endif