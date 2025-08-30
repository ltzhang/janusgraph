#include "cpp_memdb.h"
#include <iostream>
#include <cassert>
#include <string>
#include <vector>

void testStaticBuffer() {
    std::cout << "Testing StaticBuffer..." << std::endl;
    
    // Test creation and comparison
    StaticBuffer buf1("hello");
    StaticBuffer buf2("hello");
    StaticBuffer buf3("world");
    
    assert(buf1 == buf2);
    assert(buf1 != buf3);
    assert(buf1.length() == 5);
    
    // Test ordering
    assert(buf1 < buf3);  // "hello" < "world"
    
    std::cout << "StaticBuffer tests passed!" << std::endl;
}

void testEntry() {
    std::cout << "Testing Entry..." << std::endl;
    
    StaticBuffer col1("column1");
    StaticBuffer val1("value1");
    StaticBuffer col2("column2");
    StaticBuffer val2("value2");
    
    Entry entry1(col1, val1);
    Entry entry2(col1, val1);
    Entry entry3(col2, val2);
    
    assert(entry1 == entry2);
    assert(entry1 != entry3);
    assert(entry1.getColumn() == col1);
    assert(entry1.getValue() == val1);
    assert(entry1.length() == col1.length() + val1.length());
    
    std::cout << "Entry tests passed!" << std::endl;
}

void testEntryList() {
    std::cout << "Testing EntryList..." << std::endl;
    
    EntryList list;
    assert(list.empty());
    assert(list.size() == 0);
    
    StaticBuffer col("column");
    StaticBuffer val("value");
    Entry entry(col, val);
    
    list.add(entry);
    assert(!list.empty());
    assert(list.size() == 1);
    assert(list[0] == entry);
    
    std::cout << "EntryList tests passed!" << std::endl;
}

void testInMemoryColumnValueStore() {
    std::cout << "Testing InMemoryColumnValueStore..." << std::endl;
    
    InMemoryColumnValueStore store;
    StoreTransaction txh;
    
    assert(store.isEmpty(txh));
    assert(store.numEntries(txh) == 0);
    
    // Add some data
    std::vector<Entry> additions;
    additions.emplace_back(StaticBuffer("col1"), StaticBuffer("val1"));
    additions.emplace_back(StaticBuffer("col2"), StaticBuffer("val2"));
    
    std::vector<StaticBuffer> deletions;
    
    store.mutate(additions, deletions, txh);
    
    assert(!store.isEmpty(txh));
    assert(store.numEntries(txh) == 2);
    
    // Test slice query
    SliceQuery slice(StaticBuffer("col1"), StaticBuffer("col3"));
    KeySliceQuery keySlice(StaticBuffer("dummy"), slice);
    EntryList result = store.getSlice(keySlice, txh);
    
    assert(result.size() == 2);
    
    std::cout << "InMemoryColumnValueStore tests passed!" << std::endl;
}

void testInMemoryKeyColumnValueStore() {
    std::cout << "Testing InMemoryKeyColumnValueStore..." << std::endl;
    
    InMemoryKeyColumnValueStore store("testStore");
    StoreTransaction txh;
    
    assert(store.isEmpty());
    assert(store.getName() == "testStore");
    
    // Add data for a key
    StaticBuffer key1("key1");
    std::vector<Entry> additions;
    additions.emplace_back(StaticBuffer("col1"), StaticBuffer("val1"));
    additions.emplace_back(StaticBuffer("col2"), StaticBuffer("val2"));
    
    std::vector<StaticBuffer> deletions;
    
    store.mutate(key1, additions, deletions, txh);
    
    assert(!store.isEmpty());
    assert(store.size() == 1);
    
    // Query the data
    SliceQuery slice(StaticBuffer("col1"), StaticBuffer("col3"));
    KeySliceQuery keySlice(key1, slice);
    EntryList result = store.getSlice(keySlice, txh);
    
    assert(result.size() == 2);
    assert(result[0].getColumn() == StaticBuffer("col1"));
    assert(result[0].getValue() == StaticBuffer("val1"));
    
    // Test deletion
    deletions.push_back(StaticBuffer("col1"));
    additions.clear();
    store.mutate(key1, additions, deletions, txh);
    
    result = store.getSlice(keySlice, txh);
    assert(result.size() == 1);  // Only col2 should remain
    assert(result[0].getColumn() == StaticBuffer("col2"));
    
    std::cout << "InMemoryKeyColumnValueStore tests passed!" << std::endl;
}

