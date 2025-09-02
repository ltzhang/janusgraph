#!/usr/bin/env python3
"""
Simple social network graph example using JanusGraph with KVT backend
"""

from gremlin_python.driver import client, serializer
from gremlin_python.driver.driver_remote_connection import DriverRemoteConnection
from gremlin_python.process.anonymous_traversal import traversal
from gremlin_python.process.graph_traversal import __
from gremlin_python.structure.graph import Graph
import sys

def create_social_network():
    """Create a simple social network graph"""
    
    # Connect to JanusGraph server
    print("Connecting to JanusGraph server...")
    connection = DriverRemoteConnection('ws://localhost:8182/gremlin', 'g')
    g = traversal().withRemote(connection)
    
    try:
        # Clear existing data (be careful in production!)
        print("Clearing existing data...")
        g.V().drop().iterate()
        
        # Create people (vertices)
        print("Creating people...")
        alice = g.addV('person').property('name', 'Alice').property('age', 28).property('city', 'New York').next()
        bob = g.addV('person').property('name', 'Bob').property('age', 32).property('city', 'Boston').next()
        charlie = g.addV('person').property('name', 'Charlie').property('age', 24).property('city', 'Chicago').next()
        david = g.addV('person').property('name', 'David').property('age', 29).property('city', 'Denver').next()
        eve = g.addV('person').property('name', 'Eve').property('age', 26).property('city', 'New York').next()
        
        # Create relationships (edges)
        print("Creating relationships...")
        g.V(alice).addE('knows').to(bob).property('since', 2018).iterate()
        g.V(alice).addE('knows').to(charlie).property('since', 2020).iterate()
        g.V(bob).addE('knows').to(charlie).property('since', 2019).iterate()
        g.V(charlie).addE('knows').to(david).property('since', 2021).iterate()
        g.V(david).addE('knows').to(eve).property('since', 2022).iterate()
        g.V(eve).addE('knows').to(alice).property('since', 2017).iterate()
        
        print("Social network created successfully!\n")
        
        # Run some queries
        print("=== Query Examples ===\n")
        
        # 1. Find all people
        print("All people in the network:")
        people = g.V().hasLabel('person').values('name').toList()
        for person in people:
            print(f"  - {person}")
        
        # 2. Find Alice's friends
        print("\nAlice's friends:")
        friends = g.V().has('person', 'name', 'Alice').out('knows').values('name').toList()
        for friend in friends:
            print(f"  - {friend}")
        
        # 3. Find people in New York
        print("\nPeople in New York:")
        ny_people = g.V().has('person', 'city', 'New York').values('name').toList()
        for person in ny_people:
            print(f"  - {person}")
        
        # 4. Find friends of friends of Alice (2 hops)
        print("\nFriends of friends of Alice:")
        fof = g.V().has('person', 'name', 'Alice').out('knows').out('knows').dedup().values('name').toList()
        for person in fof:
            print(f"  - {person}")
        
        # 5. Count relationships per person
        print("\nNumber of connections per person:")
        counts = g.V().hasLabel('person').project('name', 'connections').by('name').by(__.bothE('knows').count()).toList()
        for item in counts:
            print(f"  - {item['name']}: {item['connections']} connections")
        
        # 6. Find shortest path between Alice and David
        print("\nShortest path from Alice to David:")
        path = g.V().has('person', 'name', 'Alice').repeat(__.out('knows').simplePath()).until(__.has('name', 'David')).path().by('name').toList()
        if path:
            print(f"  Path: {' -> '.join(path[0])}")
        
    finally:
        connection.close()
        print("\nConnection closed.")

def benchmark_operations(num_vertices=1000, num_edges=5000):
    """Run benchmark operations"""
    import time
    
    print(f"\n=== Running Benchmark ===")
    print(f"Creating {num_vertices} vertices and {num_edges} edges...\n")
    
    connection = DriverRemoteConnection('ws://localhost:8182/gremlin', 'g')
    g = traversal().withRemote(connection)
    
    try:
        # Clear existing data
        g.V().drop().iterate()
        
        # Benchmark vertex creation
        start = time.time()
        vertex_ids = []
        for i in range(num_vertices):
            v = g.addV('user').property('id', i).property('name', f'User{i}').next()
            vertex_ids.append(v)
            if (i + 1) % 100 == 0:
                print(f"  Created {i + 1} vertices...")
        vertex_time = time.time() - start
        print(f"Vertex creation: {vertex_time:.2f} seconds ({num_vertices/vertex_time:.0f} vertices/sec)")
        
        # Benchmark edge creation
        import random
        start = time.time()
        for i in range(num_edges):
            v1 = vertex_ids[random.randint(0, num_vertices-1)]
            v2 = vertex_ids[random.randint(0, num_vertices-1)]
            if v1 != v2:
                g.V(v1).addE('follows').to(v2).iterate()
            if (i + 1) % 500 == 0:
                print(f"  Created {i + 1} edges...")
        edge_time = time.time() - start
        print(f"Edge creation: {edge_time:.2f} seconds ({num_edges/edge_time:.0f} edges/sec)")
        
        # Benchmark traversal queries
        print("\nRunning traversal benchmarks...")
        
        # 1-hop traversal
        start = time.time()
        for i in range(10):
            g.V().has('user', 'id', i).out('follows').count().next()
        one_hop_time = (time.time() - start) / 10
        print(f"1-hop traversal: {one_hop_time*1000:.2f} ms average")
        
        # 2-hop traversal
        start = time.time()
        for i in range(10):
            g.V().has('user', 'id', i).out('follows').out('follows').dedup().count().next()
        two_hop_time = (time.time() - start) / 10
        print(f"2-hop traversal: {two_hop_time*1000:.2f} ms average")
        
        # Vertex lookup
        start = time.time()
        for i in range(100):
            g.V().has('user', 'id', i).next()
        lookup_time = (time.time() - start) / 100
        print(f"Vertex lookup: {lookup_time*1000:.2f} ms average")
        
    finally:
        connection.close()

if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description='JanusGraph KVT Example')
    parser.add_argument('--benchmark', action='store_true', help='Run benchmark instead of example')
    parser.add_argument('--vertices', type=int, default=1000, help='Number of vertices for benchmark')
    parser.add_argument('--edges', type=int, default=5000, help='Number of edges for benchmark')
    
    args = parser.parse_args()
    
    if args.benchmark:
        benchmark_operations(args.vertices, args.edges)
    else:
        create_social_network()