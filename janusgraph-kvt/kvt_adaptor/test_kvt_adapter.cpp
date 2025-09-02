/**
 * JanusGraph KVT Adapter Test Program - Updated for table_id API
 * 
 * Tests both storage methods:
 * 1. Serialized columns (all columns in single value)
 * 2. Composite key (column incorporated into key)
 */

#include "janusgraph_kvt_adapter.h"
#include <iostream>
#include <cassert>
#include <chrono>
#include <vector>
#include <map>

// Define the global flag
namespace janusgraph {
    bool g_use_composite_key_method = false;
}

using namespace janusgraph;

void print_separator(const std::string& title) {
    std::cout << "\n========================================" << std::endl;
    std::cout << " " << title << std::endl;
    std::cout << "========================================" << std::endl;
}

void print_test_result(const std::string& test_name, bool passed) {
    std::cout << "  [" << (passed ? "✓" : "✗") << "] " << test_name << std::endl;
    if (!passed) {
        std::cerr << "    TEST FAILED!" << std::endl;
        exit(1);
    }
}

void test_basic_operations(const std::string& method_name) {
    print_separator("Basic Operations Test - " + method_name);
    
    JanusGraphKVTAdapter adapter;
    std::string error;
    
    // Create a table for testing
    std::string table_name = g_use_composite_key_method ? "composite_test" : "serialized_test";
    std::string partition_method = g_use_composite_key_method ? "range" : "hash";
    
    uint64_t table_id;
    KVTError result = kvt_create_table(table_name, partition_method, table_id, error);
    if (result != KVTError::SUCCESS && result != KVTError::TABLE_ALREADY_EXISTS) {
        std::cerr << "Failed to create table: " << error << std::endl;
        return;
    }
    
    // If table already exists, get its ID
    if (result == KVTError::TABLE_ALREADY_EXISTS) {
        kvt_get_table_id(table_name, table_id, error);
    }
    
    std::cout << "  Using table: " << table_name << " (ID: " << table_id << ")" << std::endl;
    
    // Test 1: Set and get single column
    bool test1 = adapter.set_column(0, table_id, "vertex:1", "name", "Alice", error);
    print_test_result("Set single column", test1);
    
    std::string value;
    bool test2 = adapter.get_column(0, table_id, "vertex:1", "name", value, error);
    print_test_result("Get single column", test2 && value == "Alice");
    
    // Test 2: Set multiple columns for same key
    adapter.set_column(0, table_id, "vertex:1", "age", "30", error);
    adapter.set_column(0, table_id, "vertex:1", "city", "New York", error);
    adapter.set_column(0, table_id, "vertex:1", "email", "alice@example.com", error);
    
    bool test3 = adapter.get_column(0, table_id, "vertex:1", "age", value, error);
    print_test_result("Set/Get multiple columns - age", test3 && value == "30");
    
    bool test4 = adapter.get_column(0, table_id, "vertex:1", "city", value, error);
    print_test_result("Set/Get multiple columns - city", test4 && value == "New York");
    
    // Test 3: Get all columns
    auto columns = adapter.get_all_columns(0, table_id, "vertex:1", error);
    print_test_result("Get all columns", columns.size() == 4);
    
    if (columns.size() == 4) {
        std::cout << "  Retrieved columns:" << std::endl;
        for (const auto& cv : columns) {
            std::cout << "    " << cv.column << " = " << cv.value << std::endl;
        }
    }
    
    // Test 4: Update existing column
    adapter.set_column(0, table_id, "vertex:1", "age", "31", error);
    bool test5 = adapter.get_column(0, table_id, "vertex:1", "age", value, error);
    print_test_result("Update existing column", test5 && value == "31");
    
    // Test 5: Delete column
    bool test6 = adapter.delete_column(0, table_id, "vertex:1", "email", error);
    bool test7 = !adapter.get_column(0, table_id, "vertex:1", "email", value, error);
    print_test_result("Delete column", test6 && test7);
    
    columns = adapter.get_all_columns(0, table_id, "vertex:1", error);
    print_test_result("Verify column deleted", columns.size() == 3);
    
    // Test 6: Delete entire key
    bool test8 = adapter.delete_key(0, table_id, "vertex:1", error);
    columns = adapter.get_all_columns(0, table_id, "vertex:1", error);
    print_test_result("Delete entire key", test8 && columns.empty());
}

