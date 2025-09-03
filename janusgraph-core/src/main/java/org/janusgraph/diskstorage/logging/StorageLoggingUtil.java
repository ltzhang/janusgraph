package org.janusgraph.diskstorage.logging;

import org.janusgraph.diskstorage.Entry;
import org.janusgraph.diskstorage.EntryList;
import org.janusgraph.diskstorage.StaticBuffer;
import org.janusgraph.diskstorage.keycolumnvalue.keyvalue.KeyValueEntry;
import org.janusgraph.diskstorage.util.RecordIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Utility class for compact storage backend logging.
 * Provides single-line log format with binary value mapping for non-printable data.
 */
public class StorageLoggingUtil {
    
    private static final Logger log = LoggerFactory.getLogger("StorageBackend");
    private static final AtomicLong callCounter = new AtomicLong(0);
    
    /**
     * Log a function call with compact single-line format.
     * 
     * @param component Component name (e.g., "STORAGE-MANAGER", "STORAGE-STORE", "STORAGE-TX")
     * @param function Function name
     * @param params Parameters map
     * @param startTime Start time in milliseconds
     */
    public static void logFunctionCall(String component, String function, Map<String, Object> params, long startTime) {
        if (!Backend.ENABLE_DETAILED_LOGGING) {
            return;
        }
        
        long duration = System.currentTimeMillis() - startTime;
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(component).append("] ").append(function).append(" | ");
        
        if (params != null && !params.isEmpty()) {
            boolean first = true;
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                if (!first) sb.append(" | ");
                sb.append(entry.getKey()).append(":").append(entry.getValue());
                first = false;
            }
            sb.append(" | ");
        }
        
        sb.append("Duration:").append(duration).append("ms");
        
        log.info(sb.toString());
    }
    
    /**
     * Serialize a StaticBuffer for logging.
     * 
     * @param buffer The buffer to serialize
     * @return String representation
     */
    public static String serializeBuffer(StaticBuffer buffer) {
        return CompactBufferSerializer.serialize(buffer);
    }
    
    /**
     * Serialize an EntryList for logging.
     * 
     * @param entries The entry list to serialize
     * @return String representation
     */
    public static String serializeEntryList(EntryList entries) {
        return CompactEntrySerializer.serialize(entries);
    }
    
    /**
     * Serialize a RecordIterator for logging.
     * 
     * @param iterator The iterator to serialize
     * @return String representation
     */
    public static String serializeIterator(RecordIterator<KeyValueEntry> iterator) {
        return CompactEntrySerializer.serialize(iterator);
    }
}
