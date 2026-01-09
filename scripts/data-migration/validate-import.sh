#!/bin/bash
# Data Import Validation Script
# Validates that data was correctly migrated from Neo4j to JanusGraph

set -e

# Configuration
NEO4J_HOST="${NEO4J_HOST:-localhost}"
NEO4J_PORT="${NEO4J_PORT:-7687}"
NEO4J_USER="${NEO4J_USER:-neo4j}"
NEO4J_PASSWORD="${NEO4J_PASSWORD:-password}"
YUGABYTE_HOST="${YUGABYTE_HOST:-localhost}"
YUGABYTE_PORT="${YUGABYTE_PORT:-9042}"
GRAPH_ID="${GRAPH_ID:-domain}"
SAMPLE_SIZE="${SAMPLE_SIZE:-100}"

echo "============================================"
echo "Data Migration Validation Script"
echo "============================================"
echo "Neo4j: $NEO4J_HOST:$NEO4J_PORT"
echo "Yugabyte: $YUGABYTE_HOST:$YUGABYTE_PORT"
echo "Graph ID: $GRAPH_ID"
echo "Sample Size: $SAMPLE_SIZE"
echo "============================================"

# Count comparison
echo ""
echo "[1/4] Comparing node/vertex counts..."

NEO4J_NODE_COUNT=$(cypher-shell -a "bolt://$NEO4J_HOST:$NEO4J_PORT" \
    -u "$NEO4J_USER" -p "$NEO4J_PASSWORD" \
    "MATCH (n) RETURN count(n) as count" --format plain | tail -1)

echo "  Neo4j nodes: $NEO4J_NODE_COUNT"

# JanusGraph count (requires Java/Gremlin)
echo "  JanusGraph vertices: Checking..."
echo "  (Run validation Java program for accurate count)"

# Relationship/edge count comparison
echo ""
echo "[2/4] Comparing relationship/edge counts..."

NEO4J_REL_COUNT=$(cypher-shell -a "bolt://$NEO4J_HOST:$NEO4J_PORT" \
    -u "$NEO4J_USER" -p "$NEO4J_PASSWORD" \
    "MATCH ()-[r]->() RETURN count(r) as count" --format plain | tail -1)

echo "  Neo4j relationships: $NEO4J_REL_COUNT"
echo "  JanusGraph edges: Checking..."
echo "  (Run validation Java program for accurate count)"

# Label distribution
echo ""
echo "[3/4] Comparing label distributions..."

echo "Neo4j labels:"
cypher-shell -a "bolt://$NEO4J_HOST:$NEO4J_PORT" \
    -u "$NEO4J_USER" -p "$NEO4J_PASSWORD" \
    "MATCH (n) RETURN labels(n)[0] as label, count(*) as count ORDER BY count DESC LIMIT 20" \
    --format plain

echo ""
echo "JanusGraph labels: (Run validation Java program)"

# Sample node comparison
echo ""
echo "[4/4] Random sample validation..."
echo "Sampling $SAMPLE_SIZE random nodes for property comparison..."

# Get sample node IDs from Neo4j
SAMPLE_IDS=$(cypher-shell -a "bolt://$NEO4J_HOST:$NEO4J_PORT" \
    -u "$NEO4J_USER" -p "$NEO4J_PASSWORD" \
    "MATCH (n) WHERE exists(n.IL_UNIQUE_ID) RETURN n.IL_UNIQUE_ID as id LIMIT $SAMPLE_SIZE" \
    --format plain | tail -n +2)

echo "Sample IDs extracted: $(echo "$SAMPLE_IDS" | wc -l) nodes"
echo ""
echo "Sample IDs:"
echo "$SAMPLE_IDS" | head -10
echo "..."

# Create Java validation program
echo ""
echo "============================================"
echo "Creating detailed validation program..."
echo "============================================"

VALIDATION_DIR="./target/data-validation"
mkdir -p "$VALIDATION_DIR"

cat > "$VALIDATION_DIR/ValidationReport.java" << 'EOF'
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.janusgraph.core.JanusGraph;
import org.sunbird.graph.service.util.JanusGraphDriverUtil;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.*;

public class ValidationReport {
    