void test_transactions(const std::string& method_name) {
    print_separator("Transaction Test - " + method_name);
    
    JanusGraphKVTAdapter adapter;
    std::string error;
    
    // Get table ID
    std::string table_name = g_use_composite_key_method ? "composite_test" : "serialized_test";
    uint64_t table_id;
    kvt_get_table_id(table_name, table_id, error);
    
    // Start a transaction
    uint64_t tx_id;
    KVTError tx_result = kvt_start_transaction(tx_id, error);
    if (tx_result != KVTError::SUCCESS) {
        std::cerr << "Failed to start transaction: " << error << std::endl;
        return;
    }
    
    // Set columns within transaction
    adapter.set_column(tx_id, table_id, "vertex:2", "name", "Bob", error);
    adapter.set_column(tx_id, table_id, "vertex:2", "status", "active", error);
    
    // Read within transaction
    std::string value;
    bool test1 = adapter.get_column(tx_id, table_id, "vertex:2", "name", value, error);
    print_test_result("Read within transaction", test1 && value == "Bob");
    
    // Commit transaction
    bool test2 = kvt_commit_transaction(tx_id, error) == KVTError::SUCCESS;
    print_test_result("Commit transaction", test2);
    
    // Verify data persisted
    bool test3 = adapter.get_column(0, table_id, "vertex:2", "name", value, error);
    print_test_result("Verify data after commit", test3 && value == "Bob");
    
    // Test rollback
    kvt_start_transaction(tx_id, error);
    adapter.set_column(tx_id, table_id, "vertex:3", "name", "Charlie", error);
    kvt_rollback_transaction(tx_id, error);
    
    bool test4 = !adapter.get_column(0, table_id, "vertex:3", "name", value, error);
    print_test_result("Verify rollback", test4);
}

void test_batch_operations(const std::string& method_name) {
    print_separator("Batch Operations Test - " + method_name);
    
    JanusGraphKVTAdapter adapter;
    std::string error;
    
    // Get table ID
    std::string table_name = g_use_composite_key_method ? "composite_test" : "serialized_test";
    uint64_t table_id;
    kvt_get_table_id(table_name, table_id, error);
    
    // Set multiple columns at once
    std::vector<ColumnValue> columns = {
        {"property1", "value1"},
        {"property2", "value2"},
        {"property3", "value3"},
        {"property4", "value4"},
        {"property5", "value5"}
    };
    
    bool test1 = adapter.set_columns(0, table_id, "vertex:batch", columns, error);
    print_test_result("Batch set columns", test1);
    
    // Verify all columns were set
    auto retrieved = adapter.get_all_columns(0, table_id, "vertex:batch", error);
    print_test_result("Verify batch set", retrieved.size() == 5);
    
    // Update some columns in batch
    std::vector<ColumnValue> updates = {
        {"property2", "updated_value2"},
        {"property4", "updated_value4"},
        {"property6", "new_value6"}  // New column
    };
    
    bool test2 = adapter.set_columns(0, table_id, "vertex:batch", updates, error);
    print_test_result("Batch update columns", test2);
    
    retrieved = adapter.get_all_columns(0, table_id, "vertex:batch", error);
    print_test_result("Verify batch update", retrieved.size() == 6);
    
    // Verify specific updates
    std::string value;
    adapter.get_column(0, table_id, "vertex:batch", "property2", value, error);
    print_test_result("Verify updated value", value == "updated_value2");
    
    adapter.get_column(0, table_id, "vertex:batch", "property6", value, error);
    print_test_result("Verify new column", value == "new_value6");
}

