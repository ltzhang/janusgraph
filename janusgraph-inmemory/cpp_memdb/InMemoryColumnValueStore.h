#ifndef IN_MEMORY_COLUMN_VALUE_STORE_H
#define IN_MEMORY_COLUMN_VALUE_STORE_H

#include "Entry.h"
#include "EntryList.h"
#include "SliceQuery.h"
#include "StoreTransaction.h"
#include <map>
#include <vector>
#include <algorithm>
#include <mutex>

class InMemoryColumnValueStore {
private:
    std::map<StaticBuffer, StaticBuffer> data;
    mutable std::mutex mutex;
    static const int DEF_PAGE_SIZE = 500;

public:
    InMemoryColumnValueStore() = default;
    
    bool isEmpty(const StoreTransaction& txh) const {
        std::lock_guard<std::mutex> lock(mutex);
        return data.empty();
    }
    
    EntryList getSlice(const KeySliceQuery& query, const StoreTransaction& txh) const {
        std::lock_guard<std::mutex> lock(mutex);
        
        EntryList result;
        const SliceQuery& slice = query.getSliceQuery();
        
        auto start_it = data.lower_bound(slice.getSliceStart());
        auto end_it = data.lower_bound(slice.getSliceEnd());  // Use lower_bound for exclusive end
        
        int count = 0;
        for (auto it = start_it; it != end_it; ++it) {
            if (slice.hasLimit() && count >= slice.getLimit()) {
                break;
            }
            result.add(Entry(it->first, it->second));
            count++;
        }
        
        return result;
    }
    
    void mutate(const std::vector<Entry>& additions, 
                const std::vector<StaticBuffer>& deletions, 
                const StoreTransaction& txh) {
        std::lock_guard<std::mutex> lock(mutex);
        
        // Process deletions
        for (const StaticBuffer& deletion : deletions) {
            data.erase(deletion);
        }
        
        // Process additions
        for (const Entry& addition : additions) {
            data[addition.getColumn()] = addition.getValue();
        }
    }
    
    int numEntries(const StoreTransaction& txh) const {
        std::lock_guard<std::mutex> lock(mutex);
        return static_cast<int>(data.size());
    }
    
    void clear() {
        std::lock_guard<std::mutex> lock(mutex);
        data.clear();
    }
};

#endif