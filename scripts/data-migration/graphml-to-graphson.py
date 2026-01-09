#!/usr/bin/env python3
"""
GraphML to GraphSON Converter
Converts Neo4j GraphML export to JanusGraph-compatible GraphSON format
"""

import xml.etree.ElementTree as ET
import json
import sys
import os
from datetime import datetime

# Configuration
INPUT_FILE = os.getenv('INPUT_FILE', './neo4j-export/graph-export.graphml')
OUTPUT_FILE = os.getenv('OUTPUT_FILE', './neo4j-export/graph-export.json')

def parse_graphml(graphml_file):
    """Parse GraphML file and extract nodes and edges"""
    print(f"[1/3] Parsing GraphML file: {graphml_file}")
    
    tree = ET.parse(graphml_file)
    root = tree.getroot()
    
    # GraphML namespace
    ns = {'g': 'http://graphml.graphdrawing.org/xmlns'}
    
    # Extract key definitions
    keys = {}
    for key in root.findall('g:key', ns):
        key_id = key.get('id')
        key_name = key.get('attr.name')
        key_type = key.get('attr.type', 'string')
        keys[key_id] = {'name': key_name, 'type': key_type}
    
    # Extract graph
    graph = root.find('g:graph', ns)
    
    # Extract nodes
    nodes = []
    for node in graph.findall('g:node', ns):
        node_id = node.get('id')
        node_data = {'id': node_id, 'properties': {}}
        
        # Get node labels
        labels = node.get('labels', '').split(':')
        labels = [l for l in labels if l]  # Remove empty strings
        if labels:
            node_data['label'] = labels[0]  # Use first label as primary
        
        # Get node properties
        for data in node.findall('g:data', ns):
            key_id = data.get('key')
            value = data.text
            
            if key_id in keys:
                prop_name = keys[key_id]['name']
                prop_type = keys[key_id]['type']
                
                # Type conversion
                if value is not None:
                    if prop_type == 'int':
                        value = int(value)
                    elif prop_type == 'long':
                        value = int(value)
                    elif prop_type == 'double':
                        value = float(value)
                    elif prop_type == 'boolean':
                        value = value.lower() == 'true'
                
                node_data['properties'][prop_name] = value
        
        nodes.append(node_data)
    
    # Extract edges
    edges = []
    for edge in graph.findall('g:edge', ns):
        edge_id = edge.get('id')
        source = edge.get('source')
        target = edge.get('target')
        label = edge.get('label', 'RELATED')
        
        edge_data = {
            'id': edge_id,
            'source': source,
            'target': target,
            'label': label,
            'properties': {}
        }
        
        # Get edge properties
        for data in edge.findall('g:data', ns):
            key_id = data.get('key')
            value = data.text
            
            if key_id in keys:
                prop_name = keys[key_id]['name']
                prop_type = keys[key_id]['type']
                
                # Type conversion
                if value is not None:
                    if prop_type == 'int':
                        value = int(value)
                    elif prop_type == 'long':
                        value = int(value)
                    elif prop_type == 'double':
                        value = float(value)
                    elif prop_type == 'boolean':
                        value = value.lower() == 'true'
                
                edge_data['properties'][prop_name] = value
        
        edges.append(edge_data)
    
    print(f"  ✓ Found {len(nodes)} nodes")
    print(f"  ✓ Found {len(edges)} edges")
    
    return nodes, edges

def convert_to_graphson(nodes, edges):
    """Convert nodes and edges to GraphSON format"""
    print("[2/3] Converting to GraphSON format...")
    
    graphson = {
        'vertices': [],
        'edges': []
    }
    
    # Convert nodes to vertices
    for node in nodes:
        vertex = {
            '@type': 'g:Vertex',
            '@value': {
                'id': {'@type': 'g:Int64', '@value': hash(node['id']) & 0x7FFFFFFFFFFFFFFF},
                'label': node.get('label', 'vertex')
            }
        }
        
        # Add properties
        properties = {}
        for prop_name, prop_value in node['properties'].items():
            # JanusGraph format: property name -> array of property objects
            prop_obj = {
                '@type': 'g:VertexProperty',
                '@value': {
                    'id': {'@type': 'g:Int64', '@value': hash(f"{node['id']}_{prop_name}") & 0x7FFFFFFFFFFFFFFF},
                    'value': prop_value
                }
            }
            properties[prop_name] = [prop_obj]
        
        vertex['@value']['properties'] = properties
        graphson['vertices'].append(vertex)
    
    # Convert edges
    for edge in edges:
        edge_obj = {
            '@type': 'g:Edge',
            '@value': {
                'id': {'@type': 'g:Int64', '@value': hash(edge['id']) & 0x7FFFFFFFFFFFFFFF},
                'label': edge['label'],
                'inVLabel': 'vertex',
                'outVLabel': 'vertex',
                'inV': {'@type': 'g:Int64', '@value': hash(edge['target']) & 0x7FFFFFFFFFFFFFFF},
                'outV': {'@type': 'g:Int64', '@value': hash(edge['source']) & 0x7FFFFFFFFFFFFFFF}
            }
        }
        
        # Add edge properties
        if edge['properties']:
            edge_obj['@value']['properties'] = edge['properties']
        
        graphson['edges'].append(edge_obj)
    
    print(f"  ✓ Converted {len(graphson['vertices'])} vertices")
    print(f"  ✓ Converted {len(graphson['edges'])} edges")
    
    return graphson

def save_graphson(graphson, output_file):
    """Save GraphSON to file"""
    print(f"[3/3] Saving GraphSON to: {output_file}")
    
    with open(output_file, 'w') as f:
        json.dump(graphson, f, indent=2)
    
    file_size = os.path.getsize(output_file)
    print(f"  ✓ File size: {file_size / (1024*1024):.2f} MB")

def main():
    print("=" * 60)
    print("GraphML to GraphSON Converter")
    print("=" * 60)
    print(f"Input: {INPUT_FILE}")
    print(f"Output: {OUTPUT_FILE}")
    print("=" * 60)
    
    # Check input file
    if not os.path.exists(INPUT_FILE):
        print(f"ERROR: Input file not found: {INPUT_FILE}")
        sys.exit(1)
    
    try:
        # Parse GraphML
        nodes, edges = parse_graphml(INPUT_FILE)
        
        # Convert to GraphSON
        graphson = convert_to_graphson(nodes, edges)
        
        # Save output
        save_graphson(graphson, OUTPUT_FILE)
        
        # Create metadata
        metadata_file = OUTPUT_FILE.replace('.json', '-metadata.json')
        metadata = {
            'conversionDate': datetime.utcnow().isoformat() + 'Z',
            'inputFile': INPUT_FILE,
            'outputFile': OUTPUT_FILE,
            'vertexCount': len(graphson['vertices']),
            'edgeCount': len(graphson['edges'])
        }
        
        with open(metadata_file, 'w') as f:
            json.dump(metadata, f, indent=2)
        
        print("")
        print("=" * 60)
        print("Conversion Summary")
        print("=" * 60)
        print(f"Status: SUCCESS")
        print(f"Vertices: {len(graphson['vertices'])}")
        print(f"Edges: {len(graphson['edges'])}")
        print(f"Output: {OUTPUT_FILE}")
        print(f"Metadata: {metadata_file}")
        print("=" * 60)
        print("")
        print("Next step:")
        print("  bash scripts/data-migration/import-to-janusgraph.sh")
        print("=" * 60)
        
    except Exception as e:
        print(f"ERROR: Conversion failed: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)

if __name__ == '__main__':
    main()
