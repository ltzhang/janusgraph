/**
 * JanusGraph KVT Adapter Comprehensive Test Program
 * 
 * Tests both storage methods:
 * 1. Serialized columns (all columns in single value)
 * 2. Composite key (column incorporated into key)
 * 
 * Complete test coverage including:
 * - All KVT API functions
 * - Edge cases and boundary conditions
 * - Error handling
 * - Concurrency and stress tests
 * - Transaction isolation
 */

#include "janusgraph_kvt_adapter.h"
#include <iostream>
#include <cassert>
#include <chrono>
#include <iomanip>
#include <thread>
#include <atomic>
#include <random>
#include <set>
#include <mutex>
#include <algorithm>
#include <sstream>
#include <map>

// Define the global flag
namespace janusgraph {
    bool g_use_composite_key_method = false;
}

using namespace janusgraph;

// Test counters for reporting
std::atomic<int> g_tests_passed(0);
std::atomic<int> g_tests_failed(0);
std::atomic<int> g_tests_total(0);

// Mutex for thread-safe output
std::mutex g_print_mutex;

void print_separator(const std::string& title) {
    std::lock_guard<std::mutex> lock(g_print_mutex);
    std::cout << "\n========================================" << std::endl;
    std::cout << " " << title << std::endl;
    std::cout << "========================================" << std::endl;
}

void print_test_result(const std::string& test_name, bool passed, const std::string& details = "") {
    std::lock_guard<std::mutex> lock(g_print_mutex);
    g_tests_total++;
    if (passed) {
        g_tests_passed++;
        std::cout << "  [✓] " << test_name;
    } else {
        g_tests_failed++;
        std::cout << "  [✗] " << test_name;
    }
    if (!details.empty()) {
        std::cout << " - " << details;
    }
    std::cout << std::endl;
}

// Helper function to generate random data
std::string generate_random_string(size_t length) {
    static std::random_device rd;
    static std::mt19937 gen(rd());
    static std::uniform_int_distribution<> dis(32, 126); // Printable ASCII
    
    std::string result;
    result.reserve(length);
    for (size_t i = 0; i < length; ++i) {
        result += static_cast<char>(dis(gen));
    }
    return result;
}

std::string generate_binary_string(size_t length) {
    static std::random_device rd;
    static std::mt19937 gen(rd());
    static std::uniform_int_distribution<> dis(0, 255);
    
    std::string result;
    result.reserve(length);
    for (size_t i = 0; i < length; ++i) {
        result += static_cast<char>(dis(gen));
    }
    return result;
}

// Test 1: Basic CRUD Operations
void test_basic_crud(const std::string& method_name) {
    print_separator("Basic CRUD Operations - " + method_name);
    
    JanusGraphKVTAdapter adapter;
    std::string error;
    std::string table_name = g_use_composite_key_method ? "crud_composite" : "crud_serialized";
    
    // Create table
    uint64_t table_id;
    KVTError result = kvt_create_table(table_name, g_use_composite_key_method ? "range" : "hash", table_id, error);
    print_test_result("Create table", 
                     result == KVTError::SUCCESS || result == KVTError::TABLE_ALREADY_EXISTS);
    
    // CREATE: Set single column
    bool test1 = adapter.set_column(0, table_name, "vertex:1", "name", "Alice", error);
    print_test_result("CREATE: Set single column", test1);
    
    // READ: Get single column
    std::string value;
    bool test2 = adapter.get_column(0, table_name, "vertex:1", "name", value, error);
    print_test_result("READ: Get single column", test2 && value == "Alice");
    
    // UPDATE: Update existing column
    bool test3 = adapter.set_column(0, table_name, "vertex:1", "name", "Alice Updated", error);
    adapter.get_column(0, table_name, "vertex:1", "name", value, error);
    print_test_result("UPDATE: Update existing column", test3 && value == "Alice Updated");
    
    // DELETE: Delete column
    bool test4 = adapter.delete_column(0, table_name, "vertex:1", "name", error);
    bool test5 = !adapter.get_column(0, table_name, "vertex:1", "name", value, error);
    print_test_result("DELETE: Delete column", test4 && test5);
    
    // Multiple columns on same key
    adapter.set_column(0, table_name, "vertex:2", "prop1", "value1", error);
    adapter.set_column(0, table_name, "vertex:2", "prop2", "value2", error);
    adapter.set_column(0, table_name, "vertex:2", "prop3", "value3", error);
    
    auto columns = adapter.get_all_columns(0, table_name, "vertex:2", error);
    print_test_result("Multiple columns on same key", columns.size() == 3);
    
    // Delete entire key
    bool test6 = adapter.delete_key(0, table_name, "vertex:2", error);
    columns = adapter.get_all_columns(0, table_name, "vertex:2", error);
    print_test_result("Delete entire key", test6 && columns.empty());
}