void testInMemoryStoreManager() {
    std::cout << "Testing InMemoryStoreManager..." << std::endl;
    
    InMemoryStoreManager manager;
    
    assert(!manager.exists());
    assert(manager.getStoreCount() == 0);
    
    // Open a database
    auto store1 = manager.openDatabase("store1");
    assert(store1->getName() == "store1");
    assert(manager.exists());
    assert(manager.getStoreCount() == 1);
    
    // Open the same database again - should return the same instance
    auto store1_again = manager.openDatabase("store1");
    assert(store1.get() == store1_again.get());
    assert(manager.getStoreCount() == 1);
    
    // Open a different database
    auto store2 = manager.openDatabase("store2");
    assert(store2->getName() == "store2");
    assert(manager.getStoreCount() == 2);
    
    // Add some data
    auto txh = manager.beginTransaction();
    StaticBuffer key("testKey");
    std::vector<Entry> additions;
    additions.emplace_back(StaticBuffer("column"), StaticBuffer("value"));
    std::vector<StaticBuffer> deletions;
    
    store1->mutate(key, additions, deletions, *txh);
    
    // Verify data
    SliceQuery slice(StaticBuffer("a"), StaticBuffer("z"));
    KeySliceQuery keySlice(key, slice);
    EntryList result = store1->getSlice(keySlice, *txh);
    assert(result.size() == 1);
    
    // Test clear
    manager.clearStorage();
    assert(manager.getStoreCount() == 0);
    
    std::cout << "InMemoryStoreManager tests passed!" << std::endl;
}

void testComplexScenario() {
    std::cout << "Testing complex scenario..." << std::endl;
    
    InMemoryStoreManager manager;
    auto store = manager.openDatabase("complexTest");
    auto txh = manager.beginTransaction();
    
    // Add multiple keys with multiple columns each
    for (int i = 0; i < 10; i++) {
        StaticBuffer key("key" + std::to_string(i));
        
        std::vector<Entry> additions;
        for (int j = 0; j < 5; j++) {
            StaticBuffer col("col" + std::to_string(j));
            StaticBuffer val("value" + std::to_string(i) + "_" + std::to_string(j));
            additions.emplace_back(col, val);
        }
        
        std::vector<StaticBuffer> deletions;
        store->mutate(key, additions, deletions, *txh);
    }
    
    // Verify all data is there
    assert(store->size() == 10);
    
    // Query specific slices
    StaticBuffer testKey("key5");
    SliceQuery slice(StaticBuffer("col1"), StaticBuffer("col4"));
    KeySliceQuery keySlice(testKey, slice);
    EntryList result = store->getSlice(keySlice, *txh);
    
    
    assert(result.size() == 3);  // col1, col2, col3 (col4 is exclusive upper bound)
    
    // Test partial deletion
    std::vector<StaticBuffer> deletions;
    deletions.emplace_back("col2");
    std::vector<Entry> additions;
    
    store->mutate(testKey, additions, deletions, *txh);
    
    // Verify deletion worked
    result = store->getSlice(keySlice, *txh);
    assert(result.size() == 2);  // col1, col3 should remain
    
    std::cout << "Complex scenario tests passed!" << std::endl;
}

int main() {
    std::cout << "Starting C++ InMemory Database Tests..." << std::endl;
    
    try {
        testStaticBuffer();
        testEntry();
        testEntryList();
        testInMemoryColumnValueStore();
        testInMemoryKeyColumnValueStore();
        testInMemoryStoreManager();
        testComplexScenario();
        
        std::cout << "\nAll tests passed successfully!" << std::endl;
        std::cout << "C++ InMemory Database implementation is working correctly." << std::endl;
        
    } catch (const std::exception& e) {
        std::cerr << "Test failed with exception: " << e.what() << std::endl;
        return 1;
    } catch (...) {
        std::cerr << "Test failed with unknown exception" << std::endl;
        return 1;
    }
    
    return 0;
}