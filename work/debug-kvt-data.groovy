// Enable debug logging for KVT
import org.apache.log4j.Level
import org.apache.log4j.Logger

Logger.getLogger("org.janusgraph.diskstorage.kvt").setLevel(Level.DEBUG)

println "=== KVT Debug Test with Logging ==="
println ""

graph = JanusGraphFactory.build().
    set('storage.backend', 'org.janusgraph.diskstorage.kvt.KVTStoreManager').
    set('storage.directory', '/tmp/kvt-debug-data').
    open()

println "Graph opened"

// Add a simple vertex
println "\n=== Adding vertex ==="
v = graph.addVertex()
println "Created vertex: " + v.id()
graph.tx().commit()
println "Committed"

// Try to query it back
println "\n=== Querying vertex ==="
g = graph.traversal()
try {
    count = g.V().count().next()
    println "Found " + count + " vertices"
} catch (Exception e) {
    println "Error during query: " + e.getMessage()
}

graph.close()
println "\nDone"