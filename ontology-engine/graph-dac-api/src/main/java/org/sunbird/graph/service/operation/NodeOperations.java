package org.sunbird.graph.service.operation;

import org.apache.commons.lang3.StringUtils;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.sunbird.common.DateUtils;
import org.sunbird.common.exception.ServerException;
import org.sunbird.graph.common.Identifier;
import org.sunbird.graph.common.enums.GraphDACParams;
import org.sunbird.graph.dac.enums.GraphDACErrorCodes;
import org.sunbird.graph.dac.model.Node;
import org.sunbird.graph.dac.util.GremlinOperationsUtil;
import org.sunbird.graph.dac.util.TinkerpopNodeUtil;
import org.sunbird.graph.service.util.JanusGraphDriverUtil;
import org.sunbird.telemetry.logger.TelemetryManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * NodeOperations - Gremlin-based node CRUD operations
 * Replaces Cypher query generation in NodeQueryGenerationUtil
 */
public class NodeOperations {

    private static final String IL_UNIQUE_ID = "IL_UNIQUE_ID";

    /**
     * Create a new node
     * Replaces: generateCreateNodeCypherQuery
     */
    public static Node createNode(String graphId, Node node) {
        if (StringUtils.isBlank(graphId)) {
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_ADD_NODE.name(),
                    "Invalid graph ID for create node operation");
        }
        
