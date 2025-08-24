#ifndef ENTRY_LIST_H
#define ENTRY_LIST_H

#include "Entry.h"
#include <vector>
#include <iterator>

class EntryList {
private:
    std::vector<Entry> entries;

public:
    static const EntryList EMPTY_LIST;
    
    EntryList() = default;
    
    EntryList(const std::vector<Entry>& e) : entries(e) {}
    
    EntryList(const EntryList& other) : entries(other.entries) {}
    
    EntryList& operator=(const EntryList& other) {
        if (this != &other) {
            entries = other.entries;
        }
        return *this;
    }
    
    void add(const Entry& entry) {
        entries.push_back(entry);
    }
    
    size_t size() const {
        return entries.size();
    }
    
    bool empty() const {
        return entries.empty();
    }
    
    const Entry& operator[](size_t index) const {
        return entries[index];
    }
    
    Entry& operator[](size_t index) {
        return entries[index];
    }
    
    std::vector<Entry>::const_iterator begin() const {
        return entries.begin();
    }
    
    std::vector<Entry>::const_iterator end() const {
        return entries.end();
    }
    
    std::vector<Entry>::iterator begin() {
        return entries.begin();
    }
    
    std::vector<Entry>::iterator end() {
        return entries.end();
    }
    
    size_t getByteSize() const {
        size_t size = 48;  // Base object overhead
        for (const Entry& e : entries) {
            size += 32 + e.length();  // Entry overhead plus data
        }
        return size;
    }
};

#endif