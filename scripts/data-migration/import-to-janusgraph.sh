#!/bin/bash
# JanusGraph Data Import Script
# Imports GraphSON data into JanusGraph with Yugabyte backend

set -e

# Configuration
YUGABYTE_HOST="${YUGABYTE_HOST:-localhost}"
YUGABYTE_PORT="${YUGABYTE_PORT:-9042}"
GRAPH_ID="${GRAPH_ID:-domain}"
INPUT_FILE="${INPUT_FILE:-./neo4j-export/graph-export.json}"
BATCH_SIZE="${BATCH_SIZE:-500}"

echo "============================================"
echo "JanusGraph Data Import Script"
echo "============================================"
echo "Yugabyte Host: $YUGABYTE_HOST:$YUGABYTE_PORT"
echo "Graph ID: $GRAPH_ID"
echo "Input File: $INPUT_FILE"
echo "Batch Size: $BATCH_SIZE"
echo "============================================"

# Check if input file exists
if [ ! -f "$INPUT_FILE" ]; then
    echo "ERROR: Input file not found: $INPUT_FILE"
    exit 1
fi

# Parse GraphSON and count vertices/edges
VERTEX_COUNT=$(jq '.vertices | length' "$INPUT_FILE")
EDGE_COUNT=$(jq '.edges | length' "$INPUT_FILE")

echo "[1/5] Data Statistics"
echo "  Vertices to import: $VERTEX_COUNT"
echo "  Edges to import: $EDGE_COUNT"
echo "  Estimated time: $(( (VERTEX_COUNT + EDGE_COUNT) / 100 )) seconds"

# Test Yugabyte connectivity
echo "[2/5] Testing Yugabyte connectivity..."
cqlsh "$YUGABYTE_HOST" "$YUGABYTE_PORT" -e "DESCRIBE KEYSPACES" > /dev/null 2>&1 \
    || { echo "ERROR: Cannot connect to Yugabyte at $YUGABYTE_HOST:$YUGABYTE_PORT"; exit 1; }
echo "✓ Connected to Yugabyte"

# Create Java import program
echo "[3/5] Creating import program..."
IMPORT_DIR="./target/data-import"
mkdir -p "$IMPORT_DIR"

cat > "$IMPORT_DIR/DataImporter.java" << 'EOF'
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.janusgraph.core.JanusGraph;
import org.sunbird.graph.service.util.JanusGraphDriverUtil;
import org.sunbird.graph.dac.util.GremlinOperationsUtil;
import org.sunbird.graph.dac.model.Node;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.util.*;

public class DataImporter {
    
    public static void main(String[] args) throws Exception {
        String graphId = System.getProperty("graphId", "domain");
        String inputFile = System.getProperty("inputFile", "./neo4j-export/graph-export.json");
        int batchSize = Integer.parseInt(System.getProperty("batchSize", "500"));
        
        System.out.println("Starting import for graph: " + graphId);
        System.out.println("Input file: " + inputFile);
        System.out.println("Batch size: " + batchSize);
        
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(new File(inputFile));
        
        GraphTraversalSource g = JanusGraphDriverUtil.getTraversal(graphId);
        
        // Import vertices
        JsonNode vertices = root.get("vertices");
        System.out.println("\nImporting " + vertices.size() + " vertices...");
        
        int vertexCount = 0;
        for (JsonNode vertex : vertices) {
            try {
                String label = vertex.get("@value").get("label").asText();
                JsonNode properties = vertex.get("@value").get("properties");
                
                // Create vertex
                org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal traversal = g.addV(label);
                
                // Add properties
                if (properties != null) {
                    Iterator<Map.Entry<String, JsonNode>> fields = properties.fields();
                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> field = fields.next();
                        String propName = field.getKey();
                        JsonNode propArray = field.getValue();
                        
                        if (propArray.isArray() && propArray.size() > 0) {
                            JsonNode propValue = propArray.get(0).get("@value").get("value");
                            
                            if (propValue.isInt()) {
                                traversal.property(propName, propValue.asInt());
                            } else if (propValue.isLong()) {
                                traversal.property(propName, propValue.asLong());
                            } else if (propValue.isDouble()) {
                                traversal.property(propName, propValue.asDouble());
                            } else if (propValue.isBoolean()) {
                                traversal.property(propName, propValue.asBoolean());
                            } else {
                                traversal.property(propName, propValue.asText());
                            }
                        }
                    }
                }
                
                traversal.next();
                vertexCount++;
                
                // Commit batch
                if (vertexCount % batchSize == 0) {
                    g.tx().commit();
                    System.out.println("  Imported " + vertexCount + " vertices");
                }
                
            } catch (Exception e) {
                System.err.println("Error importing vertex: " + e.getMessage());
            }
        }
        
