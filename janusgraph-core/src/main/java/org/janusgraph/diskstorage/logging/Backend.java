package org.janusgraph.diskstorage.logging;

/**
 * Global configuration for storage backend logging.
 */
public class Backend {
    
    /**
     * Global flag to enable/disable detailed storage backend logging.
     * Can be set via system property 'janusgraph.storage.logging' or 
     * environment variable 'JANUSGRAPH_STORAGE_LOGGING'.
     */
    public static volatile boolean ENABLE_DETAILED_LOGGING = false;
    
    static {
        // Load configuration from system property or environment variable
        String loggingProp = System.getProperty("janusgraph.storage.logging");
        if (loggingProp == null) {
            loggingProp = System.getenv("JANUSGRAPH_STORAGE_LOGGING");
        }
        
        if (loggingProp != null) {
            ENABLE_DETAILED_LOGGING = Boolean.parseBoolean(loggingProp);
        }
    }
}
