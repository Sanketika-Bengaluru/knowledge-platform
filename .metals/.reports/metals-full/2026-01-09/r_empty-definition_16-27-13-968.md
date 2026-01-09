error id: file://<WORKSPACE>/ontology-engine/graph-dac-api/src/main/java/org/sunbird/graph/dac/util/GremlinOperationsUtil.java:_empty_/`<any>`#toList#
file://<WORKSPACE>/ontology-engine/graph-dac-api/src/main/java/org/sunbird/graph/dac/util/GremlinOperationsUtil.java
empty definition using pc, found symbol in pc: _empty_/`<any>`#toList#
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 9324
uri: file://<WORKSPACE>/ontology-engine/graph-dac-api/src/main/java/org/sunbird/graph/dac/util/GremlinOperationsUtil.java
text:
```scala
package org.sunbird.graph.dac.util;

import org.apache.commons.lang3.StringUtils;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.T;
import org.janusgraph.core.JanusGraph;
import org.sunbird.common.exception.ServerException;
import org.sunbird.graph.dac.enums.GraphDACErrorCodes;
import org.sunbird.graph.dac.model.Node;
import org.sunbird.graph.dac.model.Relation;
import org.sunbird.graph.service.util.JanusGraphDriverUtil;
import org.sunbird.telemetry.logger.TelemetryManager;

import java.util.*;
import java.util.stream.Collectors;

import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.unfold;

/**
 * GremlinOperationsUtil - Gremlin-based CRUD operations for JanusGraph
 * Provides replacement methods for Neo4j Cypher operations
 *
 * @author Sunbird
 */
public class GremlinOperationsUtil {

    private static final String IL_UNIQUE_ID = "IL_UNIQUE_ID";
    private static final int BATCH_SIZE = 500;

    /**
     * Create a new vertex
     */
    public static Vertex createVertex(String graphId, Node node) {
        GraphTraversalSource g = JanusGraphDriverUtil.getTraversal(graphId);
        
        try {
            Map<String, Object> properties = TinkerpopNodeUtil.prepareVertexProperties(node.getMetadata());
            
            GraphTraversal<Vertex, Vertex> traversal = g.addV(graphId);
            
            // Add system properties
            traversal.property(IL_UNIQUE_ID, node.getIdentifier());
            if (StringUtils.isNotBlank(node.getNodeType())) {
                traversal.property("IL_SYS_NODE_TYPE", node.getNodeType());
            }
            if (StringUtils.isNotBlank(node.getObjectType())) {
                traversal.property("IL_FUNC_OBJECT_TYPE", node.getObjectType());
            }
            
            // Add metadata properties
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                if (entry.getValue() != null) {
                    traversal.property(entry.getKey(), entry.getValue());
                }
            }
            
            Vertex vertex = traversal.next();
            g.tx().commit();
            
            TelemetryManager.log("Created vertex: " + node.getIdentifier());
            return vertex;
            
        } catch (Exception e) {
            g.tx().rollback();
            TelemetryManager.error("Error creating vertex: " + e.getMessage(), e);
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_ADD_NODE.name(),
                    "Error creating vertex: " + e.getMessage(), e);
        }
    }

    /**
     * Upsert vertex - create if not exists, update if exists
     * Implements Cypher MERGE pattern with ON CREATE/ON MATCH logic
     */
    public static Vertex upsertVertex(String graphId, Node node) {
        GraphTraversalSource g = JanusGraphDriverUtil.getTraversal(graphId);
        
        try {
            Map<String, Object> properties = TinkerpopNodeUtil.prepareVertexProperties(node.getMetadata());
            
            // Try to find existing vertex
            GraphTraversal<Vertex, Vertex> findTraversal = g.V()
                    .has(IL_UNIQUE_ID, node.getIdentifier())
                    .fold()
                    .coalesce(
                        unfold(),
                        __.addV(graphId).property(IL_UNIQUE_ID, node.getIdentifier())
                    );
            
            Vertex vertex = findTraversal.next();
            boolean isNew = !vertex.property("createdOn").isPresent();
            
            // Update system properties
            if (StringUtils.isNotBlank(node.getNodeType())) {
                vertex.property("IL_SYS_NODE_TYPE", node.getNodeType());
            }
            if (StringUtils.isNotBlank(node.getObjectType())) {
                vertex.property("IL_FUNC_OBJECT_TYPE", node.getObjectType());
            }
            
            // Update metadata properties
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                if (entry.getValue() != null) {
                    vertex.property(entry.getKey(), entry.getValue());
                }
            }
            
            // ON CREATE vs ON MATCH logic
            if (isNew) {
                vertex.property("createdOn", System.currentTimeMillis());
                vertex.property("versionKey", node.getMetadata().getOrDefault("versionKey", "1.0"));
            } else {
                vertex.property("lastUpdatedOn", System.currentTimeMillis());
            }
            
            g.tx().commit();
            TelemetryManager.log("Upserted vertex: " + node.getIdentifier());
            return vertex;
            
        } catch (Exception e) {
            g.tx().rollback();
            TelemetryManager.error("Error upserting vertex: " + e.getMessage(), e);
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_UPDATE_NODE.name(),
                    "Error upserting vertex: " + e.getMessage(), e);
        }
    }

    /**
     * Update vertex property
     */
    public static void updateVertexProperty(String graphId, String uniqueId, String key, Object value) {
        GraphTraversalSource g = JanusGraphDriverUtil.getTraversal(graphId);
        
        try {
            g.V().has(IL_UNIQUE_ID, uniqueId)
                    .property(key, value)
                    .iterate();
            g.tx().commit();
            TelemetryManager.log("Updated property " + key + " for vertex: " + uniqueId);
            
        } catch (Exception e) {
            g.tx().rollback();
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_UPDATE_NODE.name(),
                    "Error updating vertex property: " + e.getMessage(), e);
        }
    }

    /**
     * Remove vertex property
     */
    public static void removeVertexProperty(String graphId, String uniqueId, String key) {
        GraphTraversalSource g = JanusGraphDriverUtil.getTraversal(graphId);
        
        try {
            g.V().has(IL_UNIQUE_ID, uniqueId)
                    .properties(key)
                    .drop()
                    .iterate();
            g.tx().commit();
            TelemetryManager.log("Removed property " + key + " from vertex: " + uniqueId);
            
        } catch (Exception e) {
            g.tx().rollback();
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_UPDATE_NODE.name(),
                    "Error removing vertex property: " + e.getMessage(), e);
        }
    }

    /**
     * Update multiple vertex properties
     */
    public static void updateVertexProperties(String graphId, String uniqueId, Map<String, Object> properties) {
        GraphTraversalSource g = JanusGraphDriverUtil.getTraversal(graphId);
        
        try {
            GraphTraversal<Vertex, Vertex> traversal = g.V().has(IL_UNIQUE_ID, uniqueId);
            
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                if (entry.getValue() != null) {
                    traversal.property(entry.getKey(), entry.getValue());
                }
            }
            
            traversal.iterate();
            g.tx().commit();
            TelemetryManager.log("Updated properties for vertex: " + uniqueId);
            
        } catch (Exception e) {
            g.tx().rollback();
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_UPDATE_NODE.name(),
                    "Error updating vertex properties: " + e.getMessage(), e);
        }
    }

    /**
     * Delete vertex
     */
    public static void deleteVertex(String graphId, String uniqueId) {
        GraphTraversalSource g = JanusGraphDriverUtil.getTraversal(graphId);
        
        try {
            g.V().has(IL_UNIQUE_ID, uniqueId).drop().iterate();
            g.tx().commit();
            TelemetryManager.log("Deleted vertex: " + uniqueId);
            
        } catch (Exception e) {
            g.tx().rollback();
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_DELETE_NODE.name(),
                    "Error deleting vertex: " + e.getMessage(), e);
        }
    }

    /**
     * Get vertex by unique ID
     */
    public static Node getVertexByUniqueId(String graphId, String uniqueId) {
        GraphTraversalSource g = JanusGraphDriverUtil.getTraversal(graphId);
        
        try {
            Vertex vertex = g.V().has(IL_UNIQUE_ID, uniqueId).next();
            return TinkerpopNodeUtil.vertexToNode(graphId, vertex);
        } catch (NoSuchElementException e) {
            return null;
        }
    }

    /**
     * Get vertices by unique IDs
     */
    public static List<Node> getVerticesByUniqueIds(String graphId, List<String> uniqueIds) {
        GraphTraversalSource g = JanusGraphDriverUtil.getTraversal(graphId);
        
        try {
            List<Vertex> vertices = g.V().has(IL_UNIQUE_ID, org.apache.tinkerpop.gremlin.process.traversal.P.within(uniqueIds))
                    .@@toList();
            
            return vertices.stream()
                    .map(v -> TinkerpopNodeUtil.vertexToNode(graphId, v))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            TelemetryManager.error("Error getting vertices by IDs: " + e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Create edge/relation
     */
    public static Edge createEdge(String graphId, String startNodeId, Relation relation, String endNodeId) {
        GraphTraversalSource g = JanusGraphDriverUtil.getTraversal(graphId);
        
        try {
            GraphTraversal<Vertex, Edge> edgeTraversal = g.V().has(IL_UNIQUE_ID, startNodeId)
                    .addE(relation.getRelationType())
                    .to(__.V().has(IL_UNIQUE_ID, endNodeId));
            
            // Add relation metadata
            if (relation.getMetadata() != null) {
                for (Map.Entry<String, Object> entry : relation.getMetadata().entrySet()) {
                    edgeTraversal.property(entry.getKey(), entry.getValue());
                }
            }
            
            Edge edge = edgeTraversal.next();
            g.tx().commit();
            TelemetryManager.log("Created edge from " + startNodeId + " to " + endNodeId);
            return edge;
            
        } catch (Exception e) {
            g.tx().rollback();
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_ADD_RELATION.name(),
                    "Error creating edge: " + e.getMessage(), e);
        }
    }

    /**
     * Delete edge/relation
     */
    public static void deleteEdge(String graphId, String startNodeId, String relationType, String endNodeId) {
        GraphTraversalSource g = JanusGraphDriverUtil.getTraversal(graphId);
        
        try {
            g.V().has(IL_UNIQUE_ID, startNodeId)
                    .outE(relationType)
                    .where(__.inV().has(IL_UNIQUE_ID, endNodeId))
                    .drop()
                    .iterate();
            g.tx().commit();
            TelemetryManager.log("Deleted edge from " + startNodeId + " to " + endNodeId);
            
        } catch (Exception e) {
            g.tx().rollback();
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_DELETE_RELATION.name(),
                    "Error deleting edge: " + e.getMessage(), e);
        }
    }

    /**
     * Count vertices matching criteria
     */
    public static long countVertices(String graphId, String propertyKey, Object value) {
        GraphTraversalSource g = JanusGraphDriverUtil.getTraversal(graphId);
        
        try {
            return g.V().has(propertyKey, value).count().next();
        } catch (Exception e) {
            TelemetryManager.error("Error counting vertices: " + e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Check if cyclic loop exists
     */
    public static boolean detectCycle(String graphId, String startNodeId, int maxDepth) {
        GraphTraversalSource g = JanusGraphDriverUtil.getTraversal(graphId);
        
        try {
            return g.V().has(IL_UNIQUE_ID, startNodeId)
                    .repeat(__.out())
                    .times(maxDepth)
                    .emit()
                    .path()
                    .where(__.loops().is(org.apache.tinkerpop.gremlin.process.traversal.P.gt(0)))
                    .limit(1)
                    .hasNext();
        } catch (Exception e) {
            TelemetryManager.error("Error detecting cycle: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Bulk import vertices with batching
     */
    public static void bulkImportVertices(String graphId, List<Node> nodes) {
        JanusGraph graph = JanusGraphDriverUtil.getGraph(graphId);
        int totalNodes = nodes.size();
        int batchCount = 0;
        
        try {
            for (int i = 0; i < totalNodes; i++) {
                Node node = nodes.get(i);
                createVertex(graphId, node);
                
                batchCount++;
                if (batchCount >= BATCH_SIZE) {
                    TelemetryManager.log("Committed batch of " + batchCount + " vertices. Progress: " + (i + 1) + "/" + totalNodes);
                    batchCount = 0;
                }
            }
            
            TelemetryManager.log("Bulk import completed. Total vertices: " + totalNodes);
            
        } catch (Exception e) {
            TelemetryManager.error("Error in bulk import: " + e.getMessage(), e);
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_IMPORT_NODE.name(),
                    "Error in bulk vertex import: " + e.getMessage(), e);
        }
    }
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: _empty_/`<any>`#toList#