    public static void main(String[] args) throws Exception {
        String graphId = System.getProperty("graphId", "domain");
        String reportFile = System.getProperty("reportFile", "./validation-report.txt");
        
        System.out.println("Generating validation report for graph: " + graphId);
        
        PrintWriter writer = new PrintWriter(new FileWriter(reportFile));
        writer.println("=".repeat(60));
        writer.println("JanusGraph Data Validation Report");
        writer.println("=".repeat(60));
        writer.println("Graph ID: " + graphId);
        writer.println("Timestamp: " + new Date());
        writer.println("=".repeat(60));
        writer.println();
        
        GraphTraversalSource g = JanusGraphDriverUtil.getTraversal(graphId);
        
        // Count vertices
        long vertexCount = g.V().count().next();
        writer.println("Total Vertices: " + vertexCount);
        System.out.println("✓ Vertices: " + vertexCount);
        
        // Count edges
        long edgeCount = g.E().count().next();
        writer.println("Total Edges: " + edgeCount);
        System.out.println("✓ Edges: " + edgeCount);
        writer.println();
        
        // Label distribution
        writer.println("Vertex Label Distribution:");
        writer.println("-".repeat(60));
        
        Map<String, Long> labelCounts = new HashMap<>();
        g.V().label().groupCount().next().forEach((label, count) -> {
            labelCounts.put((String)label, (Long)count);
            writer.println(String.format("  %-30s %,10d", label, count));
        });
        
        System.out.println("✓ Found " + labelCounts.size() + " unique labels");
        writer.println();
        
        // Edge label distribution
        writer.println("Edge Label Distribution:");
        writer.println("-".repeat(60));
        
        g.E().label().groupCount().next().forEach((label, count) -> {
            writer.println(String.format("  %-30s %,10d", label, count));
        });
        writer.println();
        
        // Property statistics
        writer.println("Property Statistics:");
        writer.println("-".repeat(60));
        
        Set<String> allProperties = new HashSet<>();
        g.V().properties().key().dedup().forEachRemaining(key -> allProperties.add((String)key));
        
        writer.println("Total unique properties: " + allProperties.size());
        for (String prop : allProperties) {
            long propCount = g.V().has(prop).count().next();
            writer.println(String.format("  %-30s %,10d vertices", prop, propCount));
        }
        
        System.out.println("✓ Found " + allProperties.size() + " unique properties");
        writer.println();
        
        // Sample node validation
        writer.println("Sample Node Validation (first 10):");
        writer.println("-".repeat(60));
        
        g.V().limit(10).forEachRemaining(vertex -> {
            writer.println("Vertex ID: " + vertex.id());
            writer.println("  Label: " + vertex.label());
            writer.println("  Properties:");
            vertex.properties().forEachRemaining(prop -> {
                writer.println("    " + prop.key() + " = " + prop.value());
            });
            writer.println();
        });
        
        // Graph connectivity metrics
        writer.println("Graph Connectivity Metrics:");
        writer.println("-".repeat(60));
        
        long isolatedVertices = g.V().where(__.bothE().count().is(0)).count().next();
        writer.println("Isolated vertices (no edges): " + isolatedVertices);
        
        double avgDegree = (double)(edgeCount * 2) / vertexCount;
        writer.println("Average vertex degree: " + String.format("%.2f", avgDegree));
        
        System.out.println("✓ Connectivity analysis complete");
        writer.println();
        
        // Summary
        writer.println("=".repeat(60));
        writer.println("Validation Summary");
        writer.println("=".repeat(60));
        writer.println("Total Vertices: " + String.format("%,d", vertexCount));
        writer.println("Total Edges: " + String.format("%,d", edgeCount));
        writer.println("Unique Labels: " + labelCounts.size());
        writer.println("Unique Properties: " + allProperties.size());
        writer.println("Isolated Vertices: " + String.format("%,d (%.2f%%)", 
            isolatedVertices, (isolatedVertices * 100.0 / vertexCount)));
        writer.println("Average Degree: " + String.format("%.2f", avgDegree));
        writer.println("=".repeat(60));
        writer.println();
        writer.println("Report generated successfully!");
        writer.println("Compare these metrics with Neo4j export metadata.");
        writer.println("=".repeat(60));
        
        writer.close();
        JanusGraphDriverUtil.closeAllGraphs();
        
        System.out.println();
        System.out.println("============================================");
        System.out.println("Validation report saved to: " + reportFile);
        System.out.println("============================================");
        System.out.println("Review the report and compare with Neo4j metrics");
        System.out.println("Expected to see:");
        System.out.println("  - Same vertex/node counts");
        System.out.println("  - Same edge/relationship counts");
        System.out.println("  - Same label distributions");
        System.out.println("  - All properties migrated");
        System.out.println("============================================");
    }
}
EOF

echo "✓ Validation program created"
echo ""
echo "To run complete validation:"
echo "  mvn exec:java -Dexec.mainClass=\"ValidationReport\" \\"
echo "    -DgraphId=$GRAPH_ID \\"
echo "    -DreportFile=./validation-report.txt"
echo ""
echo "============================================"
echo "Validation script prepared"
echo "============================================"
echo "Run the commands above to generate detailed report"
echo "============================================"
