#include <jni.h>
#include <string>
#include <vector>
#include <memory>
#include <map>
#include <iostream>
#include "../../../kvt/kvt_inc.h"
#include "../../../kvt/janusgraph/janusgraph_kvt_adapter.h"

// Helper functions for JNI string conversion
std::string jstringToString(JNIEnv *env, jstring jstr) {
    if (!jstr) return "";
    
    const char* cstr = env->GetStringUTFChars(jstr, nullptr);
    std::string result(cstr);
    env->ReleaseStringUTFChars(jstr, cstr);
    return result;
}

jstring stringToJstring(JNIEnv *env, const std::string& str) {
    return env->NewStringUTF(str.c_str());
}

// Convert Java byte array to std::string
std::string jbyteArrayToString(JNIEnv *env, jbyteArray jbytes) {
    if (!jbytes) return "";
    
    jsize length = env->GetArrayLength(jbytes);
    jbyte* bytes = env->GetByteArrayElements(jbytes, nullptr);
    std::string result(reinterpret_cast<char*>(bytes), length);
    env->ReleaseByteArrayElements(jbytes, bytes, JNI_ABORT);
    return result;
}

// Convert std::string to Java byte array
jbyteArray stringToJbyteArray(JNIEnv *env, const std::string& str) {
    jbyteArray result = env->NewByteArray(str.length());
    env->SetByteArrayRegion(result, 0, str.length(), 
                           reinterpret_cast<const jbyte*>(str.c_str()));
    return result;
}

// Global KVT adapter instance - one per JVM
static std::unique_ptr<janusgraph::JanusGraphKVTAdapter> g_kvt_adapter;
static std::map<jlong, std::string> g_store_names; // store pointer -> store name mapping

