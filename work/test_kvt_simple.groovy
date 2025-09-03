import org.janusgraph.core.JanusGraphFactory

println "Starting KVT test..."

try {
    // Create a simple in-memory-like configuration 
    def graph = JanusGraphFactory.build()
        .set('storage.backend', 'org.janusgraph.diskstorage.kvt.KVTStoreManager')
        .set('storage.directory', '/tmp/janusgraph-kvt-test')
        .set('storage.kvt.storage-method', 'composite')  // Use composite key method
        .open()
    
    println "SUCCESS: Graph opened with KVT backend!"
    
    // Try a simple operation
    def v = graph.addVertex("name", "Test")
    graph.tx().commit()
    println "Added vertex with name: Test"
    
    // Close the graph
    graph.close()
    println "Graph closed successfully"
    
} catch (Exception e) {
    println "ERROR: " + e.getMessage()
    e.printStackTrace()
}

println "Test complete"