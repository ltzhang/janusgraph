#include <jni.h>
#include <string>
#include <vector>
#include <memory>
#include "../cpp_memdb/StaticBuffer.h"
#include "../cpp_memdb/Entry.h"
#include "../cpp_memdb/EntryList.h"
#include "../cpp_memdb/SliceQuery.h"
#include "../cpp_memdb/StoreTransaction.h"
#include "../cpp_memdb/InMemoryKeyColumnValueStore.h"
#include "../cpp_memdb/InMemoryStoreManager.h"

// Helper functions
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

// Database management functions
extern "C" {

JNIEXPORT jlong JNICALL Java_NativeInMemoryDB_createDB(JNIEnv *env, jobject obj) {
    try {
        auto* manager = new InMemoryStoreManager();
        return reinterpret_cast<jlong>(manager);
    } catch (...) {
        return 0;
    }
}

JNIEXPORT void JNICALL Java_NativeInMemoryDB_destroyDB(JNIEnv *env, jobject obj, jlong ptr) {
    if (ptr != 0) {
        auto* manager = reinterpret_cast<InMemoryStoreManager*>(ptr);
        delete manager;
    }
}

JNIEXPORT jlong JNICALL Java_NativeInMemoryDB_openStore(JNIEnv *env, jobject obj, jlong dbPtr, jstring storeName) {
    try {
        auto* manager = reinterpret_cast<InMemoryStoreManager*>(dbPtr);
        std::string name = jstringToString(env, storeName);
        
        auto store = manager->openDatabase(name);
        // Return raw pointer (store is managed by shared_ptr in manager)
        return reinterpret_cast<jlong>(store.get());
    } catch (...) {
        return 0;
    }
}

JNIEXPORT void JNICALL Java_NativeInMemoryDB_closeDB(JNIEnv *env, jobject obj, jlong dbPtr) {
    if (dbPtr != 0) {
        auto* manager = reinterpret_cast<InMemoryStoreManager*>(dbPtr);
        manager->close();
    }
}

JNIEXPORT void JNICALL Java_NativeInMemoryDB_clearStorage(JNIEnv *env, jobject obj, jlong dbPtr) {
    if (dbPtr != 0) {
        auto* manager = reinterpret_cast<InMemoryStoreManager*>(dbPtr);
        manager->clearStorage();
    }
}

JNIEXPORT jboolean JNICALL Java_NativeInMemoryDB_exists(JNIEnv *env, jobject obj, jlong dbPtr) {
    if (dbPtr != 0) {
        auto* manager = reinterpret_cast<InMemoryStoreManager*>(dbPtr);
        return manager->exists();
    }
    return false;
}

JNIEXPORT jint JNICALL Java_NativeInMemoryDB_getStoreCount(JNIEnv *env, jobject obj, jlong dbPtr) {
    if (dbPtr != 0) {
        auto* manager = reinterpret_cast<InMemoryStoreManager*>(dbPtr);
        return manager->getStoreCount();
    }
    return 0;
}

// Store operations
JNIEXPORT void JNICALL Java_NativeInMemoryDB_put(JNIEnv *env, jobject obj, jlong storePtr, jstring key, jstring column, jstring value) {
    try {
        auto* store = reinterpret_cast<InMemoryKeyColumnValueStore*>(storePtr);
        
        std::string keyStr = jstringToString(env, key);
        std::string colStr = jstringToString(env, column);
        std::string valStr = jstringToString(env, value);
        
        StaticBuffer keyBuf(keyStr);
        StaticBuffer colBuf(colStr);
        StaticBuffer valBuf(valStr);
        Entry entry(colBuf, valBuf);
        
        std::vector<Entry> additions;
        additions.push_back(entry);
        std::vector<StaticBuffer> deletions;
        
        StoreTransaction txh;
        store->mutate(keyBuf, additions, deletions, txh);
    } catch (...) {
        // Ignore errors for now
    }
}

JNIEXPORT void JNICALL Java_NativeInMemoryDB_delete(JNIEnv *env, jobject obj, jlong storePtr, jstring key, jstring column) {
    try {
        auto* store = reinterpret_cast<InMemoryKeyColumnValueStore*>(storePtr);
        
        std::string keyStr = jstringToString(env, key);
        std::string colStr = jstringToString(env, column);
        
        StaticBuffer keyBuf(keyStr);
        StaticBuffer colBuf(colStr);
        
        std::vector<Entry> additions;
        std::vector<StaticBuffer> deletions;
        deletions.push_back(colBuf);
        
        StoreTransaction txh;
        store->mutate(keyBuf, additions, deletions, txh);
    } catch (...) {
        // Ignore errors for now
    }
}

JNIEXPORT void JNICALL Java_NativeInMemoryDB_mutateMany(JNIEnv *env, jobject obj, jlong storePtr, jstring key, 
                                                       jobjectArray addColumns, jobjectArray addValues, jobjectArray delColumns) {
    try {
        auto* store = reinterpret_cast<InMemoryKeyColumnValueStore*>(storePtr);
        std::string keyStr = jstringToString(env, key);
        StaticBuffer keyBuf(keyStr);
        
        std::vector<Entry> additions;
        std::vector<StaticBuffer> deletions;
        
        // Process additions
        if (addColumns && addValues) {
            jsize addCount = env->GetArrayLength(addColumns);
            for (jsize i = 0; i < addCount; i++) {
                jstring colStr = (jstring)env->GetObjectArrayElement(addColumns, i);
                jstring valStr = (jstring)env->GetObjectArrayElement(addValues, i);
                
                std::string col = jstringToString(env, colStr);
                std::string val = jstringToString(env, valStr);
                
                additions.emplace_back(StaticBuffer(col), StaticBuffer(val));
                
                env->DeleteLocalRef(colStr);
                env->DeleteLocalRef(valStr);
            }
        }
        
        // Process deletions
        if (delColumns) {
            jsize delCount = env->GetArrayLength(delColumns);
            for (jsize i = 0; i < delCount; i++) {
                jstring colStr = (jstring)env->GetObjectArrayElement(delColumns, i);
                std::string col = jstringToString(env, colStr);
                deletions.emplace_back(StaticBuffer(col));
                env->DeleteLocalRef(colStr);
            }
        }
        
        StoreTransaction txh;
        store->mutate(keyBuf, additions, deletions, txh);
    } catch (...) {
        // Ignore errors for now
    }
}

JNIEXPORT jobjectArray JNICALL Java_NativeInMemoryDB_getSlice(JNIEnv *env, jobject obj, jlong storePtr, jstring key, 
                                                             jstring startColumn, jstring endColumn) {
    try {
        auto* store = reinterpret_cast<InMemoryKeyColumnValueStore*>(storePtr);
        
        std::string keyStr = jstringToString(env, key);
        std::string startStr = jstringToString(env, startColumn);
        std::string endStr = jstringToString(env, endColumn);
        
        StaticBuffer keyBuf(keyStr);
        StaticBuffer startBuf(startStr);
        StaticBuffer endBuf(endStr);
        SliceQuery slice(startBuf, endBuf);
        KeySliceQuery keySlice(keyBuf, slice);
        
        StoreTransaction txh;
        EntryList result = store->getSlice(keySlice, txh);
        
        // Convert result to string array [column1, value1, column2, value2, ...]
        jclass stringClass = env->FindClass("java/lang/String");
        jobjectArray resultArray = env->NewObjectArray(result.size() * 2, stringClass, nullptr);
        
        for (size_t i = 0; i < result.size(); i++) {
            const Entry& entry = result[i];
            
            // Convert column and value to Java strings
            const auto& colBytes = entry.getColumn().getBytes();
            const auto& valBytes = entry.getValue().getBytes();
            
            std::string colStr(colBytes.begin(), colBytes.end());
            std::string valStr(valBytes.begin(), valBytes.end());
            
            jstring colJstr = stringToJstring(env, colStr);
            jstring valJstr = stringToJstring(env, valStr);
            
            env->SetObjectArrayElement(resultArray, i * 2, colJstr);
            env->SetObjectArrayElement(resultArray, i * 2 + 1, valJstr);
            
            env->DeleteLocalRef(colJstr);
            env->DeleteLocalRef(valJstr);
        }
        
        return resultArray;
    } catch (...) {
        // Return empty array on error
        jclass stringClass = env->FindClass("java/lang/String");
        return env->NewObjectArray(0, stringClass, nullptr);
    }
}

JNIEXPORT jint JNICALL Java_NativeInMemoryDB_getEntryCount(JNIEnv *env, jobject obj, jlong storePtr, jstring key) {
    try {
        auto* store = reinterpret_cast<InMemoryKeyColumnValueStore*>(storePtr);
        
        std::string keyStr = jstringToString(env, key);
        StaticBuffer keyBuf(keyStr);
        
        // Get all entries for the key
        SliceQuery slice(StaticBuffer(""), StaticBuffer("zzzzz"));
        KeySliceQuery keySlice(keyBuf, slice);
        
        StoreTransaction txh;
        EntryList result = store->getSlice(keySlice, txh);
        
        return result.size();
    } catch (...) {
        return 0;
    }
}

JNIEXPORT jboolean JNICALL Java_NativeInMemoryDB_isEmpty(JNIEnv *env, jobject obj, jlong storePtr) {
    try {
        auto* store = reinterpret_cast<InMemoryKeyColumnValueStore*>(storePtr);
        return store->isEmpty();
    } catch (...) {
        return true;
    }
}

JNIEXPORT void JNICALL Java_NativeInMemoryDB_clearStore(JNIEnv *env, jobject obj, jlong storePtr) {
    try {
        auto* store = reinterpret_cast<InMemoryKeyColumnValueStore*>(storePtr);
        store->clear();
    } catch (...) {
        // Ignore errors
    }
}

} // extern "C"