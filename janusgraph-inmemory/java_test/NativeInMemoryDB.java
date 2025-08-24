import java.util.List;
import java.util.ArrayList;

/**
 * Simple JNI wrapper for C++ InMemory Database.
 * Provides only essential operations needed for equivalence testing.
 */
public class NativeInMemoryDB {
    
    private long nativePtr;
    
    static {
        System.loadLibrary("janusgraph_jni");
    }
    
    // Essential database operations
    private native long createDB();
    private native void destroyDB(long ptr);
    private native long openStore(long dbPtr, String storeName);
    private native void closeDB(long dbPtr);
    private native void clearStorage(long dbPtr);
    private native boolean exists(long dbPtr);
    private native int getStoreCount(long dbPtr);
    
    // Store operations
    private native void put(long storePtr, String key, String column, String value);
    private native void delete(long storePtr, String key, String column);
    private native void mutateMany(long storePtr, String key, String[] addColumns, String[] addValues, String[] delColumns);
    private native String[] getSlice(long storePtr, String key, String startColumn, String endColumn);
    private native int getEntryCount(long storePtr, String key);
    private native boolean isEmpty(long storePtr);
    private native void clearStore(long storePtr);
    
    // Data structure for returning key-value pairs
    public static class Entry {
        public final String column;
        public final String value;
        
        public Entry(String column, String value) {
            this.column = column;
            this.value = value;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            Entry entry = (Entry) obj;
            return column.equals(entry.column) && value.equals(entry.value);
        }
        
        @Override
        public String toString() {
            return column + ":" + value;
        }
    }
    
    // Constructor
    public NativeInMemoryDB() {
        this.nativePtr = createDB();
    }
    
    // Database management
    public NativeStore openStore(String storeName) {
        long storePtr = openStore(nativePtr, storeName);
        return new NativeStore(storePtr, this);
    }
    
    public void close() {
        if (nativePtr != 0) {
            closeDB(nativePtr);
        }
    }
    
    public void clearStorage() {
        clearStorage(nativePtr);
    }
    
    public boolean exists() {
        return exists(nativePtr);
    }
    
    public int getStoreCount() {
        return getStoreCount(nativePtr);
    }
    
    @Override
    protected void finalize() throws Throwable {
        if (nativePtr != 0) {
            destroyDB(nativePtr);
            nativePtr = 0;
        }
        super.finalize();
    }
    
    // Inner class for store operations
    public class NativeStore {
        private final long storePtr;
        private final NativeInMemoryDB parent;
        
        NativeStore(long storePtr, NativeInMemoryDB parent) {
            this.storePtr = storePtr;
            this.parent = parent;
        }
        
        public void put(String key, String column, String value) {
            parent.put(storePtr, key, column, value);
        }
        
        public void delete(String key, String column) {
            parent.delete(storePtr, key, column);
        }
        
        public void mutate(String key, List<Entry> additions, List<String> deletions) {
            String[] addColumns = new String[additions.size()];
            String[] addValues = new String[additions.size()];
            String[] delColumns = deletions.toArray(new String[0]);
            
            for (int i = 0; i < additions.size(); i++) {
                addColumns[i] = additions.get(i).column;
                addValues[i] = additions.get(i).value;
            }
            
            parent.mutateMany(storePtr, key, addColumns, addValues, delColumns);
        }
        
        public List<Entry> getSlice(String key, String startColumn, String endColumn) {
            String[] results = parent.getSlice(storePtr, key, startColumn, endColumn);
            List<Entry> entries = new ArrayList<>();
            
            // Results come in pairs: [column1, value1, column2, value2, ...]
            for (int i = 0; i < results.length; i += 2) {
                entries.add(new Entry(results[i], results[i + 1]));
            }
            
            return entries;
        }
        
        public int getEntryCount(String key) {
            return parent.getEntryCount(storePtr, key);
        }
        
        public boolean isEmpty() {
            return parent.isEmpty(storePtr);
        }
        
        public void clear() {
            parent.clearStore(storePtr);
        }
    }
}