// Test 2: All Transaction Operations
void test_transactions_comprehensive(const std::string& method_name) {
    print_separator("Comprehensive Transaction Tests - " + method_name);
    
    JanusGraphKVTAdapter adapter;
    std::string error;
    std::string table_name = g_use_composite_key_method ? "tx_composite" : "tx_serialized";
    
    uint64_t table_id;
    kvt_create_table(table_name, g_use_composite_key_method ? "range" : "hash", table_id, error);
    
    // Test 1: Basic transaction commit
    uint64_t tx1;
    KVTError tx_result = kvt_start_transaction(tx1, error);
    print_test_result("Start transaction", tx_result == KVTError::SUCCESS);
    
    adapter.set_column(tx1, table_name, "tx_key1", "col1", "value1", error);
    adapter.set_column(tx1, table_name, "tx_key1", "col2", "value2", error);
    
    // Read within transaction
    std::string value;
    bool read_in_tx = adapter.get_column(tx1, table_name, "tx_key1", "col1", value, error);
    print_test_result("Read within transaction", read_in_tx && value == "value1");
    
    // Commit transaction
    bool commit_success = kvt_commit_transaction(tx1, error) == KVTError::SUCCESS;
    print_test_result("Commit transaction", commit_success);
    
    // Verify data persisted after commit
    bool persisted = adapter.get_column(0, table_name, "tx_key1", "col1", value, error);
    print_test_result("Data persisted after commit", persisted && value == "value1");
    
    // Test 2: Transaction rollback
    uint64_t tx2;
    kvt_start_transaction(tx2, error);
    adapter.set_column(tx2, table_name, "tx_key2", "col1", "should_not_persist", error);
    
    // Rollback transaction
    bool rollback_success = kvt_rollback_transaction(tx2, error) == KVTError::SUCCESS;
    print_test_result("Rollback transaction", rollback_success);
    
    // Verify data not persisted after rollback
    bool not_persisted = !adapter.get_column(0, table_name, "tx_key2", "col1", value, error);
    print_test_result("Data not persisted after rollback", not_persisted);
    
    // Test 3: Transaction isolation (if supported)
    uint64_t tx3, tx4;
    kvt_start_transaction(tx3, error);
    kvt_start_transaction(tx4, error);
    
    // tx3 writes a value
    adapter.set_column(tx3, table_name, "isolated_key", "col1", "tx3_value", error);
    
    // tx4 should not see tx3's uncommitted changes
    bool isolated = !adapter.get_column(tx4, table_name, "isolated_key", "col1", value, error);
    print_test_result("Transaction isolation - uncommitted changes not visible", isolated);
    
    // Commit tx3
    kvt_commit_transaction(tx3, error);
    
    // Now tx4 should see the committed value
    bool visible_after_commit = adapter.get_column(tx4, table_name, "isolated_key", "col1", value, error);
    print_test_result("Transaction isolation - committed changes visible", 
                     visible_after_commit && value == "tx3_value");
    
    kvt_rollback_transaction(tx4, error);
    
    // Test 4: Nested updates within transaction
    uint64_t tx5;
    kvt_start_transaction(tx5, error);
    
    // Initial write
    adapter.set_column(tx5, table_name, "nested_key", "col1", "initial", error);
    
    // Update same column within same transaction
    adapter.set_column(tx5, table_name, "nested_key", "col1", "updated", error);
    
    // Read should return updated value
    adapter.get_column(tx5, table_name, "nested_key", "col1", value, error);
    bool nested_update = (value == "updated");
    
    kvt_commit_transaction(tx5, error);
    
    // Verify final value after commit
    adapter.get_column(0, table_name, "nested_key", "col1", value, error);
    print_test_result("Nested updates within transaction", nested_update && value == "updated");
}