        if (node == null) {
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_ADD_NODE.name(),
                    "Invalid node for create operation");
        }

        // Generate identifier if not present
        if (StringUtils.isBlank(node.getIdentifier())) {
            node.setIdentifier(Identifier.getIdentifier(graphId, Identifier.getUniqueIdFromTimestamp()));
        }

        // Set timestamps
        String currentDate = DateUtils.formatCurrentDate();
        if (!node.getMetadata().containsKey("createdOn")) {
            node.getMetadata().put("createdOn", currentDate);
        }
        if (!node.getMetadata().containsKey("lastUpdatedOn")) {
            node.getMetadata().put("lastUpdatedOn", currentDate);
        }

        Vertex vertex = GremlinOperationsUtil.createVertex(graphId, node);
        Node createdNode = TinkerpopNodeUtil.vertexToNode(graphId, vertex);
        
        TelemetryManager.log("Created node with ID: " + node.getIdentifier());
        return createdNode;
    }

    /**
     * Upsert node - create if not exists, update if exists
     * Replaces: generateUpsertNodeCypherQuery
     */
    public static Node upsertNode(String graphId, Node node) {
        if (StringUtils.isBlank(graphId)) {
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_UPDATE_NODE.name(),
                    "Invalid graph ID for upsert node operation");
        }
        
        if (node == null) {
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_UPDATE_NODE.name(),
                    "Invalid node for upsert operation");
        }

        // Generate identifier if not present
        if (StringUtils.isBlank(node.getIdentifier())) {
            node.setIdentifier(Identifier.getIdentifier(graphId, Identifier.getUniqueIdFromTimestamp()));
        }

        // Set timestamps - upsert logic handles ON CREATE vs ON MATCH
        String currentDate = DateUtils.formatCurrentDate();
        node.getMetadata().put("lastUpdatedOn", currentDate);

        Vertex vertex = GremlinOperationsUtil.upsertVertex(graphId, node);
        Node upsertedNode = TinkerpopNodeUtil.vertexToNode(graphId, vertex);
        
        TelemetryManager.log("Upserted node with ID: " + node.getIdentifier());
        return upsertedNode;
    }

    /**
     * Update node properties
     * Replaces: generateUpdateNodeCypherQuery
     */
    public static Node updateNode(String graphId, String nodeId, Map<String, Object> metadata) {
        if (StringUtils.isBlank(graphId) || StringUtils.isBlank(nodeId)) {
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_UPDATE_NODE.name(),
                    "Invalid graph ID or node ID for update operation");
        }

        // Add timestamp
        String currentDate = DateUtils.formatCurrentDate();
        metadata.put("lastUpdatedOn", currentDate);

        GremlinOperationsUtil.updateVertexProperties(graphId, nodeId, metadata);
        
        // Fetch and return updated node
        Node updatedNode = GremlinOperationsUtil.getVertexByUniqueId(graphId, nodeId);
        
        TelemetryManager.log("Updated node with ID: " + nodeId);
        return updatedNode;
    }

    /**
     * Delete node
     * Replaces: generateDeleteNodeCypherQuery
     */
    public static void deleteNode(String graphId, String nodeId) {
        if (StringUtils.isBlank(graphId) || StringUtils.isBlank(nodeId)) {
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_DELETE_NODE.name(),
                    "Invalid graph ID or node ID for delete operation");
        }

        GremlinOperationsUtil.deleteVertex(graphId, nodeId);
        TelemetryManager.log("Deleted node with ID: " + nodeId);
    }

    /**
     * Add/update a single property
     * Replaces: generateAddPropertyCypherQuery
     */
    public static void addProperty(String graphId, String nodeId, String propertyKey, Object propertyValue) {
        if (StringUtils.isBlank(graphId) || StringUtils.isBlank(nodeId) || StringUtils.isBlank(propertyKey)) {
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_UPDATE_NODE.name(),
                    "Invalid parameters for add property operation");
        }

        GremlinOperationsUtil.updateVertexProperty(graphId, nodeId, propertyKey, propertyValue);
        TelemetryManager.log("Added property " + propertyKey + " to node: " + nodeId);
    }

    /**
     * Remove a property
     * Replaces: generateRemovePropertyCypherQuery
     */
    public static void removeProperty(String graphId, String nodeId, String propertyKey) {
        if (StringUtils.isBlank(graphId) || StringUtils.isBlank(nodeId) || StringUtils.isBlank(propertyKey)) {
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_UPDATE_NODE.name(),
                    "Invalid parameters for remove property operation");
        }

        GremlinOperationsUtil.removeVertexProperty(graphId, nodeId, propertyKey);
        TelemetryManager.log("Removed property " + propertyKey + " from node: " + nodeId);
    }

    /**
     * Get node by unique ID
     */
    public static Node getNodeByUniqueId(String graphId, String nodeId) {
        if (StringUtils.isBlank(graphId) || StringUtils.isBlank(nodeId)) {
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_GET_NODE.name(),
                    "Invalid graph ID or node ID for get operation");
        }

        Node node = GremlinOperationsUtil.getVertexByUniqueId(graphId, nodeId);
        if (node == null) {
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_GET_NODE.name(),
                    "Node not found with ID: " + nodeId);
        }

        TelemetryManager.log("Retrieved node with ID: " + nodeId);
        return node;
    }

    /**
     * Get nodes by unique IDs (bulk operation)
     */
    public static List<Node> getNodesByUniqueIds(String graphId, List<String> nodeIds) {
        if (StringUtils.isBlank(graphId) || nodeIds == null || nodeIds.isEmpty()) {
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_GET_NODE.name(),
                    "Invalid parameters for bulk get operation");
        }

        List<Node> nodes = GremlinOperationsUtil.getVerticesByUniqueIds(graphId, nodeIds);
        TelemetryManager.log("Retrieved " + nodes.size() + " nodes");
        return nodes;
    }

    /**
     * Get nodes by property
     */
    public static List<Node> getNodesByProperty(String graphId, String propertyKey, Object propertyValue) {
        if (StringUtils.isBlank(graphId) || StringUtils.isBlank(propertyKey)) {
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_GET_NODE.name(),
                    "Invalid parameters for get by property operation");
        }

        GraphTraversalSource g = JanusGraphDriverUtil.getTraversal(graphId);
        
        try {
            List<Vertex> vertices = g.V()
                    .has(propertyKey, propertyValue)
                    .toList();

            List<Node> nodes = vertices.stream()
                    .map(v -> TinkerpopNodeUtil.vertexToNode(graphId, v))
                    .collect(Collectors.toList());

            TelemetryManager.log("Retrieved " + nodes.size() + " nodes by property: " + propertyKey);
            return nodes;
            
        } catch (Exception e) {
            TelemetryManager.error("Error getting nodes by property: " + e.getMessage(), e);
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_GET_NODE.name(),
                    "Error retrieving nodes by property: " + e.getMessage(), e);
        }
    }

    /**
     * Check if node exists
     */
    public static boolean nodeExists(String graphId, String nodeId) {
        if (StringUtils.isBlank(graphId) || StringUtils.isBlank(nodeId)) {
            return false;
        }

        GraphTraversalSource g = JanusGraphDriverUtil.getTraversal(graphId);
        
        try {
            return g.V().has(IL_UNIQUE_ID, nodeId).hasNext();
        } catch (Exception e) {
            TelemetryManager.error("Error checking node existence: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Count nodes matching criteria
     */
    public static long countNodes(String graphId, String propertyKey, Object propertyValue) {
        if (StringUtils.isBlank(graphId)) {
            return 0;
        }

        return GremlinOperationsUtil.countVertices(graphId, propertyKey, propertyValue);
    }
}
