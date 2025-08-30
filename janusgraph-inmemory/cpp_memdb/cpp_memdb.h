#ifndef CPP_MEMDB_H
#define CPP_MEMDB_H

#include <vector>
#include <string>
#include <cstring>
#include <cstdint>
#include <algorithm>
#include <map>
#include <iterator>
#include <memory>
#include <mutex>

// Forward declarations
class StaticBuffer;
class Entry;
class EntryList;
class SliceQuery;
class KeySliceQuery;
class StoreTransaction;
class InMemoryColumnValueStore;
class InMemoryKeyColumnValueStore;
class InMemoryStoreManager;

//=============================================================================
// StaticBuffer - Immutable byte buffer for keys and values
//=============================================================================
class StaticBuffer {
private:
    std::vector<uint8_t> data;

public:
    StaticBuffer() = default;
    
    StaticBuffer(const std::vector<uint8_t>& d) : data(d) {}
    
    StaticBuffer(const uint8_t* bytes, size_t length) : data(bytes, bytes + length) {}
    
    StaticBuffer(const std::string& str) : data(str.begin(), str.end()) {}
    
    inline size_t length() const {
        return data.size();
    }
    
    inline const uint8_t* getData() const {
        return data.data();
    }
    
    inline const std::vector<uint8_t>& getBytes() const {
        return data;
    }
    
    inline bool operator<(const StaticBuffer& other) const {
        return data < other.data;
    }
    
    inline bool operator==(const StaticBuffer& other) const {
        return data == other.data;
    }
    
    inline bool operator!=(const StaticBuffer& other) const {
        return !(*this == other);
    }
};

//=============================================================================
// Entry - Key-value pair container
//=============================================================================
class Entry {
private:
    StaticBuffer column;
    StaticBuffer value;

public:
    Entry() = default;
    
    Entry(const StaticBuffer& col, const StaticBuffer& val) : column(col), value(val) {}
    
    inline const StaticBuffer& getColumn() const {
        return column;
    }
    
    inline const StaticBuffer& getValue() const {
        return value;
    }
    
    inline size_t length() const {
        return column.length() + value.length();
    }
    
    inline bool operator<(const Entry& other) const {
        return column < other.column;
    }
    
    inline bool operator==(const Entry& other) const {
        return column == other.column && value == other.value;
    }
    
    inline bool operator!=(const Entry& other) const {
        return !(*this == other);
    }
};

//=============================================================================
// EntryList - Ordered collection of entries
//=============================================================================
class EntryList {
private:
    std::vector<Entry> entries;

public:
    static const EntryList EMPTY_LIST;
    
    EntryList() = default;
    
    EntryList(const std::vector<Entry>& e) : entries(e) {}
    
    EntryList(const EntryList& other) : entries(other.entries) {}
    
    inline EntryList& operator=(const EntryList& other) {
        if (this != &other) {
            entries = other.entries;
        }
        return *this;
    }
    
    inline void add(const Entry& entry) {
        entries.push_back(entry);
    }
    
    inline size_t size() const {
        return entries.size();
    }
    
    inline bool empty() const {
        return entries.empty();
    }
    
    inline const Entry& operator[](size_t index) const {
        return entries[index];
    }
    
    inline Entry& operator[](size_t index) {
        return entries[index];
    }
    
    inline std::vector<Entry>::const_iterator begin() const {
        return entries.begin();
    }
    
    inline std::vector<Entry>::const_iterator end() const {
        return entries.end();
    }
    
    inline std::vector<Entry>::iterator begin() {
        return entries.begin();
    }
    
    inline std::vector<Entry>::iterator end() {
        return entries.end();
    }
    
    inline size_t getByteSize() const {
        size_t size = 48;  // Base object overhead
        for (const Entry& e : entries) {
            size += 32 + e.length();  // Entry overhead plus data
        }
        return size;
    }
};

//=============================================================================
// SliceQuery - Range query definition
//=============================================================================
class SliceQuery {
private:
    StaticBuffer sliceStart;
    StaticBuffer sliceEnd;
    int limit;

public:
    SliceQuery(const StaticBuffer& start, const StaticBuffer& end, int lim = -1) 
        : sliceStart(start), sliceEnd(end), limit(lim) {}
    
    inline const StaticBuffer& getSliceStart() const {
        return sliceStart;
    }
    
    inline const StaticBuffer& getSliceEnd() const {
        return sliceEnd;
    }
    
    inline int getLimit() const {
        return limit;
    }
    
    inline bool hasLimit() const {
        return limit > 0;
    }
};

//=============================================================================
// KeySliceQuery - Combines a key with a slice query
//=============================================================================
class KeySliceQuery {
private:
    StaticBuffer key;
    SliceQuery sliceQuery;

public:
    KeySliceQuery(const StaticBuffer& k, const SliceQuery& slice) 
        : key(k), sliceQuery(slice) {}
    
