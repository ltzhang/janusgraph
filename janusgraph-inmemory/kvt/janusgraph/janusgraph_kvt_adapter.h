#ifndef JANUSGRAPH_KVT_ADAPTER_H
#define JANUSGRAPH_KVT_ADAPTER_H

#include "kvt_inc.h"
#include <string>
#include <vector>
#include <map>
#include <sstream>
#include <cstring>
#include <algorithm>
#include <cstdint>

/**
 * JanusGraph KVT Adapter
 * 
 * This adapter provides two methods for storing key-column-value data:
 * 1. Serialized columns: All columns for a key are serialized into a single value
 * 2. Composite key: Column is incorporated into the key with a separator
 */

namespace janusgraph {

// Global flag to switch between storage methods
// true = use composite key method (column in key)
// false = use serialized columns method (all columns in value)
extern bool g_use_composite_key_method;

// Separator for composite keys (using null character)
const char KEY_COLUMN_SEPARATOR = '\0';

#define CHECK(condition, message) \
    if (!(condition)) { \
        throw std::runtime_error(message); \
    }

// Column-value pair
struct ColumnValue {
    std::string column;
    std::string value;
    
    ColumnValue() = default;
    ColumnValue(const std::string& c, const std::string& v) : column(c), value(v) {}
    ColumnValue(const std::string&& c, const std::string&& v) : column(std::move(c)), value(std::move(v)) {}
    ColumnValue(ColumnValue&& other) noexcept : column(std::move(other.column)), value(std::move(other.value)) {}
    bool operator < (const ColumnValue& other) const {
        return column < other.column;
    }
    bool operator == (const ColumnValue& other) const {
        return column == other.column;
    }
};

// Utility functions for serialization
namespace serialization {
    
    // Serialize a vector of column-value pairs into a single string
    inline std::string serialize_columns(const std::vector<ColumnValue>& columns) {
        std::stringstream ss;
        
        // Write number of columns (4 bytes)
        uint32_t num_columns = columns.size();
        CHECK(num_columns > 0, "Number of columns must be greater than 0");
        ss.write(reinterpret_cast<const char*>(&num_columns), sizeof(num_columns));
        CHECK(std::is_sorted(columns.begin(), columns.end()), "Columns Must Be sorted before serialization");
        // Write each column-value pair
        for (const auto& cv : columns) {
            // Write column length and data
            uint32_t col_len = cv.column.size();
            ss.write(reinterpret_cast<const char*>(&col_len), sizeof(col_len));
            ss.write(cv.column.data(), col_len);
            
            // Write value length and data
            uint32_t val_len = cv.value.size();
            ss.write(reinterpret_cast<const char*>(&val_len), sizeof(val_len));
            ss.write(cv.value.data(), val_len);
        }
        
        return ss.str();
    }
    
    // Deserialize a string back into column-value pairs
    inline std::vector<ColumnValue> deserialize_columns(const std::string& data) {
        std::vector<ColumnValue> result;
        
        CHECK(!data.empty(), "Data is empty");
        
        const char* ptr = data.data();
        const char* end = ptr + data.size();
        
        // Read number of columns
        if (ptr + sizeof(uint32_t) > end) return result;
        uint32_t num_columns;
        std::memcpy(&num_columns, ptr, sizeof(num_columns));
        ptr += sizeof(num_columns);
        
        // Read each column-value pair
        for (uint32_t i = 0; i < num_columns; ++i) {
            // Read column
            if (ptr + sizeof(uint32_t) > end) break;
            uint32_t col_len;
            std::memcpy(&col_len, ptr, sizeof(col_len));
            ptr += sizeof(col_len);
            
            if (ptr + col_len > end) break;
            std::string column(ptr, col_len);
            ptr += col_len;
            
            // Read value
            if (ptr + sizeof(uint32_t) > end) break;
            uint32_t val_len;
            std::memcpy(&val_len, ptr, sizeof(val_len));
            ptr += sizeof(val_len);
            
            if (ptr + val_len > end) break;
            std::string value(ptr, val_len);
            ptr += val_len;
            
            result.emplace_back(std::move(column), std::move(value));
        }
        
        // Only check sorting if we have data
        if (!result.empty()) {
            CHECK(std::is_sorted(result.begin(), result.end()), "Columns Must Be sorted before deserialization");
        }
        return result;
    }
    
