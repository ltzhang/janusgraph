#ifndef IN_MEMORY_KEY_COLUMN_VALUE_STORE_H
#define IN_MEMORY_KEY_COLUMN_VALUE_STORE_H

#include "InMemoryColumnValueStore.h"
#include "SliceQuery.h"
#include <map>
#include <string>
#include <memory>
#include <mutex>

class InMemoryKeyColumnValueStore {
private:
    std::string name;
    std::map<StaticBuffer, std::shared_ptr<InMemoryColumnValueStore>> kcv;
    mutable std::mutex mutex;

public:
    explicit InMemoryKeyColumnValueStore(const std::string& storeName) : name(storeName) {}
    
    EntryList getSlice(const KeySliceQuery& query, const StoreTransaction& txh) const {
        std::lock_guard<std::mutex> lock(mutex);
        
        auto it = kcv.find(query.getKey());
        if (it == kcv.end()) {
            return EntryList();  // Empty list
        }
        return it->second->getSlice(query, txh);
    }
    
    std::map<StaticBuffer, EntryList> getSlice(
        const std::vector<StaticBuffer>& keys, 
        const SliceQuery& query, 
        const StoreTransaction& txh) const {
        
        std::map<StaticBuffer, EntryList> result;
        for (const StaticBuffer& key : keys) {
            KeySliceQuery keyQuery(key, query);
            result[key] = getSlice(keyQuery, txh);
        }
        return result;
    }
    
    void mutate(const StaticBuffer& key, 
                const std::vector<Entry>& additions, 
                const std::vector<StaticBuffer>& deletions, 
                const StoreTransaction& txh) {
        std::lock_guard<std::mutex> lock(mutex);
        
        auto it = kcv.find(key);
        if (it == kcv.end()) {
            kcv[key] = std::make_shared<InMemoryColumnValueStore>();
            it = kcv.find(key);
        }
        
        it->second->mutate(additions, deletions, txh);
    }
    
    const std::string& getName() const {
        return name;
    }
    
    void clear() {
        std::lock_guard<std::mutex> lock(mutex);
        kcv.clear();
    }
    
    void close() {
        clear();
    }
    
    size_t size() const {
        std::lock_guard<std::mutex> lock(mutex);
        return kcv.size();
    }
    
    bool isEmpty() const {
        std::lock_guard<std::mutex> lock(mutex);
        return kcv.empty();
    }
};


#endif