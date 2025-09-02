// Create a simple social network graph
// Usage: $JANUSGRAPH_HOME/bin/gremlin.sh -e create_social_graph.groovy

import org.janusgraph.core.JanusGraphFactory

// Load configuration (assumes you have created the config file)
graph = JanusGraphFactory.open('conf/kvt/janusgraph-kvt-server.properties')
g = graph.traversal()

println "Creating social network graph..."

// Create vertices (people)
alice = g.addV('person').property('name', 'Alice').property('age', 28).next()
bob = g.addV('person').property('name', 'Bob').property('age', 32).next()
charlie = g.addV('person').property('name', 'Charlie').property('age', 24).next()
david = g.addV('person').property('name', 'David').property('age', 29).next()

// Create edges (relationships)
g.V(alice).addE('knows').to(bob).property('since', 2018).iterate()
g.V(alice).addE('knows').to(charlie).property('since', 2020).iterate()
g.V(bob).addE('knows').to(charlie).property('since', 2019).iterate()
g.V(charlie).addE('knows').to(david).property('since', 2021).iterate()

// Commit the transaction
graph.tx().commit()

println "Graph created successfully!"

// Some queries
println "\n=== Friends of Alice ==="
g.V().has('name', 'Alice').out('knows').values('name').each { println "  - " + it }

println "\n=== People who know Charlie ==="
g.V().has('name', 'Charlie').in('knows').values('name').each { println "  - " + it }

println "\n=== Friends of friends of Alice ==="
g.V().has('name', 'Alice').out('knows').out('knows').dedup().values('name').each { println "  - " + it }

println "\n=== Count of all vertices ==="
println "  Total vertices: " + g.V().count().next()

println "\n=== Count of all edges ==="
println "  Total edges: " + g.E().count().next()

graph.close()
println "\nDone!"