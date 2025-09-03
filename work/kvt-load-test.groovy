try {
    graph = JanusGraphFactory.build().
        set('storage.backend', 'org.janusgraph.diskstorage.kvt.KVTStoreManager').
        set('storage.directory', '/tmp/kvt-verify').
        open()
    println "✓ KVT backend loaded successfully"
    graph.close()
} catch (Exception e) {
    println "✗ Failed to load KVT backend: " + e.getMessage()
    System.exit(1)
}
