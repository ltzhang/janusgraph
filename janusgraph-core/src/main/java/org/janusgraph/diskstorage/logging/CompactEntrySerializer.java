package org.janusgraph.diskstorage.logging;

import org.janusgraph.diskstorage.Entry;
import org.janusgraph.diskstorage.EntryList;
import org.janusgraph.diskstorage.keycolumnvalue.keyvalue.KeyValueEntry;
import org.janusgraph.diskstorage.util.RecordIterator;

/**
 * Serializes EntryList and RecordIterator objects for compact logging.
 */
public class CompactEntrySerializer {
    
    /**
     * Serialize an EntryList for logging.
     * 
     * @param entries The entry list to serialize
     * @return String representation
     */
    public static String serialize(EntryList entries) {
        if (entries == null) return "null";
        if (entries.isEmpty()) return "[0 entries]";
        return "[" + entries.size() + " entries]";
    }
    
    /**
     * Serialize a RecordIterator for logging.
     * 
     * @param iterator The iterator to serialize
     * @return String representation
     */
    public static String serialize(RecordIterator<KeyValueEntry> iterator) {
        if (iterator == null) return "null";
        return "[iterator]";
    }
    
    /**
     * Serialize a generic iterator for logging.
     * 
     * @param iterator The iterator to serialize
     * @return String representation
     */
    public static String serialize(java.util.Iterator<Entry> iterator) {
        if (iterator == null) return "null";
        return "[iterator]";
    }
}
