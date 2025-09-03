#include <jni.h>
#include <string>
#include <vector>
#include <cstring>
#include <map>
#include <mutex>
#include "../../../kvt/kvt_inc.h"

// Mutex for thread-safety
static std::mutex g_jni_mutex;

// Helper function to convert Java byte array to C++ string
std::string jbyteArrayToString(JNIEnv* env, jbyteArray arr) {
    if (arr == nullptr) {
        return "";
    }
    jsize len = env->GetArrayLength(arr);
    std::string result(len, '\0');
    env->GetByteArrayRegion(arr, 0, len, reinterpret_cast<jbyte*>(&result[0]));
    return result;
}

// Helper function to convert C++ string to Java byte array
jbyteArray stringToJbyteArray(JNIEnv* env, const std::string& str) {
    jbyteArray arr = env->NewByteArray(str.length());
    if (arr != nullptr) {
        env->SetByteArrayRegion(arr, 0, str.length(), 
                                reinterpret_cast<const jbyte*>(str.data()));
    }
    return arr;
}

// Helper function to convert Java string to C++ string
std::string jstringToString(JNIEnv* env, jstring str) {
    if (str == nullptr) {
        return "";
    }
    const char* chars = env->GetStringUTFChars(str, nullptr);
    std::string result(chars);
    env->ReleaseStringUTFChars(str, chars);
    return result;
}