extern "C" {

// KVT System Management
JNIEXPORT jboolean JNICALL Java_org_janusgraph_diskstorage_kvt_KVTStoreManager_initializeKVT(JNIEnv *env, jobject obj) {
    try {
        bool success = kvt_initialize();
        if (success) {
            g_kvt_adapter = std::make_unique<janusgraph::JanusGraphKVTAdapter>();
        }
        return success;
    } catch (...) {
        return false;
    }
}

JNIEXPORT void JNICALL Java_org_janusgraph_diskstorage_kvt_KVTStoreManager_shutdownKVT(JNIEnv *env, jobject obj) {
    try {
        g_kvt_adapter.reset();
        g_store_names.clear();
        kvt_shutdown();
    } catch (...) {
        // Ignore errors during shutdown
    }
}

// Store Management
JNIEXPORT jlong JNICALL Java_org_janusgraph_diskstorage_kvt_KVTStoreManager_openDatabase(
    JNIEnv *env, jobject obj, jstring storeName, jboolean useCompositeKey) {
    try {
        if (!g_kvt_adapter) {
            return 0; // KVT not initialized
        }
        
        std::string name = jstringToString(env, storeName);
        
        // Set the storage method based on parameter
        janusgraph::g_use_composite_key_method = useCompositeKey;
        
        // Create table in KVT - use range partitioning for composite keys, hash for serialized
        std::string partition_method = useCompositeKey ? "range" : "hash";
        std::string error;
        uint64_t table_id = kvt_create_table(name, partition_method, error);
        
        if (table_id == 0 && error.find("already exists") == std::string::npos) {
            std::cerr << "Failed to create KVT table " << name << ": " << error << std::endl;
            return 0;
        }
        
        // Return table_id as store handle - we'll use this to identify the store
        g_store_names[table_id] = name;
        return static_cast<jlong>(table_id);
        
    } catch (const std::exception& e) {
        std::cerr << "Exception in openDatabase: " << e.what() << std::endl;
        return 0;
    } catch (...) {
        return 0;
    }
}

JNIEXPORT jboolean JNICALL Java_org_janusgraph_diskstorage_kvt_KVTStoreManager_exists(JNIEnv *env, jobject obj) {
    return g_kvt_adapter != nullptr && !g_store_names.empty();
}

JNIEXPORT void JNICALL Java_org_janusgraph_diskstorage_kvt_KVTStoreManager_clearStorage(JNIEnv *env, jobject obj) {
    // Since KVT doesn't have a global clear, we'll clear each table individually
    // This would need to be implemented in KVT if needed
}

// Transaction Management
JNIEXPORT jlong JNICALL Java_org_janusgraph_diskstorage_kvt_KVTKeyColumnValueStore_beginTransaction(JNIEnv *env, jobject obj) {
    try {
        std::string error;
        uint64_t tx_id = kvt_start_transaction(error);
        if (tx_id == 0) {
            std::cerr << "Failed to start transaction: " << error << std::endl;
        }
        return static_cast<jlong>(tx_id);
    } catch (...) {
        return 0;
    }
}

JNIEXPORT jboolean JNICALL Java_org_janusgraph_diskstorage_kvt_KVTKeyColumnValueStore_commitTransaction(
    JNIEnv *env, jobject obj, jlong txId) {
    try {
        std::string error;
        return kvt_commit_transaction(static_cast<uint64_t>(txId), error);
    } catch (...) {
        return false;
    }
}

JNIEXPORT jboolean JNICALL Java_org_janusgraph_diskstorage_kvt_KVTKeyColumnValueStore_rollbackTransaction(
    JNIEnv *env, jobject obj, jlong txId) {
    try {
        std::string error;
        return kvt_rollback_transaction(static_cast<uint64_t>(txId), error);
    } catch (...) {
        return false;
    }
}

// Store Operations
JNIEXPORT jobjectArray JNICALL Java_org_janusgraph_diskstorage_kvt_KVTKeyColumnValueStore_getSlice(
    JNIEnv *env, jobject obj, jlong storeId, jlong txId, jbyteArray key, 
    jbyteArray columnStart, jbyteArray columnEnd, jint limit) {
    
    try {
        if (!g_kvt_adapter) {
            return nullptr;
        }
        
        auto store_iter = g_store_names.find(storeId);
        if (store_iter == g_store_names.end()) {
            return nullptr;
        }
        
        std::string table_name = store_iter->second;
        std::string keyStr = jbyteArrayToString(env, key);
        std::string startStr = jbyteArrayToString(env, columnStart);
        std::string endStr = jbyteArrayToString(env, columnEnd);
        
        // Get all columns for the key using the adapter
        std::string error;
        auto columns = g_kvt_adapter->get_all_columns(static_cast<uint64_t>(txId), table_name, keyStr, error);
        
        // Filter columns based on range and limit
        std::vector<janusgraph::ColumnValue> filtered;
        int count = 0;
        for (const auto& cv : columns) {
            if ((startStr.empty() || cv.column >= startStr) &&
                (endStr.empty() || cv.column < endStr)) {
                filtered.push_back(cv);
                count++;
                if (limit > 0 && count >= limit) {
                    break;
                }
            }
        }
        
        // Convert to Java array of Entry objects
        jclass entryClass = env->FindClass("org/janusgraph/diskstorage/util/StaticArrayEntry");
        jmethodID entryConstructor = env->GetMethodID(entryClass, "<init>", "([B[B)V");
        
        jobjectArray result = env->NewObjectArray(filtered.size(), entryClass, nullptr);
        
        for (size_t i = 0; i < filtered.size(); i++) {
            jbyteArray colBytes = stringToJbyteArray(env, filtered[i].column);
            jbyteArray valBytes = stringToJbyteArray(env, filtered[i].value);
            
            jobject entry = env->NewObject(entryClass, entryConstructor, colBytes, valBytes);
            env->SetObjectArrayElement(result, i, entry);
            
            env->DeleteLocalRef(colBytes);
            env->DeleteLocalRef(valBytes);
            env->DeleteLocalRef(entry);
        }
        
        return result;
        
    } catch (const std::exception& e) {
        std::cerr << "Exception in getSlice: " << e.what() << std::endl;
        return nullptr;
    } catch (...) {
        return nullptr;
    }
}

JNIEXPORT void JNICALL Java_org_janusgraph_diskstorage_kvt_KVTKeyColumnValueStore_mutate(
    JNIEnv *env, jobject obj, jlong storeId, jlong txId, jbyteArray key,
    jobjectArray additions, jobjectArray deletions) {
    
    try {
        if (!g_kvt_adapter) {
            return;
        }
        
        auto store_iter = g_store_names.find(storeId);
        if (store_iter == g_store_names.end()) {
            return;
        }
        
        std::string table_name = store_iter->second;
        std::string keyStr = jbyteArrayToString(env, key);
        std::string error;
        
        // Process deletions first
        if (deletions) {
            jsize delCount = env->GetArrayLength(deletions);
            for (jsize i = 0; i < delCount; i++) {
                jbyteArray colBytes = (jbyteArray)env->GetObjectArrayElement(deletions, i);
                std::string column = jbyteArrayToString(env, colBytes);
                
                g_kvt_adapter->delete_column(static_cast<uint64_t>(txId), table_name, keyStr, column, error);
                
                env->DeleteLocalRef(colBytes);
            }
        }
        
        // Process additions
        if (additions) {
            jsize addCount = env->GetArrayLength(additions);
            jclass entryClass = env->FindClass("org/janusgraph/diskstorage/Entry");
            jmethodID getColumnMethod = env->GetMethodID(entryClass, "getColumn", "()Lorg/janusgraph/diskstorage/StaticBuffer;");
            jmethodID getValueMethod = env->GetMethodID(entryClass, "getValue", "()Lorg/janusgraph/diskstorage/StaticBuffer;");
            
            jclass bufferClass = env->FindClass("org/janusgraph/diskstorage/StaticBuffer");
            jmethodID asByteArrayMethod = env->GetMethodID(bufferClass, "as", "([B)[B");
            
            for (jsize i = 0; i < addCount; i++) {
                jobject entry = env->GetObjectArrayElement(additions, i);
                
                jobject colBuffer = env->CallObjectMethod(entry, getColumnMethod);
                jobject valBuffer = env->CallObjectMethod(entry, getValueMethod);
                
                jbyteArray colBytes = (jbyteArray)env->CallObjectMethod(colBuffer, asByteArrayMethod, env->NewByteArray(0));
                jbyteArray valBytes = (jbyteArray)env->CallObjectMethod(valBuffer, asByteArrayMethod, env->NewByteArray(0));
                
                std::string column = jbyteArrayToString(env, colBytes);
                std::string value = jbyteArrayToString(env, valBytes);
                
                g_kvt_adapter->set_column(static_cast<uint64_t>(txId), table_name, keyStr, column, value, error);
                
                env->DeleteLocalRef(entry);
                env->DeleteLocalRef(colBuffer);
                env->DeleteLocalRef(valBuffer);
                env->DeleteLocalRef(colBytes);
                env->DeleteLocalRef(valBytes);
            }
        }
        
    } catch (const std::exception& e) {
        std::cerr << "Exception in mutate: " << e.what() << std::endl;
    } catch (...) {
        // Ignore errors
    }
}

JNIEXPORT jobjectArray JNICALL Java_org_janusgraph_diskstorage_kvt_KVTKeyColumnValueStore_getKeys(
    JNIEnv *env, jobject obj, jlong storeId, jlong txId, 
    jbyteArray keyStart, jbyteArray keyEnd, 
    jbyteArray columnStart, jbyteArray columnEnd, jint limit) {
    
    try {
        if (!g_kvt_adapter) {
            return nullptr;
        }
        
        auto store_iter = g_store_names.find(storeId);
        if (store_iter == g_store_names.end()) {
            return nullptr;
        }
        
        std::string table_name = store_iter->second;
        std::string keyStartStr = jbyteArrayToString(env, keyStart);
        std::string keyEndStr = jbyteArrayToString(env, keyEnd);
        std::string error;
        
        // Use KVT scan to get keys in range
        std::vector<std::pair<std::string, std::string>> scan_results;
        kvt_scan(static_cast<uint64_t>(txId), table_name, keyStartStr, keyEndStr, limit, scan_results, error);
        
        // Extract unique keys
        std::set<std::string> unique_keys;
        for (const auto& [key, value] : scan_results) {
            if (janusgraph::g_use_composite_key_method) {
                // For composite keys, extract the original key part
                auto [original_key, column] = janusgraph::serialization::split_composite_key(key);
                unique_keys.insert(original_key);
            } else {
                // For serialized columns, the key is the key
                unique_keys.insert(key);
            }
        }
        
        // Convert to Java byte array
        jobjectArray result = env->NewObjectArray(unique_keys.size(), env->FindClass("[B"), nullptr);
        
        int i = 0;
        for (const std::string& key : unique_keys) {
            jbyteArray keyBytes = stringToJbyteArray(env, key);
            env->SetObjectArrayElement(result, i++, keyBytes);
            env->DeleteLocalRef(keyBytes);
        }
        
        return result;
        
    } catch (const std::exception& e) {
        std::cerr << "Exception in getKeys: " << e.what() << std::endl;
        return nullptr;
    } catch (...) {
        return nullptr;
    }
}

JNIEXPORT jstring JNICALL Java_org_janusgraph_diskstorage_kvt_KVTKeyColumnValueStore_getName(
    JNIEnv *env, jobject obj, jlong storeId) {
    
    auto store_iter = g_store_names.find(storeId);
    if (store_iter != g_store_names.end()) {
        return stringToJstring(env, store_iter->second);
    }
    return nullptr;
}

JNIEXPORT void JNICALL Java_org_janusgraph_diskstorage_kvt_KVTKeyColumnValueStore_close(
    JNIEnv *env, jobject obj, jlong storeId) {
    
    // Remove from our mapping
    g_store_names.erase(storeId);
}

} // extern "C"