    // Create composite key from key and column
    inline std::string make_composite_key(const std::string& key, const std::string& column) {
        //make sure there is no separator in the key or column, so that user cannot fake the composite key
        if (key.find(KEY_COLUMN_SEPARATOR) != std::string::npos || 
            column.find(KEY_COLUMN_SEPARATOR) != std::string::npos ||
            key.empty() ||
            column.empty()) {
            throw std::invalid_argument("Key or column contains separator or is empty");
        }
        return key + KEY_COLUMN_SEPARATOR + column;
    }

    // Extract key and column from composite key
    inline std::pair<std::string, std::string> split_composite_key(const std::string& composite_key) {
        size_t pos = composite_key.find(KEY_COLUMN_SEPARATOR);
        if (pos != std::string::npos) {
            return {composite_key.substr(0, pos), composite_key.substr(pos + 1)};
        }
        throw std::invalid_argument("Composite key is invalid");
        return {composite_key, ""};
    }
}

/**
 * JanusGraph KVT Adapter class
 * Provides key-column-value operations using the underlying KVT store
 */
class JanusGraphKVTAdapter {
public:
    JanusGraphKVTAdapter() = default;
    
    /**
     * Set a column value for a key
     */
    bool set_column(uint64_t tx_id, const std::string& table_name,
                    const std::string& key, const std::string& column,
                    const std::string& value, std::string& error_msg) {
        
        // Input validation
        if (key.empty() || column.empty()) {
            error_msg = "Key and column cannot be empty";
            return false;
        }
        
        if (g_use_composite_key_method) {
            // Method 2: Use composite key
            std::string composite_key = serialization::make_composite_key(key, column);
            return kvt_set(tx_id, table_name, composite_key, value, error_msg);
        } else {
            // Method 1: Serialize all columns in value
            // First, get existing columns
            std::vector<ColumnValue> columns = get_all_columns(tx_id, table_name, key, error_msg);
            // Update or add the column
            if (columns.empty()) {
                columns.emplace_back(column, value);
            } else {
                CHECK(std::is_sorted(columns.begin(), columns.end()), "Columns are not sorted");
                // Binary search and then update or insert
                auto it = std::lower_bound(columns.begin(), columns.end(), column,
                                         [](const ColumnValue& cv, const std::string& col) {
                                             return cv.column < col;
                                         });
                if (it != columns.end() && it->column == column) {
                    it->value = value;
                } else {
                    columns.emplace(it, column, value);
                }
            }
            // Serialize and store
            std::string serialized = serialization::serialize_columns(columns);
            return kvt_set(tx_id, table_name, key, serialized, error_msg);
        }
    }
    
    /**
     * Get a column value for a key
     */
    bool get_column(uint64_t tx_id, const std::string& table_name,
                   const std::string& key, const std::string& column,
                   std::string& value, std::string& error_msg) {
        
        // Input validation
        if (key.empty() || column.empty()) {
            error_msg = "Key and column cannot be empty";
            return false;
        }
        
        if (g_use_composite_key_method) {
            // Method 2: Use composite key
            std::string composite_key = serialization::make_composite_key(key, column);
            return kvt_get(tx_id, table_name, composite_key, value, error_msg);
        } else {
            // Method 1: Deserialize and search
            std::string serialized;
            if (!kvt_get(tx_id, table_name, key, serialized, error_msg)) {
                return false;
            }
            
            std::vector<ColumnValue> columns = serialization::deserialize_columns(serialized);
            
            // Binary search since columns are sorted
            auto it = std::lower_bound(columns.begin(), columns.end(), column,
                                     [](const ColumnValue& cv, const std::string& col) {
                                         return cv.column < col;
                                     });
            if (it != columns.end() && it->column == column) {
                value = it->value;
                return true;
            }
            error_msg = "Column not found: " + column;
            return false;
        }
    }
    
    /**
     * Delete a column for a key
     */
    bool delete_column(uint64_t tx_id, const std::string& table_name,
                      const std::string& key, const std::string& column,
                      std::string& error_msg) {
        
        // Input validation
        if (key.empty() || column.empty()) {
            error_msg = "Key and column cannot be empty";
            return false;
        }
        
        if (g_use_composite_key_method) {
            // Method 2: Delete composite key
            std::string composite_key = serialization::make_composite_key(key, column);
            return kvt_del(tx_id, table_name, composite_key, error_msg);
        } else {
            // Method 1: Remove from serialized columns (assuming columns are sorted)
            std::vector<ColumnValue> columns = get_all_columns(tx_id, table_name, key, error_msg);
            CHECK(std::is_sorted(columns.begin(), columns.end()), "Columns are not sorted");
            // Binary search to find the column
            auto it = std::lower_bound(columns.begin(), columns.end(), column,
                                     [](const ColumnValue& cv, const std::string& col) {
                                         return cv.column < col;
                                     });
            
            // Check if column was found and matches exactly
            if (it == columns.end() || it->column != column) {
                error_msg = "Column not found: " + column;
                return false;
            }
            // Erase the found column
            columns.erase(it);
            
            if (columns.empty()) {
                // Delete the entire key if no columns left
                return kvt_del(tx_id, table_name, key, error_msg);
            } else {
                // Update with remaining columns
                std::string serialized = serialization::serialize_columns(columns);
                return kvt_set(tx_id, table_name, key, serialized, error_msg);
            }
        }
    }
    