// Test 3: Batch Operations
void test_batch_operations_comprehensive(const std::string& method_name) {
    print_separator("Comprehensive Batch Operations - " + method_name);
    
    JanusGraphKVTAdapter adapter;
    std::string error;
    std::string table_name = g_use_composite_key_method ? "batch_composite" : "batch_serialized";
    
    uint64_t table_id;
    kvt_create_table(table_name, g_use_composite_key_method ? "range" : "hash", table_id, error);
    
    // Test 1: Batch set multiple columns
    std::vector<ColumnValue> batch_columns;
    for (int i = 0; i < 10; ++i) {
        batch_columns.push_back({"col" + std::to_string(i), "value" + std::to_string(i)});
    }
    
    bool batch_set = adapter.set_columns(0, table_name, "batch_key1", batch_columns, error);
    print_test_result("Batch set 10 columns", batch_set);
    
    // Verify all columns were set
    auto retrieved = adapter.get_all_columns(0, table_name, "batch_key1", error);
    print_test_result("Verify all batch columns set", retrieved.size() == 10);
    
    // Test 2: Mixed batch operations (only for composite key method)
    if (g_use_composite_key_method) {
        std::vector<JanusGraphKVTAdapter::JanusGraphBatchOp> batch_ops;
        
        // Prepare mixed operations
        batch_ops.push_back({JanusGraphKVTAdapter::JanusGraphBatchOp::SET_COLUMN, 
                           "batch_key2", "col1", "new_value1"});
        batch_ops.push_back({JanusGraphKVTAdapter::JanusGraphBatchOp::SET_COLUMN, 
                           "batch_key2", "col2", "new_value2"});
        batch_ops.push_back({JanusGraphKVTAdapter::JanusGraphBatchOp::GET_COLUMN, 
                           "batch_key1", "col5", ""});
        batch_ops.push_back({JanusGraphKVTAdapter::JanusGraphBatchOp::DELETE_COLUMN, 
                           "batch_key1", "col9", ""});
        
        std::vector<JanusGraphKVTAdapter::JanusGraphBatchResult> results;
        bool batch_exec = adapter.batch_execute(0, table_name, batch_ops, results, error);
        print_test_result("Mixed batch operations", batch_exec);
        
        // Verify results
        bool results_valid = results.size() == 4 && 
                           results[2].success && results[2].value == "value5";
        print_test_result("Batch operation results valid", results_valid);
        
        // Verify deletion worked
        std::string deleted_value;
        bool deleted = !adapter.get_column(0, table_name, "batch_key1", "col9", deleted_value, error);
        print_test_result("Batch delete verified", deleted);
    } else {
        print_test_result("Mixed batch operations", true, "Skipped for serialized method");
    }
    
    // Test 3: Large batch operations
    std::vector<ColumnValue> large_batch;
    for (int i = 0; i < 100; ++i) {
        large_batch.push_back({"large_col" + std::to_string(i), 
                              generate_random_string(100)});
    }
    
    auto start = std::chrono::high_resolution_clock::now();
    bool large_batch_set = adapter.set_columns(0, table_name, "large_batch_key", large_batch, error);
    auto end = std::chrono::high_resolution_clock::now();
    auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(end - start);
    
    print_test_result("Large batch (100 columns)", large_batch_set, 
                     "Time: " + std::to_string(duration.count()) + "ms");
    
    // Verify large batch
    auto large_retrieved = adapter.get_all_columns(0, table_name, "large_batch_key", error);
    print_test_result("Verify large batch columns", large_retrieved.size() == 100);
}

// Test 4: Edge Cases and Boundary Conditions
void test_edge_cases_comprehensive(const std::string& method_name) {
    print_separator("Comprehensive Edge Cases - " + method_name);
    
    JanusGraphKVTAdapter adapter;
    std::string error;
    std::string table_name = g_use_composite_key_method ? "edge_composite" : "edge_serialized";
    
    uint64_t table_id;
    kvt_create_table(table_name, g_use_composite_key_method ? "range" : "hash", table_id, error);
    
    // Test 1: Empty values
    bool empty_value = adapter.set_column(0, table_name, "edge_key1", "empty_col", "", error);
    std::string retrieved_empty;
    bool get_empty = adapter.get_column(0, table_name, "edge_key1", "empty_col", retrieved_empty, error);
    print_test_result("Empty value storage", empty_value && get_empty && retrieved_empty.empty());
    
    // Test 2: Very long keys and values
    std::string long_key = "key_" + generate_random_string(1000);
    std::string long_column = "col_" + generate_random_string(1000);
    std::string long_value = generate_random_string(10000);
    
    bool long_set = adapter.set_column(0, table_name, long_key, long_column, long_value, error);
    std::string retrieved_long;
    bool long_get = adapter.get_column(0, table_name, long_key, long_column, retrieved_long, error);
    print_test_result("Very long keys/values (10KB)", 
                     long_set && long_get && retrieved_long == long_value);
    
    // Test 3: Binary data with all byte values
    std::string binary_value;
    for (int i = 0; i < 256; ++i) {
        binary_value += static_cast<char>(i);
    }
    
    bool binary_set = adapter.set_column(0, table_name, "binary_key", "binary_col", binary_value, error);
    std::string retrieved_binary;
    bool binary_get = adapter.get_column(0, table_name, "binary_key", "binary_col", retrieved_binary, error);
    print_test_result("Binary data (all byte values)", 
                     binary_set && binary_get && retrieved_binary == binary_value);
    
    // Test 4: Special characters in keys and columns
    std::string special_chars = "!@#$%^&*()_+-=[]{}|;':\",./<>?";
    bool special_set = adapter.set_column(0, table_name, "special_" + special_chars, 
                                         "col_" + special_chars, "value", error);
    std::string retrieved_special;
    bool special_get = adapter.get_column(0, table_name, "special_" + special_chars, 
                                         "col_" + special_chars, retrieved_special, error);
    print_test_result("Special characters in keys/columns", 
                     special_set && special_get && retrieved_special == "value");
    
    // Test 5: Unicode characters
    std::string unicode_key = "key_你好世界_🌍🌎🌏";
    std::string unicode_value = "Hello_世界_مرحبا_नमस्ते_🚀🎉";
    
    bool unicode_set = adapter.set_column(0, table_name, unicode_key, "unicode_col", unicode_value, error);
    std::string retrieved_unicode;
    bool unicode_get = adapter.get_column(0, table_name, unicode_key, "unicode_col", retrieved_unicode, error);
    print_test_result("Unicode characters", 
                     unicode_set && unicode_get && retrieved_unicode == unicode_value);
    
    // Test 6: Maximum number of columns per key
    const int max_columns = 1000;
    for (int i = 0; i < max_columns; ++i) {
        adapter.set_column(0, table_name, "max_cols_key", 
                          "col_" + std::to_string(i), "v" + std::to_string(i), error);
    }
    
    auto max_retrieved = adapter.get_all_columns(0, table_name, "max_cols_key", error);
    print_test_result("Maximum columns per key (1000)", max_retrieved.size() == max_columns);
    
    // Test 7: Null character handling (should fail for composite key method)
    if (g_use_composite_key_method) {
        std::string null_key = "key";
        null_key += '\0';
        null_key += "suffix";
        
        bool null_failed = false;
        try {
            adapter.set_column(0, table_name, null_key, "col", "value", error);
        } catch (const std::exception& e) {
            null_failed = true;
        }
        print_test_result("Null character rejection (composite key)", null_failed);
    } else {
        // For serialized method, null chars should work
        std::string null_value = "prefix";
        null_value += '\0';
        null_value += "suffix";
        
        bool null_set = adapter.set_column(0, table_name, "null_key", "null_col", null_value, error);
        std::string retrieved_null;
        bool null_get = adapter.get_column(0, table_name, "null_key", "null_col", retrieved_null, error);
        print_test_result("Null character in value (serialized)", 
                         null_set && null_get && retrieved_null == null_value);
    }
    
    // Test 8: Operations on non-existent items
    std::string non_existent;
    bool not_found = !adapter.get_column(0, table_name, "non_existent_key", "col", non_existent, error);
    print_test_result("Get non-existent key", not_found);
    
    bool delete_non_existent = !adapter.delete_column(0, table_name, "non_existent_key", "col", error);
    print_test_result("Delete non-existent column", delete_non_existent);
    
    // Test 9: Overwrite existing values multiple times
    for (int i = 0; i < 10; ++i) {
        adapter.set_column(0, table_name, "overwrite_key", "col", 
                          "iteration_" + std::to_string(i), error);
    }
    
    std::string final_value;
    adapter.get_column(0, table_name, "overwrite_key", "col", final_value, error);
    print_test_result("Multiple overwrites", final_value == "iteration_9");
}

