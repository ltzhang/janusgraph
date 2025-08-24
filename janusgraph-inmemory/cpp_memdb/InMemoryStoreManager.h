#ifndef IN_MEMORY_STORE_MANAGER_H
#define IN_MEMORY_STORE_MANAGER_H

#include "InMemoryKeyColumnValueStore.h"
#include <map>
#include <string>
#include <memory>
#include <mutex>

class InMemoryStoreManager {
private:
    std::map<std::string, std::shared_ptr<InMemoryKeyColumnValueStore>> stores;
    mutable std::mutex mutex;

public:
    InMemoryStoreManager() = default;
    
    std::shared_ptr<StoreTransaction> beginTransaction() {
        return std::make_shared<StoreTransaction>();
    }
    
    void close() {
        std::lock_guard<std::mutex> lock(mutex);
        for (auto& pair : stores) {
            pair.second->close();
        }
        stores.clear();
    }
    
    void clearStorage() {
        std::lock_guard<std::mutex> lock(mutex);
        for (auto& pair : stores) {
            pair.second->clear();
        }
        stores.clear();
    }
    
    bool exists() const {
        std::lock_guard<std::mutex> lock(mutex);
        return !stores.empty();
    }
    
    std::shared_ptr<InMemoryKeyColumnValueStore> openDatabase(const std::string& name) {
        std::lock_guard<std::mutex> lock(mutex);
        
        auto it = stores.find(name);
        if (it == stores.end()) {
            auto store = std::make_shared<InMemoryKeyColumnValueStore>(name);
            stores[name] = store;
            return store;
        }
        return it->second;
    }
    
    void mutateMany(const std::map<std::string, 
                                      std::map<StaticBuffer, 
                                               std::pair<std::vector<Entry>, std::vector<StaticBuffer>>>>& mutations,
                    const StoreTransaction& txh) {
        for (const auto& storeMut : mutations) {
            auto store = stores.find(storeMut.first);
            if (store != stores.end()) {
                for (const auto& keyMut : storeMut.second) {
                    store->second->mutate(keyMut.first, keyMut.second.first, keyMut.second.second, txh);
                }
            }
        }
    }
    
    std::string getName() const {
        return "InMemoryStoreManager";
    }
    
    size_t getStoreCount() const {
        std::lock_guard<std::mutex> lock(mutex);
        return stores.size();
    }
};

#endif