    inline const StaticBuffer& getKey() const {
        return key;
    }
    
    inline const SliceQuery& getSliceQuery() const {
        return sliceQuery;
    }
};

//=============================================================================
// StoreTransaction - Transaction handle
//=============================================================================
class StoreTransaction {
private:
    std::map<std::string, bool> configuration;

public:
    StoreTransaction() {
        configuration["transactional"] = false;  // Default non-transactional
    }
    
    inline bool isTransactional() const {
        auto it = configuration.find("transactional");
        return it != configuration.end() ? it->second : false;
    }
    
    inline void setTransactional(bool transactional) {
        configuration["transactional"] = transactional;
    }
};

//=============================================================================
// InMemoryColumnValueStore - Column-family storage abstraction
//=============================================================================
class InMemoryColumnValueStore {
private:
    std::map<StaticBuffer, StaticBuffer> data;
    mutable std::mutex mutex;
    static const int DEF_PAGE_SIZE = 500;

public:
    InMemoryColumnValueStore() = default;
    
    inline bool isEmpty(const StoreTransaction& txh) const {
        std::lock_guard<std::mutex> lock(mutex);
        return data.empty();
    }
    
    inline EntryList getSlice(const KeySliceQuery& query, const StoreTransaction& txh) const {
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
    
    inline void mutate(const std::vector<Entry>& additions, 
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
    
    inline int numEntries(const StoreTransaction& txh) const {
        std::lock_guard<std::mutex> lock(mutex);
        return static_cast<int>(data.size());
    }
    
    inline void clear() {
        std::lock_guard<std::mutex> lock(mutex);
        data.clear();
    }
};

//=============================================================================
// InMemoryKeyColumnValueStore - Key-based storage with column families
//=============================================================================
class InMemoryKeyColumnValueStore {
private:
    std::string name;
    std::map<StaticBuffer, std::shared_ptr<InMemoryColumnValueStore>> kcv;
    mutable std::mutex mutex;

public:
    explicit InMemoryKeyColumnValueStore(const std::string& storeName) : name(storeName) {}
    
    inline EntryList getSlice(const KeySliceQuery& query, const StoreTransaction& txh) const {
        std::lock_guard<std::mutex> lock(mutex);
        
        auto it = kcv.find(query.getKey());
        if (it == kcv.end()) {
            return EntryList();  // Empty list
        }
        return it->second->getSlice(query, txh);
    }
    
    inline std::map<StaticBuffer, EntryList> getSlice(
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
    
    inline void mutate(const StaticBuffer& key, 
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
    
    inline const std::string& getName() const {
        return name;
    }
    
    inline void clear() {
        std::lock_guard<std::mutex> lock(mutex);
        kcv.clear();
    }
    
    inline void close() {
        clear();
    }
    
    inline size_t size() const {
        std::lock_guard<std::mutex> lock(mutex);
        return kcv.size();
    }
    
    inline bool isEmpty() const {
        std::lock_guard<std::mutex> lock(mutex);
        return kcv.empty();
    }
};

//=============================================================================
// InMemoryStoreManager - Top-level storage manager
//=============================================================================
class InMemoryStoreManager {
private:
    std::map<std::string, std::shared_ptr<InMemoryKeyColumnValueStore>> stores;
    mutable std::mutex mutex;

public:
    InMemoryStoreManager() = default;
    
    inline std::shared_ptr<StoreTransaction> beginTransaction() {
        return std::make_shared<StoreTransaction>();
    }
    
    inline void close() {
        std::lock_guard<std::mutex> lock(mutex);
        for (auto& pair : stores) {
            pair.second->close();
        }
        stores.clear();
    }
    
    inline void clearStorage() {
        std::lock_guard<std::mutex> lock(mutex);
        for (auto& pair : stores) {
            pair.second->clear();
        }
        stores.clear();
    }
    
    inline bool exists() const {
        std::lock_guard<std::mutex> lock(mutex);
        return !stores.empty();
    }
    
    inline std::shared_ptr<InMemoryKeyColumnValueStore> openDatabase(const std::string& name) {
        std::lock_guard<std::mutex> lock(mutex);
        
        auto it = stores.find(name);
        if (it == stores.end()) {
            auto store = std::make_shared<InMemoryKeyColumnValueStore>(name);
            stores[name] = store;
            return store;
        }
        return it->second;
    }
    
    inline void mutateMany(const std::map<std::string, 
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
    
    inline std::string getName() const {
        return "InMemoryStoreManager";
    }
    
    inline size_t getStoreCount() const {
        std::lock_guard<std::mutex> lock(mutex);
        return stores.size();
    }
};

#endif // CPP_MEMDB_H