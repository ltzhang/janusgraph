package org.janusgraph.diskstorage.logging;

import org.janusgraph.diskstorage.StaticBuffer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Serializes StaticBuffer objects for logging with binary value mapping.
 * Printable ASCII is shown as text, non-printable data uses hash table mapping.
 */
public class CompactBufferSerializer {
    
    private static final int MAX_BYTES = 16;
    private static final Map<String, String> binaryValueMap = new ConcurrentHashMap<>();
    private static final AtomicInteger valueCounter = new AtomicInteger(0);
    
    /**
     * Serialize a StaticBuffer for logging.
     * 
     * @param buffer The buffer to serialize
     * @return String representation
     */
    public static String serialize(StaticBuffer buffer) {
        if (buffer == null) return "null";
        if (buffer.length() == 0) return "[]";
        
        byte[] bytes = new byte[Math.min(buffer.length(), MAX_BYTES)];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = buffer.getByte(i);
        }
        
        // Check if printable ASCII
        if (isPrintableAscii(bytes)) {
            String result = bytesToText(bytes);
            if (buffer.length() > MAX_BYTES) {
                result += "...";
            }
            return result;
        } else {
            // Use hash table for non-printable data
            return getOrCreateBinaryValue(bytes);
        }
    }
    
    /**
     * Check if byte array contains only printable ASCII characters.
     */
    private static boolean isPrintableAscii(byte[] bytes) {
        for (byte b : bytes) {
            if (b < 32 || b > 126) return false;
        }
        return true;
    }
    
    /**
     * Get or create a binary value mapping for non-printable data.
     */
    private static String getOrCreateBinaryValue(byte[] bytes) {
        String hexKey = bytesToHex(bytes);
        return binaryValueMap.computeIfAbsent(hexKey, k -> {
            int counter = valueCounter.incrementAndGet();
            String valueName = "V_" + counter;
            // Log the mapping immediately
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < bytes.length; i++) {
                sb.append(String.format("\\%02X", bytes[i] & 0xFF));
            }
            System.out.println("V_" + counter + "=" + sb.toString());
            return valueName;
        });
    }
    
    /**
     * Convert byte array to hex string representation.
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) sb.append(" ");
            sb.append(String.format("%02X", bytes[i] & 0xFF));
        }
        sb.append("]");
        return sb.toString();
    }
    
    /**
     * Convert byte array to text string representation.
     */
    private static String bytesToText(byte[] bytes) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < bytes.length; i++) {
            sb.append((char) bytes[i]);
        }
        sb.append("\"");
        return sb.toString();
    }
}