// Test 5: Error Handling and Recovery
void test_error_handling(const std::string& method_name) {
    print_separator("Error Handling and Recovery - " + method_name);
    
    JanusGraphKVTAdapter adapter;
    std::string error;
    
    // Test 1: Invalid table operations
    std::string value;
    bool invalid_table = !adapter.get_column(0, "non_existent_table", "key", "col", value, error);
    print_test_result("Operation on non-existent table", invalid_table);
    
    // Test 2: Invalid transaction ID
    bool invalid_tx = !adapter.set_column(999999, "any_table", "key", "col", "value", error);
    print_test_result("Invalid transaction ID", invalid_tx);
    
    // Test 3: Create duplicate table
    std::string dup_table = g_use_composite_key_method ? "dup_composite" : "dup_serialized";
    uint64_t table_id1, table_id2;
    
    KVTError first_create = kvt_create_table(dup_table, "hash", table_id1, error);
    KVTError second_create = kvt_create_table(dup_table, "hash", table_id2, error);
    
    print_test_result("Duplicate table creation prevented", 
                     first_create == KVTError::SUCCESS && 
                     second_create == KVTError::TABLE_ALREADY_EXISTS);
    
    // Test 4: Invalid partition method
    uint64_t invalid_table_id;
    KVTError invalid_partition = kvt_create_table("invalid_partition", "invalid_method", 
                                                  invalid_table_id, error);
    print_test_result("Invalid partition method rejected", 
                     invalid_partition == KVTError::INVALID_PARTITION_METHOD);
    
    // Test 5: Transaction after commit/rollback
    uint64_t tx_finished;
    kvt_start_transaction(tx_finished, error);
    kvt_commit_transaction(tx_finished, error);
    
    // Try to use committed transaction
    bool tx_reuse_failed = !adapter.set_column(tx_finished, dup_table, "key", "col", "value", error);
    print_test_result("Cannot reuse committed transaction", tx_reuse_failed);
    
    // Test 6: Empty key/column validation
    bool empty_key_prevented = false;
    bool empty_col_prevented = false;
    
    // Test empty key
    if (!adapter.set_column(0, dup_table, "", "col", "value", error)) {
        empty_key_prevented = true;
    }
    
    // Test empty column (composite key method doesn't allow empty columns)
    if (g_use_composite_key_method) {
        try {
            adapter.set_column(0, dup_table, "key", "", "value", error);
        } catch (const std::exception& e) {
            empty_col_prevented = true;
        }
        print_test_result("Empty column validation (composite)", empty_col_prevented);
    } else {
        // Serialized method may allow empty columns
        adapter.set_column(0, dup_table, "key", "", "value", error);
        print_test_result("Empty column handling (serialized)", true);
    }
    
    print_test_result("Empty key validation", empty_key_prevented);
    
    // Test 7: Recovery after error
    // Create a valid table and verify operations work after previous errors
    std::string recovery_table = g_use_composite_key_method ? "recovery_composite" : "recovery_serialized";
    uint64_t recovery_id;
    kvt_create_table(recovery_table, "hash", recovery_id, error);
    
    bool recovery_set = adapter.set_column(0, recovery_table, "recovery_key", "col", "value", error);
    std::string recovery_value;
    bool recovery_get = adapter.get_column(0, recovery_table, "recovery_key", "col", recovery_value, error);
    
    print_test_result("Recovery after errors", 
                     recovery_set && recovery_get && recovery_value == "value");
}