        g.tx().commit();
        System.out.println("✓ Imported " + vertexCount + " vertices");
        
        // Import edges
        JsonNode edges = root.get("edges");
        System.out.println("\nImporting " + edges.size() + " edges...");
        
        int edgeCount = 0;
        for (JsonNode edge : edges) {
            try {
                String label = edge.get("@value").get("label").asText();
                long outV = edge.get("@value").get("outV").get("@value").asLong();
                long inV = edge.get("@value").get("inV").get("@value").asLong();
                
                // Create edge
                org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal edgeTraversal = 
                    g.V(outV).addE(label).to(g.V(inV));
                
                // Add edge properties
                JsonNode properties = edge.get("@value").get("properties");
                if (properties != null) {
                    Iterator<Map.Entry<String, JsonNode>> fields = properties.fields();
                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> field = fields.next();
                        String propName = field.getKey();
                        JsonNode propValue = field.getValue();
                        
                        if (propValue.isInt()) {
                            edgeTraversal.property(propName, propValue.asInt());
                        } else if (propValue.isLong()) {
                            edgeTraversal.property(propName, propValue.asLong());
                        } else if (propValue.isDouble()) {
                            edgeTraversal.property(propName, propValue.asDouble());
                        } else if (propValue.isBoolean()) {
                            edgeTraversal.property(propName, propValue.asBoolean());
                        } else {
                            edgeTraversal.property(propName, propValue.asText());
                        }
                    }
                }
                
                edgeTraversal.next();
                edgeCount++;
                
                // Commit batch
                if (edgeCount % batchSize == 0) {
                    g.tx().commit();
                    System.out.println("  Imported " + edgeCount + " edges");
                }
                
            } catch (Exception e) {
                System.err.println("Error importing edge: " + e.getMessage());
            }
        }
        
        g.tx().commit();
        System.out.println("✓ Imported " + edgeCount + " edges");
        
        System.out.println("\n============================================");
        System.out.println("Import Summary");
        System.out.println("============================================");
        System.out.println("Status: SUCCESS");
        System.out.println("Vertices imported: " + vertexCount);
        System.out.println("Edges imported: " + edgeCount);
        System.out.println("============================================");
        
        JanusGraphDriverUtil.closeAllGraphs();
    }
}
EOF

echo "✓ Import program created"

# Compile and run import (would need Maven project setup)
echo "[4/5] Running import..."
echo "NOTE: This requires the knowledge platform to be built first"
echo "Run: mvn clean install -DskipTests in the project root"
echo ""
echo "Then run the import with:"
echo "  mvn exec:java -Dexec.mainClass=\"DataImporter\" \\"
echo "    -DgraphId=$GRAPH_ID \\"
echo "    -DinputFile=$INPUT_FILE \\"
echo "    -DbatchSize=$BATCH_SIZE"

# Validation
echo "[5/5] Post-import validation"
echo "After import completes, validate with:"
echo "  bash scripts/data-migration/validate-import.sh"

echo ""
echo "============================================"
echo "Import script prepared"
echo "============================================"
echo "Follow the instructions above to complete the import"
echo "============================================"
