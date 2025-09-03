import org.janusgraph.core.JanusGraphFactory

try {
    def graph = JanusGraphFactory.build()
        .set('storage.backend', 'org.janusgraph.diskstorage.kvt.KVTStoreManager')
        .set('storage.directory', '/tmp/janusgraph-kvt-test')
        .open()
    println("Success: Graph opened with KVT backend")
    graph.close()
} catch (Exception e) {
    println("Error: " + e.getMessage())
    e.printStackTrace()
}