    /**
     * Get all columns for a key
     */
    std::vector<ColumnValue> get_all_columns(uint64_t tx_id, const std::string& table_name,
                                             const std::string& key, std::string& error_msg) {
        std::vector<ColumnValue> result;
        
        if (g_use_composite_key_method) {
            // Method 2: Scan for all keys with the prefix
            // Note: This requires range scan support
            std::string start_key = serialization::make_composite_key(key, "");
            std::string end_key = key + char(KEY_COLUMN_SEPARATOR + 1); // Next character after separator
            
            std::vector<std::pair<std::string, std::string>> scan_results;
            if (kvt_scan(tx_id, table_name, start_key, end_key, 10000, scan_results, error_msg)) {
                for (const auto& [composite_key, value] : scan_results) {
                    auto [extracted_key, column] = serialization::split_composite_key(composite_key);
                    CHECK(extracted_key == key, "Composite key is not extracted correctly");
                    result.emplace_back(column, value);
                }
            }
        } else {
            // Method 1: Deserialize all columns
            std::string serialized;
            if (kvt_get(tx_id, table_name, key, serialized, error_msg)) {
                result = serialization::deserialize_columns(serialized);
            }
        }
        return result;
    }
    
    /**
     * Delete all columns for a key
     */
    bool delete_key(uint64_t tx_id, const std::string& table_name,
                    const std::string& key, std::string& error_msg) {
        
        // Input validation
        if (key.empty()) {
            error_msg = "Key cannot be empty";
            return false;
        }
        
        if (g_use_composite_key_method) {
            // Method 2: Delete all composite keys with this prefix
            std::vector<ColumnValue> columns = get_all_columns(tx_id, table_name, key, error_msg);
            
            for (const auto& cv : columns) {
                std::string composite_key = serialization::make_composite_key(key, cv.column);
                if (!kvt_del(tx_id, table_name, composite_key, error_msg)) {
                    return false;
                }
            }
            return true;
        } else {
            // Method 1: Delete the single key
            return kvt_del(tx_id, table_name, key, error_msg);
        }
    }
    
    /**
     * Set multiple columns for a key atomically
     */
    bool set_columns(uint64_t tx_id, const std::string& table_name,
                    const std::string& key, const std::vector<ColumnValue>& columns,
                    std::string& error_msg) {
        
        // Input validation
        if (key.empty() || columns.empty()) {
            error_msg = "Key and columns cannot be empty";
            return false;
        }
        if (columns.empty()) {
            error_msg = "Columns vector cannot be empty";
            return false;
        }
        
        if (g_use_composite_key_method) {
            // Method 2: Set each composite key
            for (const auto& cv : columns) {
                std::string composite_key = serialization::make_composite_key(key, cv.column);
                if (!kvt_set(tx_id, table_name, composite_key, cv.value, error_msg)) {
                    return false;
                }
            }
            return true;
        } else {
            // Method 1: Serialize and store all at once
            // Get existing columns first
            std::vector<ColumnValue> existing = get_all_columns(tx_id, table_name, key, error_msg);
            // Merge with new columns (new ones override existing)
            std::map<std::string, std::string> column_map;
            for (const auto& cv : existing) {
                column_map[cv.column] = cv.value;
            }
            for (const auto& cv : columns) {
                column_map[cv.column] = cv.value;
            }
            
            // Convert back to vector (map is already sorted by key)
            std::vector<ColumnValue> merged;
            for (const auto& [col, val] : column_map) {
                merged.emplace_back(col, val);
            }
            // Serialize and store
            std::string serialized = serialization::serialize_columns(merged);
            return kvt_set(tx_id, table_name, key, serialized, error_msg);
        }
    }
};

} // namespace janusgraph

#endif // JANUSGRAPH_KVT_ADAPTER_H