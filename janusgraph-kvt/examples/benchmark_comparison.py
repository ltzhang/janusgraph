#!/usr/bin/env python3
"""
Benchmark comparison between KVT and InMemory backends
"""

import time
import subprocess
import json
from gremlin_python.driver.driver_remote_connection import DriverRemoteConnection
from gremlin_python.process.anonymous_traversal import traversal
import matplotlib.pyplot as plt
import pandas as pd

def start_server(config_file):
    """Start JanusGraph server with specified configuration"""
    print(f"Starting server with {config_file}...")
    process = subprocess.Popen([
        f"{os.environ['JANUSGRAPH_HOME']}/bin/gremlin-server.sh",
        config_file
    ])
    time.sleep(10)  # Wait for server to start
    return process

def stop_server(process):
    """Stop JanusGraph server"""
    process.terminate()
    time.sleep(5)

def benchmark_backend(backend_name, num_vertices=1000, num_edges=5000):
    """Run benchmark on specified backend"""
    print(f"\nBenchmarking {backend_name} backend...")
    
    connection = DriverRemoteConnection('ws://localhost:8182/gremlin', 'g')
    g = traversal().withRemote(connection)
    
    results = {}
    
    try:
        # Clear existing data
        g.V().drop().iterate()
        
        # Benchmark vertex creation
        start = time.time()
        vertex_ids = []
        for i in range(num_vertices):
            v = g.addV('user').property('id', i).property('name', f'User{i}').next()
            vertex_ids.append(v)
        results['vertex_creation'] = time.time() - start
        
        # Benchmark edge creation
        import random
        start = time.time()
        for i in range(num_edges):
            v1 = vertex_ids[random.randint(0, num_vertices-1)]
            v2 = vertex_ids[random.randint(0, num_vertices-1)]
            if v1 != v2:
                g.V(v1).addE('follows').to(v2).iterate()
        results['edge_creation'] = time.time() - start
        
        # Benchmark 1-hop traversal
        times = []
        for i in range(10):
            start = time.time()
            g.V().has('user', 'id', i).out('follows').count().next()
            times.append(time.time() - start)
        results['1_hop_traversal'] = sum(times) / len(times)
        
        # Benchmark 2-hop traversal
        times = []
        for i in range(10):
            start = time.time()
            g.V().has('user', 'id', i).out('follows').out('follows').dedup().count().next()
            times.append(time.time() - start)
        results['2_hop_traversal'] = sum(times) / len(times)
        
        # Benchmark vertex lookup
        times = []
        for i in range(100):
            start = time.time()
            g.V().has('user', 'id', i).next()
            times.append(time.time() - start)
        results['vertex_lookup'] = sum(times) / len(times)
        
    finally:
        connection.close()
    
    return results

def plot_results(results):
    """Create comparison charts"""
    backends = list(results.keys())
    metrics = list(results[backends[0]].keys())
    
    # Create bar chart
    fig, axes = plt.subplots(2, 3, figsize=(15, 10))
    axes = axes.flatten()
    
    for i, metric in enumerate(metrics):
        values = [results[backend][metric] for backend in backends]
        axes[i].bar(backends, values)
        axes[i].set_title(metric.replace('_', ' ').title())
        axes[i].set_ylabel('Time (seconds)')
        
    plt.tight_layout()
    plt.savefig('benchmark_comparison.png')
    print("\nResults saved to benchmark_comparison.png")
    
    # Create results table
    df = pd.DataFrame(results).T
    print("\n=== Benchmark Results ===")
    print(df.to_string())
    df.to_csv('benchmark_results.csv')
    print("\nResults saved to benchmark_results.csv")

if __name__ == "__main__":
    import os
    import argparse
    
    parser = argparse.ArgumentParser(description='Compare JanusGraph backends')
    parser.add_argument('--vertices', type=int, default=1000, help='Number of vertices')
    parser.add_argument('--edges', type=int, default=5000, help='Number of edges')
    parser.add_argument('--server', action='store_true', help='Use server mode (requires manual server start)')
    
    args = parser.parse_args()
    
    if not os.environ.get('JANUSGRAPH_HOME'):
        print("Error: JANUSGRAPH_HOME not set!")
        exit(1)
    
    results = {}
    
    if args.server:
        print("Using server mode - ensure servers are started manually")
        
        # Test KVT composite
        input("Start server with KVT composite config and press Enter...")
        results['KVT-Composite'] = benchmark_backend('KVT-Composite', args.vertices, args.edges)
        
        # Test KVT serialized
        input("Stop server, start with KVT serialized config and press Enter...")
        results['KVT-Serialized'] = benchmark_backend('KVT-Serialized', args.vertices, args.edges)
        
        # Test InMemory
        input("Stop server, start with InMemory config and press Enter...")
        results['InMemory'] = benchmark_backend('InMemory', args.vertices, args.edges)
        
    else:
        print("Note: Embedded mode benchmarking requires JanusGraph configuration")
        print("Please use --server mode for easier testing")
        exit(1)
    
    plot_results(results)