extern "C" {

// KVTStoreManager native methods

JNIEXPORT jlong JNICALL Java_org_janusgraph_diskstorage_kvt_KVTStoreManager_nativeInitialize
  (JNIEnv *env, jclass cls) {
    std::lock_guard<std::mutex> lock(g_jni_mutex);
    
    KVTError error = kvt_initialize();
    if (error != KVTError::SUCCESS) {
        return 0;
    }
    
    // Return a non-zero value as manager pointer (we use 1 as KVT uses global instance)
    return 1;
}

JNIEXPORT void JNICALL Java_org_janusgraph_diskstorage_kvt_KVTStoreManager_nativeShutdown
  (JNIEnv *env, jclass cls, jlong managerPtr) {
    std::lock_guard<std::mutex> lock(g_jni_mutex);
    
    if (managerPtr != 0) {
        kvt_shutdown();
    }
}

JNIEXPORT jlong JNICALL Java_org_janusgraph_diskstorage_kvt_KVTStoreManager_nativeCreateTable
  (JNIEnv *env, jclass cls, jlong managerPtr, jstring tableName, jstring partitionMethod) {
    std::lock_guard<std::mutex> lock(g_jni_mutex);
    
    if (managerPtr == 0) {
        return 0;
    }
    
    std::string table = jstringToString(env, tableName);
    std::string method = jstringToString(env, partitionMethod);
    
    uint64_t table_id = 0;
    std::string error_msg;
    
    KVTError error = kvt_create_table(table, method, table_id, error_msg);
    if (error != KVTError::SUCCESS && error != KVTError::TABLE_ALREADY_EXISTS) {
        return 0;
    }
    
    return static_cast<jlong>(table_id);
}

JNIEXPORT jlong JNICALL Java_org_janusgraph_diskstorage_kvt_KVTStoreManager_nativeStartTransaction
  (JNIEnv *env, jclass cls, jlong managerPtr) {
    std::lock_guard<std::mutex> lock(g_jni_mutex);
    
    if (managerPtr == 0) {
        return 0;
    }
    
    uint64_t tx_id = 0;
    std::string error_msg;
    
    KVTError error = kvt_start_transaction(tx_id, error_msg);
    if (error != KVTError::SUCCESS) {
        return 0;
    }
    
    return static_cast<jlong>(tx_id);
}

JNIEXPORT jboolean JNICALL Java_org_janusgraph_diskstorage_kvt_KVTStoreManager_nativeCommitTransaction
  (JNIEnv *env, jclass cls, jlong managerPtr, jlong txId) {
    std::lock_guard<std::mutex> lock(g_jni_mutex);
    
    if (managerPtr == 0 || txId == 0) {
        return JNI_FALSE;
    }
    
    std::string error_msg;
    KVTError error = kvt_commit_transaction(static_cast<uint64_t>(txId), error_msg);
    
    return (error == KVTError::SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_org_janusgraph_diskstorage_kvt_KVTStoreManager_nativeRollbackTransaction
  (JNIEnv *env, jclass cls, jlong managerPtr, jlong txId) {
    std::lock_guard<std::mutex> lock(g_jni_mutex);
    
    if (managerPtr == 0 || txId == 0) {
        return JNI_FALSE;
    }
    
    std::string error_msg;
    KVTError error = kvt_rollback_transaction(static_cast<uint64_t>(txId), error_msg);
    
    return (error == KVTError::SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

// KVTKeyColumnValueStore native methods

JNIEXPORT jbyteArray JNICALL Java_org_janusgraph_diskstorage_kvt_KVTKeyColumnValueStore_nativeGet
  (JNIEnv *env, jclass cls, jlong managerPtr, jlong txId, jlong tableId, jbyteArray key) {
    std::lock_guard<std::mutex> lock(g_jni_mutex);
    
    if (managerPtr == 0) {
        return nullptr;
    }
    
    std::string key_str = jbyteArrayToString(env, key);
    std::string value;
    std::string error_msg;
    
    KVTError error = kvt_get(static_cast<uint64_t>(txId), static_cast<uint64_t>(tableId), key_str, value, error_msg);
    
    if (error != KVTError::SUCCESS) {
        return nullptr;
    }
    
    return stringToJbyteArray(env, value);
}

JNIEXPORT jboolean JNICALL Java_org_janusgraph_diskstorage_kvt_KVTKeyColumnValueStore_nativeSet
  (JNIEnv *env, jclass cls, jlong managerPtr, jlong txId, jlong tableId, 
   jbyteArray key, jbyteArray value) {
    std::lock_guard<std::mutex> lock(g_jni_mutex);
    
    if (managerPtr == 0) {
        return JNI_FALSE;
    }
    
    std::string key_str = jbyteArrayToString(env, key);
    std::string value_str = jbyteArrayToString(env, value);
    std::string error_msg;
    
    KVTError error = kvt_set(static_cast<uint64_t>(txId), static_cast<uint64_t>(tableId), key_str, value_str, error_msg);
    
    return (error == KVTError::SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_org_janusgraph_diskstorage_kvt_KVTKeyColumnValueStore_nativeDelete
  (JNIEnv *env, jclass cls, jlong managerPtr, jlong txId, jlong tableId, jbyteArray key) {
    std::lock_guard<std::mutex> lock(g_jni_mutex);
    
    if (managerPtr == 0) {
        return JNI_FALSE;
    }
    
    std::string key_str = jbyteArrayToString(env, key);
    std::string error_msg;
    
    KVTError error = kvt_del(static_cast<uint64_t>(txId), static_cast<uint64_t>(tableId), key_str, error_msg);
    
    // Treat KEY_NOT_FOUND as success for delete operations
    return (error == KVTError::SUCCESS || error == KVTError::KEY_NOT_FOUND) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jobjectArray JNICALL Java_org_janusgraph_diskstorage_kvt_KVTKeyColumnValueStore_nativeScan
  (JNIEnv *env, jclass cls, jlong managerPtr, jlong txId, jlong tableId,
   jbyteArray startKey, jbyteArray endKey, jint limit) {
    std::lock_guard<std::mutex> lock(g_jni_mutex);
    
    if (managerPtr == 0) {
        return nullptr;
    }
    
    std::string start_key = jbyteArrayToString(env, startKey);
    std::string end_key = jbyteArrayToString(env, endKey);
    
    std::vector<std::pair<std::string, std::string>> results;
    std::string error_msg;
    
    KVTError error = kvt_scan(static_cast<uint64_t>(txId), static_cast<uint64_t>(tableId), start_key, end_key,
                             static_cast<size_t>(limit), results, error_msg);
    
    if (error != KVTError::SUCCESS || results.empty()) {
        return nullptr;
    }
    
    // Create array of byte arrays (alternating keys and values)
    jclass byteArrayClass = env->FindClass("[B");
    jobjectArray resultArray = env->NewObjectArray(results.size() * 2, byteArrayClass, nullptr);
    
    for (size_t i = 0; i < results.size(); i++) {
        jbyteArray keyArr = stringToJbyteArray(env, results[i].first);
        jbyteArray valArr = stringToJbyteArray(env, results[i].second);
        
        env->SetObjectArrayElement(resultArray, i * 2, keyArr);
        env->SetObjectArrayElement(resultArray, i * 2 + 1, valArr);
        
        env->DeleteLocalRef(keyArr);
        env->DeleteLocalRef(valArr);
    }
    
    return resultArray;
}

} // extern "C"