import org.janusgraph.core.JanusGraphFactory
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.config.Configurator

println "Starting InMemory backend DETAILED test with backend call logging..."
println ""

// Enable detailed logging for inmemory backend
Configurator.setLevel("org.janusgraph.diskstorage.inmemory", Level.TRACE)
Configurator.setLevel("org.janusgraph.diskstorage", Level.DEBUG)
Configurator.setLevel("org.janusgraph.graphdb.transaction", Level.DEBUG)
Configurator.setLevel("org.janusgraph.graphdb.database", Level.DEBUG)

success = true
errors = []

try {
    // Test 1: Can we load the backend?
    println "=========================================="
    println "TEST 1: Loading InMemory backend"
    println "=========================================="
    
    graph = JanusGraphFactory.build().
        set('storage.backend', 'inmemory').
        set('storage.transactions', true).
        set('query.force-index', false).
        open()
    
    println "✓ Backend loaded"
    println ""
    
    // Test 2: Can we add vertices?
    println "=========================================="
    println "TEST 2: Adding vertices"
    println "=========================================="
    
    g = graph.traversal()
    
    println "Creating vertex 1..."
    v1 = graph.addVertex("test", "vertex1")
    println "  Vertex 1 created: id=" + v1.id() + ", test=" + v1.value("test")
    
    println "Creating vertex 2..."
    v2 = graph.addVertex("test", "vertex2")
    println "  Vertex 2 created: id=" + v2.id() + ", test=" + v2.value("test")
    
    println "Committing transaction..."
    graph.tx().commit()
    println "✓ Transaction committed"
    println ""
    
    // Test 3: Can we query vertices?
    println "=========================================="
    println "TEST 3: Querying vertices"
    println "=========================================="
    
    println "Creating new traversal after commit..."
    g = graph.traversal()
    
    println "Counting all vertices with g.V().count()..."
    count = g.V().count().next()
    println "  Count result: " + count
    
    if (count != 2) {
        throw new Exception("Expected 2 vertices, got " + count)
    }
    
    println "Listing all vertices..."
    g.V().each { v ->
        println "  Found vertex: id=" + v.id() + ", properties=" + v.properties().collect { p -> p.key() + "=" + p.value() }
    }
    
    println "✓ Query successful"
    println ""
    
    // Test 4: Can we add edges?
    println "=========================================="
    println "TEST 4: Adding edges"
    println "=========================================="
    
    // Get fresh vertex references after commit
    println "Getting fresh traversal..."
    g = graph.traversal()
    
    println "Finding vertex 1 by property..."
    v1Fresh = g.V().has("test", "vertex1").next()
    println "  Found vertex 1: id=" + v1Fresh.id()
    
    println "Finding vertex 2 by property..."
    v2Fresh = g.V().has("test", "vertex2").next()
    println "  Found vertex 2: id=" + v2Fresh.id()
    
    println "Adding edge from vertex1 to vertex2..."
    edge = v1Fresh.addEdge("connected", v2Fresh, "weight", 1.0d)
    println "  Edge created: id=" + edge.id() + ", label=" + edge.label() + ", weight=" + edge.value("weight")
    
    println "Committing transaction..."
    graph.tx().commit()
    println "✓ Edge added"
    println ""
    
    // Test 5: Can we traverse edges?
    println "=========================================="
    println "TEST 5: Traversing edges"
    println "=========================================="
    
    println "Getting fresh traversal..."
    g = graph.traversal()
    
    println "Counting edges with g.E().count()..."
    edgeCount = g.E().count().next()
    println "  Edge count: " + edgeCount
    
    if (edgeCount != 1) {
        throw new Exception("Expected 1 edge, got " + edgeCount)
    }
    
    println "Getting edge details..."
    edge = g.E().next()
    println "  Edge id: " + edge.id()
    println "  Edge label: " + edge.label()
    
    weight = edge.value("weight")
    println "  Edge weight: " + weight
    
    if (weight != 1.0d) {
        throw new Exception("Expected edge weight 1.0, got " + weight)
    }
    
    println "Traversing from vertex to vertex through edge..."
    g.V().has("test", "vertex1").outE("connected").inV().each { v ->
        println "  Reached vertex: id=" + v.id() + ", test=" + v.value("test")
    }
    
    println "✓ Edge traversal successful"
    println ""
    
    // Test 6: Can we use transactions?
    println "=========================================="
    println "TEST 6: Testing transactions"
    println "=========================================="
    
    println "Creating new transaction..."
    tx = graph.newTransaction()
    
    println "Adding temporary vertex in transaction..."
    tempV = tx.addVertex("temp", "should-not-persist")
    println "  Temporary vertex created: id=" + tempV.id()
    
    println "Rolling back transaction..."
    tx.rollback()
    println "  Transaction rolled back"
    
    println "Verifying rollback with fresh traversal..."
    g = graph.traversal()
    tempCount = g.V().has("temp", "should-not-persist").count().next()
    println "  Vertices with temp property: " + tempCount
    
    if (tempCount != 0) {
        throw new Exception("Transaction rollback failed")
    }
    
    println "✓ Transaction rollback successful"
    println ""
    
    // Final state check
    println "=========================================="
    println "FINAL STATE CHECK"
    println "=========================================="
    
    g = graph.traversal()
    println "Total vertices: " + g.V().count().next()
    println "Total edges: " + g.E().count().next()
    
    println "All vertices:"
    g.V().each { v ->
        props = v.properties().collect { p -> p.key() + "=" + p.value() }.join(", ")
        println "  Vertex id=" + v.id() + ": " + props
    }
    
    println "All edges:"
    g.E().each { e ->
        println "  Edge id=" + e.id() + ", " + e.outVertex().id() + " --[" + e.label() + "]--> " + e.inVertex().id()
    }
    
    // Clean up
    println ""
    println "Closing graph..."
    graph.close()
    println "✓ Graph closed"
    
} catch (Exception e) {
    success = false
    println "✗ TEST FAILED"
    errors.add(e.getMessage())
    println "Error: " + e.getMessage()
    e.printStackTrace()
}

println ""
println "=========================================="
if (success) {
    println "✅ ALL TESTS PASSED"
    println "InMemory backend is working correctly!"
} else {
    println "❌ TESTS FAILED"
    errors.each { println "  - " + it }
}
println "=========================================="

System.exit(success ? 0 : 1)