#!/bin/bash
# Neo4j to JanusGraph Data Migration Script
# Exports all data from Neo4j to GraphML format

set -e

# Configuration
NEO4J_HOST="${NEO4J_HOST:-localhost}"
NEO4J_PORT="${NEO4J_PORT:-7687}"
NEO4J_USER="${NEO4J_USER:-neo4j}"
NEO4J_PASSWORD="${NEO4J_PASSWORD:-password}"
OUTPUT_DIR="${OUTPUT_DIR:-./neo4j-export}"
EXPORT_FILE="${OUTPUT_DIR}/graph-export.graphml"

echo "============================================"
echo "Neo4j Data Export Script"
echo "============================================"
echo "Neo4j Host: $NEO4J_HOST:$NEO4J_PORT"
echo "Output Directory: $OUTPUT_DIR"
echo "Export File: $EXPORT_FILE"
echo "============================================"

# Create output directory
mkdir -p "$OUTPUT_DIR"

# Check if APOC plugin is available
echo "[1/4] Checking Neo4j APOC plugin..."
cypher-shell -a "bolt://$NEO4J_HOST:$NEO4J_PORT" \
    -u "$NEO4J_USER" -p "$NEO4J_PASSWORD" \
    "CALL dbms.procedures() YIELD name WHERE name STARTS WITH 'apoc' RETURN count(name) as apocCount" \
    || { echo "ERROR: APOC plugin not found. Please install APOC plugin."; exit 1; }

echo "✓ APOC plugin found"

# Get statistics
echo "[2/4] Gathering Neo4j statistics..."
NODE_COUNT=$(cypher-shell -a "bolt://$NEO4J_HOST:$NEO4J_PORT" \
    -u "$NEO4J_USER" -p "$NEO4J_PASSWORD" \
    "MATCH (n) RETURN count(n) as count" --format plain | tail -1)

REL_COUNT=$(cypher-shell -a "bolt://$NEO4J_HOST:$NEO4J_PORT" \
    -u "$NEO4J_USER" -p "$NEO4J_PASSWORD" \
    "MATCH ()-[r]->() RETURN count(r) as count" --format plain | tail -1)

echo "  Nodes: $NODE_COUNT"
echo "  Relationships: $REL_COUNT"

# Export data using APOC
echo "[3/4] Exporting data to GraphML format..."
START_TIME=$(date +%s)

cypher-shell -a "bolt://$NEO4J_HOST:$NEO4J_PORT" \
    -u "$NEO4J_USER" -p "$NEO4J_PASSWORD" \
    "CALL apoc.export.graphml.all('$EXPORT_FILE', {
        useTypes: true,
        storeNodeIds: true,
        readLabels: true,
        defaultRelationshipType: 'RELATED'
    })"

END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

echo "✓ Export completed in ${DURATION}s"

# Validate export
echo "[4/4] Validating export..."
if [ ! -f "$EXPORT_FILE" ]; then
    echo "ERROR: Export file not found!"
    exit 1
fi

EXPORT_SIZE=$(du -h "$EXPORT_FILE" | cut -f1)
echo "  Export file size: $EXPORT_SIZE"

# Create metadata file
METADATA_FILE="${OUTPUT_DIR}/export-metadata.json"
cat > "$METADATA_FILE" << EOF
{
  "exportDate": "$(date -u +"%Y-%m-%dT%H:%M:%SZ")",
  "neo4jHost": "$NEO4J_HOST:$NEO4J_PORT",
  "nodeCount": $NODE_COUNT,
  "relationshipCount": $REL_COUNT,
  "exportFile": "$EXPORT_FILE",
  "exportSize": "$EXPORT_SIZE",
  "durationSeconds": $DURATION
}
EOF

echo "✓ Metadata saved to $METADATA_FILE"

echo ""
echo "============================================"
echo "Export Summary"
echo "============================================"
echo "Status: SUCCESS"
echo "Nodes exported: $NODE_COUNT"
echo "Relationships exported: $REL_COUNT"
echo "Export file: $EXPORT_FILE"
echo "File size: $EXPORT_SIZE"
echo "Duration: ${DURATION}s"
echo "============================================"
echo ""
echo "Next steps:"
echo "1. Run: python3 scripts/data-migration/graphml-to-graphson.py"
echo "2. Run: bash scripts/data-migration/import-to-janusgraph.sh"
echo "============================================"