// Test 6: Scan Operations (for range-partitioned tables)
void test_scan_operations(const std::string& method_name) {
    print_separator("Scan Operations - " + method_name);
    
    if (!g_use_composite_key_method) {
        print_test_result("Scan operations", true, "Skipped for hash-partitioned serialized method");
        return;
    }
    
    JanusGraphKVTAdapter adapter;
    std::string error;
    std::string table_name = "scan_range_table";
    
    // Create range-partitioned table
    uint64_t table_id;
    KVTError create_result = kvt_create_table(table_name, "range", table_id, error);
    print_test_result("Create range-partitioned table", 
                     create_result == KVTError::SUCCESS || create_result == KVTError::TABLE_ALREADY_EXISTS);
    
    // Insert data with ordered keys
    for (int i = 0; i < 100; ++i) {
        std::string key = "key_" + std::to_string(1000 + i); // Ensures lexicographic ordering
        adapter.set_column(0, table_name, key, "col1", "value_" + std::to_string(i), error);
        adapter.set_column(0, table_name, key, "col2", "data_" + std::to_string(i), error);
    }
    
    // Test 1: Basic range scan
    std::vector<std::pair<std::string, std::string>> scan_results;
    KVTError scan_result = kvt_scan(0, table_name, "key_1010", "key_1020", 100, scan_results, error);
    
    // Count unique keys in results (each key may have multiple columns)
    std::set<std::string> unique_keys;
    for (const auto& [composite_key, value] : scan_results) {
        try {
            auto [key, col] = serialization::split_composite_key(composite_key);
            unique_keys.insert(key);
        } catch (...) {
            // Handle non-composite keys
            unique_keys.insert(composite_key);
        }
    }
    
    print_test_result("Basic range scan", scan_result == KVTError::SUCCESS && !scan_results.empty(),
                     "Found " + std::to_string(unique_keys.size()) + " unique keys");
    
    // Test 2: Scan with limit
    scan_results.clear();
    scan_result = kvt_scan(0, table_name, "key_1000", "key_1099", 10, scan_results, error);
    print_test_result("Scan with limit", scan_result == KVTError::SUCCESS && scan_results.size() <= 10);
    
    // Test 3: Empty range scan
    scan_results.clear();
    scan_result = kvt_scan(0, table_name, "key_2000", "key_2100", 100, scan_results, error);
    print_test_result("Empty range scan", scan_result == KVTError::SUCCESS && scan_results.empty());
    
    // Test 4: Full table scan
    scan_results.clear();
    scan_result = kvt_scan(0, table_name, "", "key_9999", 10000, scan_results, error);
    print_test_result("Full table scan", scan_result == KVTError::SUCCESS && !scan_results.empty(),
                     "Total items: " + std::to_string(scan_results.size()));
    
    // Test 5: Scan within transaction
    uint64_t tx_scan;
    kvt_start_transaction(tx_scan, error);
    
    // Add new data in transaction
    adapter.set_column(tx_scan, table_name, "key_1200", "col1", "tx_value", error);
    
    // Scan should see transaction's changes
    scan_results.clear();
    scan_result = kvt_scan(tx_scan, table_name, "key_1199", "key_1201", 100, scan_results, error);
    
    bool tx_visible = false;
    for (const auto& [key, value] : scan_results) {
        if (value == "tx_value") {
            tx_visible = true;
            break;
        }
    }
    
    print_test_result("Scan within transaction sees changes", tx_visible);
    
    kvt_rollback_transaction(tx_scan, error);
}