void test_edge_cases(const std::string& method_name) {
    print_separator("Edge Cases Test - " + method_name);
    
    JanusGraphKVTAdapter adapter;
    std::string error;
    
    // Get table ID
    std::string table_name = g_use_composite_key_method ? "composite_test" : "serialized_test";
    uint64_t table_id;
    kvt_get_table_id(table_name, table_id, error);
    
    // Test 1: Empty value
    bool test1 = adapter.set_column(0, table_id, "edge_key1", "empty_val", "", error);
    std::string value;
    bool test1_get = adapter.get_column(0, table_id, "edge_key1", "empty_val", value, error);
    print_test_result("Empty value storage", test1 && test1_get && value.empty());
    
    // Test 2: Special characters in values
    std::string special_value = "Value with special chars: !@#$%^&*()[]{}|;':\",./<>?";
    bool test2 = adapter.set_column(0, table_id, "edge_key2", "special", special_value, error);
    bool test2_get = adapter.get_column(0, table_id, "edge_key2", "special", value, error);
    print_test_result("Special characters", test2 && test2_get && value == special_value);
    
    // Test 3: Binary data in values
    std::string binary_value;
    for (int i = 0; i < 256; ++i) {
        binary_value += static_cast<char>(i);
    }
    
    bool test3 = adapter.set_column(0, table_id, "edge_key3", "binary", binary_value, error);
    std::string retrieved_binary;
    bool test3_get = adapter.get_column(0, table_id, "edge_key3", "binary", retrieved_binary, error);
    print_test_result("Binary data", test3 && test3_get && retrieved_binary == binary_value);
    
    // Test 4: Non-existent key/column
    bool test4 = !adapter.get_column(0, table_id, "nonexistent", "col", value, error);
    print_test_result("Non-existent key", test4);
    
    // Test 5: Delete non-existent column
    bool test5 = !adapter.delete_column(0, table_id, "edge_key1", "nonexistent", error);
    print_test_result("Delete non-existent column", test5);
}

int main() {
    std::cout << "==================================" << std::endl;
    std::cout << "  JanusGraph KVT Adapter Test    " << std::endl;
    std::cout << "  (Updated for table_id API)     " << std::endl;
    std::cout << "==================================" << std::endl;
    
    // Initialize KVT system
    if (kvt_initialize() != KVTError::SUCCESS) {
        std::cerr << "Failed to initialize KVT system!" << std::endl;
        return 1;
    }
    std::cout << "✓ KVT system initialized" << std::endl;
    
    try {
        // Test Method 1: Serialized columns
        std::cout << "\n╔══════════════════════════════════════╗" << std::endl;
        std::cout << "║  METHOD 1: SERIALIZED COLUMNS       ║" << std::endl;
        std::cout << "╚══════════════════════════════════════╝" << std::endl;
        
        g_use_composite_key_method = false;
        test_basic_operations("Serialized Columns");
        test_transactions("Serialized Columns");
        test_batch_operations("Serialized Columns");
        test_edge_cases("Serialized Columns");
        
        // Test Method 2: Composite keys
        std::cout << "\n╔══════════════════════════════════════╗" << std::endl;
        std::cout << "║  METHOD 2: COMPOSITE KEYS           ║" << std::endl;
        std::cout << "╚══════════════════════════════════╝" << std::endl;
        
        g_use_composite_key_method = true;
        test_basic_operations("Composite Keys");
        test_transactions("Composite Keys");
        test_batch_operations("Composite Keys");
        test_edge_cases("Composite Keys");
        
        print_separator("All Tests Completed Successfully");
        
    } catch (const std::exception& e) {
        std::cerr << "Exception: " << e.what() << std::endl;
        kvt_shutdown();
        return 1;
    }
    
    // Cleanup
    kvt_shutdown();
    std::cout << "\n✓ KVT system shutdown" << std::endl;
    
    return 0;
}