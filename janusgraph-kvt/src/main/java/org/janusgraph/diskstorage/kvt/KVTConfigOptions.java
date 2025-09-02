package org.janusgraph.diskstorage.kvt;

import org.janusgraph.diskstorage.configuration.ConfigNamespace;
import org.janusgraph.diskstorage.configuration.ConfigOption;
import org.janusgraph.graphdb.configuration.GraphDatabaseConfiguration;

public class KVTConfigOptions {

    public static final ConfigNamespace KVT_NS = new ConfigNamespace(
            GraphDatabaseConfiguration.STORAGE_NS,
            "kvt",
            "Configuration options for the KVT (Key-Value Transaction) storage backend. " +
            "KVT provides a high-performance, transactional, distributed key-value store " +
            "with full ACID properties and pessimistic locking (2PL)."
    );

    // Add any KVT-specific configuration options here
    // For now, KVT uses default configurations
    
}