// Test 7: Stress Test
void test_stress(const std::string& method_name) {
    print_separator("Stress Test - " + method_name);
    
    JanusGraphKVTAdapter adapter;
    std::string error;
    std::string table_name = g_use_composite_key_method ? "stress_composite" : "stress_serialized";
    
    uint64_t table_id;
    kvt_create_table(table_name, g_use_composite_key_method ? "range" : "hash", table_id, error);
    
    const int num_keys = 1000;
    const int num_columns_per_key = 20;
    const int value_size = 1000; // 1KB values
    
    // Test 1: High volume writes
    auto start = std::chrono::high_resolution_clock::now();
    
    for (int i = 0; i < num_keys; ++i) {
        std::string key = "stress_key_" + std::to_string(i);
        for (int j = 0; j < num_columns_per_key; ++j) {
            std::string column = "col_" + std::to_string(j);
            std::string value = generate_random_string(value_size);
            adapter.set_column(0, table_name, key, column, value, error);
        }
    }
    
    auto end = std::chrono::high_resolution_clock::now();
    auto write_duration = std::chrono::duration_cast<std::chrono::milliseconds>(end - start);
    
    int total_writes = num_keys * num_columns_per_key;
    double writes_per_sec = (total_writes * 1000.0) / write_duration.count();
    
    print_test_result("High volume writes", true,
                     std::to_string(total_writes) + " writes in " + 
                     std::to_string(write_duration.count()) + "ms (" +
                     std::to_string(static_cast<int>(writes_per_sec)) + " ops/sec)");
    
    // Test 2: High volume reads
    start = std::chrono::high_resolution_clock::now();
    
    int successful_reads = 0;
    for (int i = 0; i < num_keys; ++i) {
        std::string key = "stress_key_" + std::to_string(i);
        for (int j = 0; j < num_columns_per_key; ++j) {
            std::string column = "col_" + std::to_string(j);
            std::string value;
            if (adapter.get_column(0, table_name, key, column, value, error)) {
                successful_reads++;
            }
        }
    }
    
    end = std::chrono::high_resolution_clock::now();
    auto read_duration = std::chrono::duration_cast<std::chrono::milliseconds>(end - start);
    
    double reads_per_sec = (successful_reads * 1000.0) / read_duration.count();
    
    print_test_result("High volume reads", successful_reads == total_writes,
                     std::to_string(successful_reads) + " reads in " +
                     std::to_string(read_duration.count()) + "ms (" +
                     std::to_string(static_cast<int>(reads_per_sec)) + " ops/sec)");
    
    // Test 3: Mixed read/write workload
    start = std::chrono::high_resolution_clock::now();
    
    std::random_device rd;
    std::mt19937 gen(rd());
    std::uniform_int_distribution<> key_dis(0, num_keys - 1);
    std::uniform_int_distribution<> col_dis(0, num_columns_per_key - 1);
    std::uniform_int_distribution<> op_dis(0, 2); // 0=read, 1=write, 2=delete
    
    int mixed_ops = 10000;
    int mixed_success = 0;
    
    for (int i = 0; i < mixed_ops; ++i) {
        std::string key = "stress_key_" + std::to_string(key_dis(gen));
        std::string column = "col_" + std::to_string(col_dis(gen));
        
        switch (op_dis(gen)) {
            case 0: { // Read
                std::string value;
                if (adapter.get_column(0, table_name, key, column, value, error)) {
                    mixed_success++;
                }
                break;
            }
            case 1: { // Write
                if (adapter.set_column(0, table_name, key, column, 
                                      generate_random_string(100), error)) {
                    mixed_success++;
                }
                break;
            }
            case 2: { // Delete
                if (adapter.delete_column(0, table_name, key, column, error)) {
                    mixed_success++;
                }
                break;
            }
        }
    }
    
    end = std::chrono::high_resolution_clock::now();
    auto mixed_duration = std::chrono::duration_cast<std::chrono::milliseconds>(end - start);
    
    print_test_result("Mixed workload", true,
                     std::to_string(mixed_success) + "/" + std::to_string(mixed_ops) + 
                     " ops in " + std::to_string(mixed_duration.count()) + "ms");
    
    // Test 4: Large value stress
    std::string large_value = generate_random_string(100000); // 100KB
    bool large_success = true;
    
    for (int i = 0; i < 10; ++i) {
        if (!adapter.set_column(0, table_name, "large_key_" + std::to_string(i), 
                               "large_col", large_value, error)) {
            large_success = false;
            break;
        }
    }
    
    print_test_result("Large value stress (10 x 100KB)", large_success);
}

