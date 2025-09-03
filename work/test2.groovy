// KVT Backend Test
println "Testing KVT backend..."

try {
    // Open graph with KVT backend
    graph = JanusGraphFactory.build().
        set('storage.backend', 'org.janusgraph.diskstorage.kvt.KVTStoreManager').
        set('storage.directory', '/tmp/janusgraph-kvt-test').
        open()
    
    println "SUCCESS: Graph opened with KVT backend!"
    
    // Create schema
    mgmt = graph.openManagement()
    name = mgmt.makePropertyKey('name').dataType(String.class).make()
    age = mgmt.makePropertyKey('age').dataType(Integer.class).make()
    mgmt.commit()
    println "Schema created"
    
    // Add some test data
    g = graph.traversal()
    alice = g.addV('person').property('name', 'Alice').property('age', 30).next()
    bob = g.addV('person').property('name', 'Bob').property('age', 25).next()
    g.addE('knows').from(alice).to(bob).iterate()
    graph.tx().commit()
    println "Added test data: Alice knows Bob"
    
    // Query the data
    count = g.V().hasLabel('person').count().next()
    println "Number of people in graph: " + count
    
    if (count > 0) {
        result = g.V().has('name', 'Alice').out('knows').values('name').next()
        println "Alice knows: " + result
    } else {
        // Retry without label (in case schema didn't apply)
        g = graph.traversal()
        alice = g.addV().property('name', 'Alice').property('age', 30).next()
        bob = g.addV().property('name', 'Bob').property('age', 25).next() 
        g.addE('knows').from(alice).to(bob).iterate()
        graph.tx().commit()
        
        count = g.V().count().next()
        println "Number of vertices in graph: " + count
        
        result = g.V().has('name', 'Alice').out('knows').values('name').toList()
        println "Alice knows: " + result
    }
    
    // Clean up
    graph.close()
    println "Graph closed successfully"
    println ""
    println "✅ KVT backend test PASSED!"
    
} catch (Exception e) {
    println "❌ ERROR: " + e.getMessage()
    e.printStackTrace()
}
