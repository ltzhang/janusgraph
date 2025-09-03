// Debug KVT Backend
println "Debug KVT backend operations..."

graph = JanusGraphFactory.build().
    set('storage.backend', 'org.janusgraph.diskstorage.kvt.KVTStoreManager').
    set('storage.directory', '/tmp/kvt-debug-' + System.currentTimeMillis()).
    open()

println "\n=== Adding single vertex ==="
v1 = graph.addVertex()
println "Created vertex with ID: " + v1.id()
graph.tx().commit()
println "Committed transaction"

println "\n=== Querying back ==="
g = graph.traversal()
count = g.V().count().next()
println "Vertex count: " + count

if (count > 0) {
    println "Vertex IDs found:"
    g.V().forEachRemaining { v ->
        println "  - ID: " + v.id()
    }
} else {
    println "No vertices found!"
    
    println "\n=== Trying again with property ==="
    v2 = graph.addVertex("test", "value")
    println "Created vertex with property"
    graph.tx().commit()
    
    g = graph.traversal()
    count2 = g.V().count().next()
    println "New vertex count: " + count2
}

graph.close()
println "\nDone."