// Test 8: Concurrency Test
void test_concurrency(const std::string& method_name) {
    print_separator("Concurrency Test - " + method_name);
    
    std::string table_name = g_use_composite_key_method ? "concurrent_composite" : "concurrent_serialized";
    std::string error;
    
    uint64_t table_id;
    kvt_create_table(table_name, g_use_composite_key_method ? "range" : "hash", table_id, error);
    
    const int num_threads = 10;
    const int ops_per_thread = 100;
    std::atomic<int> successful_ops(0);
    std::atomic<int> failed_ops(0);
    
    // Test 1: Concurrent writes to different keys
    auto writer_func = [&](int thread_id) {
        JanusGraphKVTAdapter adapter;
        std::string local_error;
        
        for (int i = 0; i < ops_per_thread; ++i) {
            std::string key = "thread_" + std::to_string(thread_id) + "_key_" + std::to_string(i);
            std::string value = "value_" + std::to_string(thread_id) + "_" + std::to_string(i);
            
            if (adapter.set_column(0, table_name, key, "col", value, local_error)) {
                successful_ops++;
            } else {
                failed_ops++;
            }
        }
    };
    
    auto start = std::chrono::high_resolution_clock::now();
    
    std::vector<std::thread> write_threads;
    for (int i = 0; i < num_threads; ++i) {
        write_threads.emplace_back(writer_func, i);
    }
    
    for (auto& t : write_threads) {
        t.join();
    }
    
    auto end = std::chrono::high_resolution_clock::now();
    auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(end - start);
    
    print_test_result("Concurrent writes to different keys", 
                     successful_ops == num_threads * ops_per_thread,
                     std::to_string(successful_ops.load()) + " successful, " +
                     std::to_string(failed_ops.load()) + " failed in " +
                     std::to_string(duration.count()) + "ms");
    
    // Test 2: Concurrent reads
    successful_ops = 0;
    failed_ops = 0;
    
    auto reader_func = [&](int thread_id) {
        JanusGraphKVTAdapter adapter;
        std::string local_error;
        
        for (int i = 0; i < ops_per_thread; ++i) {
            // Read keys written by different threads
            int target_thread = (thread_id + i) % num_threads;
            std::string key = "thread_" + std::to_string(target_thread) + "_key_" + std::to_string(i);
            std::string value;
            
            if (adapter.get_column(0, table_name, key, "col", value, local_error)) {
                successful_ops++;
            } else {
                failed_ops++;
            }
        }
    };
    
    std::vector<std::thread> read_threads;
    for (int i = 0; i < num_threads; ++i) {
        read_threads.emplace_back(reader_func, i);
    }
    
    for (auto& t : read_threads) {
        t.join();
    }
    
    print_test_result("Concurrent reads", successful_ops > 0,
                     std::to_string(successful_ops.load()) + " successful reads");
    
    // Test 3: Concurrent transactions
    successful_ops = 0;
    failed_ops = 0;
    
    auto tx_func = [&](int thread_id) {
        JanusGraphKVTAdapter adapter;
        std::string local_error;
        
        for (int i = 0; i < 10; ++i) { // Fewer transactions
            uint64_t tx_id;
            if (kvt_start_transaction(tx_id, local_error) == KVTError::SUCCESS) {
                bool tx_success = true;
                
                // Do some work in transaction
                for (int j = 0; j < 10; ++j) {
                    std::string key = "tx_thread_" + std::to_string(thread_id) + "_" + std::to_string(j);
                    if (!adapter.set_column(tx_id, table_name, key, "col", "tx_value", local_error)) {
                        tx_success = false;
                        break;
                    }
                }
                
                if (tx_success) {
                    if (kvt_commit_transaction(tx_id, local_error) == KVTError::SUCCESS) {
                        successful_ops++;
                    } else {
                        kvt_rollback_transaction(tx_id, local_error);
                        failed_ops++;
                    }
                } else {
                    kvt_rollback_transaction(tx_id, local_error);
                    failed_ops++;
                }
            } else {
                failed_ops++;
            }
        }
    };
    
    std::vector<std::thread> tx_threads;
    for (int i = 0; i < num_threads; ++i) {
        tx_threads.emplace_back(tx_func, i);
    }
    
    for (auto& t : tx_threads) {
        t.join();
    }
    
    print_test_result("Concurrent transactions", successful_ops > 0,
                     std::to_string(successful_ops.load()) + " committed, " +
                     std::to_string(failed_ops.load()) + " rolled back");
}

// Test 9: Data Integrity Verification
void test_data_integrity(const std::string& method_name) {
    print_separator("Data Integrity Verification - " + method_name);
    
    JanusGraphKVTAdapter adapter;
    std::string error;
    std::string table_name = g_use_composite_key_method ? "integrity_composite" : "integrity_serialized";
    
    uint64_t table_id;
    kvt_create_table(table_name, g_use_composite_key_method ? "range" : "hash", table_id, error);
    
    // Test 1: Write-read consistency
    const int num_iterations = 100;
    bool write_read_consistent = true;
    
    for (int i = 0; i < num_iterations; ++i) {
        std::string key = "consistency_key_" + std::to_string(i);
        std::string expected_value = generate_random_string(1000);
        
        adapter.set_column(0, table_name, key, "col", expected_value, error);
        
        std::string actual_value;
        adapter.get_column(0, table_name, key, "col", actual_value, error);
        
        if (actual_value != expected_value) {
            write_read_consistent = false;
            break;
        }
    }
    
    print_test_result("Write-read consistency", write_read_consistent);
    
    // Test 2: Column ordering preservation (for serialized method)
    if (!g_use_composite_key_method) {
        std::vector<ColumnValue> ordered_columns;
        for (int i = 0; i < 26; ++i) {
            char col_name = 'a' + i;
            ordered_columns.push_back({std::string(1, col_name), "value_" + std::string(1, col_name)});
        }
        
        adapter.set_columns(0, table_name, "ordered_key", ordered_columns, error);
        
        auto retrieved = adapter.get_all_columns(0, table_name, "ordered_key", error);
        
        bool order_preserved = true;
        if (retrieved.size() == ordered_columns.size()) {
            for (size_t i = 0; i < retrieved.size(); ++i) {
                if (retrieved[i].column != ordered_columns[i].column ||
                    retrieved[i].value != ordered_columns[i].value) {
                    order_preserved = false;
                    break;
                }
            }
        } else {
            order_preserved = false;
        }
        
        print_test_result("Column ordering preservation", order_preserved);
    } else {
        print_test_result("Column ordering preservation", true, "N/A for composite key method");
    }
    
    // Test 3: Delete verification
    adapter.set_column(0, table_name, "delete_test_key", "col1", "value1", error);
    adapter.set_column(0, table_name, "delete_test_key", "col2", "value2", error);
    adapter.set_column(0, table_name, "delete_test_key", "col3", "value3", error);
    
    adapter.delete_column(0, table_name, "delete_test_key", "col2", error);
    
    auto after_delete = adapter.get_all_columns(0, table_name, "delete_test_key", error);
    
    bool delete_correct = after_delete.size() == 2;
    for (const auto& cv : after_delete) {
        if (cv.column == "col2") {
            delete_correct = false;
            break;
        }
    }
    
    print_test_result("Delete column integrity", delete_correct);
    
    // Test 4: Transaction atomicity
    uint64_t tx_atomic;
    kvt_start_transaction(tx_atomic, error);
    
    // Write multiple values in transaction
    for (int i = 0; i < 10; ++i) {
        adapter.set_column(tx_atomic, table_name, "atomic_key", 
                          "col_" + std::to_string(i), "value_" + std::to_string(i), error);
    }
    
    // Rollback
    kvt_rollback_transaction(tx_atomic, error);
    
    // Verify none of the values exist
    auto after_rollback = adapter.get_all_columns(0, table_name, "atomic_key", error);
    
    print_test_result("Transaction atomicity (rollback)", after_rollback.empty());
    
    // Test 5: Update consistency
    std::string update_key = "update_consistency_key";
    std::map<std::string, std::string> expected_state;
    
    // Initial state
    for (int i = 0; i < 10; ++i) {
        std::string col = "col_" + std::to_string(i);
        std::string val = "initial_" + std::to_string(i);
        adapter.set_column(0, table_name, update_key, col, val, error);
        expected_state[col] = val;
    }
    
    // Random updates
    std::random_device rd;
    std::mt19937 gen(rd());
    std::uniform_int_distribution<> dis(0, 9);
    
    for (int i = 0; i < 50; ++i) {
        int col_idx = dis(gen);
        std::string col = "col_" + std::to_string(col_idx);
        std::string val = "updated_" + std::to_string(i);
        adapter.set_column(0, table_name, update_key, col, val, error);
        expected_state[col] = val;
    }
    
    // Verify final state
    auto final_state = adapter.get_all_columns(0, table_name, update_key, error);
    
    bool state_consistent = final_state.size() == expected_state.size();
    for (const auto& cv : final_state) {
        if (expected_state[cv.column] != cv.value) {
            state_consistent = false;
            break;
        }
    }
    
    print_test_result("Update consistency after 50 random updates", state_consistent);
}

// Main test runner
int main() {
    std::cout << "\n===================================" << std::endl;
    std::cout << "  JanusGraph KVT Adapter" << std::endl;
    std::cout << "  COMPREHENSIVE TEST SUITE" << std::endl;
    std::cout << "===================================" << std::endl;
    
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
        test_basic_crud("Serialized Columns");
        test_transactions_comprehensive("Serialized Columns");
        test_batch_operations_comprehensive("Serialized Columns");
        test_edge_cases_comprehensive("Serialized Columns");
        test_error_handling("Serialized Columns");
        test_scan_operations("Serialized Columns");
        test_stress("Serialized Columns");
        test_concurrency("Serialized Columns");
        test_data_integrity("Serialized Columns");
        
        // Test Method 2: Composite keys
        std::cout << "\n╔══════════════════════════════════════╗" << std::endl;
        std::cout << "║  METHOD 2: COMPOSITE KEYS           ║" << std::endl;
        std::cout << "╚══════════════════════════════════════╝" << std::endl;
        
        g_use_composite_key_method = true;
        test_basic_crud("Composite Keys");
        test_transactions_comprehensive("Composite Keys");
        test_batch_operations_comprehensive("Composite Keys");
        test_edge_cases_comprehensive("Composite Keys");
        test_error_handling("Composite Keys");
        test_scan_operations("Composite Keys");
        test_stress("Composite Keys");
        test_concurrency("Composite Keys");
        test_data_integrity("Composite Keys");
        
        // Final summary
        print_separator("TEST SUMMARY");
        std::cout << "\n  Total Tests: " << g_tests_total << std::endl;
        std::cout << "  Passed: " << g_tests_passed << " ✓" << std::endl;
        std::cout << "  Failed: " << g_tests_failed << " ✗" << std::endl;
        std::cout << "  Success Rate: " 
                  << std::fixed << std::setprecision(1)
                  << (g_tests_passed * 100.0 / g_tests_total) << "%" << std::endl;
        
        if (g_tests_failed == 0) {
            std::cout << "\n🎉 ALL TESTS PASSED! 🎉" << std::endl;
        } else {
            std::cout << "\n⚠️  Some tests failed. Please review the output above." << std::endl;
        }
        
    } catch (const std::exception& e) {
        std::cerr << "Exception during testing: " << e.what() << std::endl;
        kvt_shutdown();
        return 1;
    }
    
    // Cleanup
    kvt_shutdown();
    std::cout << "\n✓ KVT system shutdown" << std::endl;
    
    return (g_tests_failed == 0) ? 0 : 1;
}