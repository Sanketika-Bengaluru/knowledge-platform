package org.sunbird.graph.service.operation;

import org.apache.commons.lang3.StringUtils;
import org.apache.tinkerpop.gremlin.process.traversal.P;
import org.apache.tinkerpop.gremlin.process.traversal.Path;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.sunbird.common.DateUtils;
import org.sunbird.common.exception.ServerException;
import org.sunbird.graph.dac.enums.GraphDACErrorCodes;
import org.sunbird.graph.dac.model.Node;
import org.sunbird.graph.dac.model.Relation;
import org.sunbird.graph.dac.util.GremlinOperationsUtil;
import org.sunbird.graph.dac.util.TinkerpopNodeUtil;
import org.sunbird.graph.service.util.JanusGraphDriverUtil;
import org.sunbird.telemetry.logger.TelemetryManager;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RelationOperations - Gremlin-based relation/edge operations
 * Replaces Cypher query generation in GraphQueryGenerationUtil
 */
public class RelationOperations {

    private static final String IL_UNIQUE_ID = "IL_UNIQUE_ID";

    /**
     * Create a relation between two nodes
     * Replaces: generateCreateRelationCypherQuery
     */
    public static Relation createRelation(String graphId, String startNodeId, String relationType, 
                                          String endNodeId, Map<String, Object> metadata) {
        if (StringUtils.isBlank(graphId) || StringUtils.isBlank(startNodeId) || 
            StringUtils.isBlank(relationType) || StringUtils.isBlank(endNodeId)) {
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_ADD_RELATION.name(),
                    "Invalid parameters for create relation operation");
        }

        // Create relation object
        Relation relation = new Relation();
        relation.setStartNodeId(startNodeId);
        relation.setEndNodeId(endNodeId);
        relation.setRelationType(relationType);
        
        if (metadata != null && !metadata.isEmpty()) {
            relation.setMetadata(metadata);
        }

        // Add timestamp
        String currentDate = DateUtils.formatCurrentDate();
        if (relation.getMetadata() == null) {
            relation.setMetadata(new HashMap<>());
        }
        relation.getMetadata().put("createdOn", currentDate);

        Edge edge = GremlinOperationsUtil.createEdge(graphId, startNodeId, relation, endNodeId);
        Relation createdRelation = new Relation(graphId, edge);

        TelemetryManager.log("Created relation: " + startNodeId + " -[" + relationType + "]-> " + endNodeId);
        return createdRelation;
    }

    /**
     * Update relation properties
     * Replaces: generateUpdateRelationCypherQuery
     */
    public static Relation updateRelation(String graphId, String startNodeId, String relationType, 
                                          String endNodeId, Map<String, Object> metadata) {
        if (StringUtils.isBlank(graphId) || StringUtils.isBlank(startNodeId) || 
            StringUtils.isBlank(relationType) || StringUtils.isBlank(endNodeId)) {
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_UPDATE_RELATION.name(),
                    "Invalid parameters for update relation operation");
        }

        GraphTraversalSource g = JanusGraphDriverUtil.getTraversal(graphId);

        try {
            // Find the edge
            GraphTraversal<Vertex, Edge> edgeTraversal = g.V().has(IL_UNIQUE_ID, startNodeId)
                    .outE(relationType)
                    .where(__.inV().has(IL_UNIQUE_ID, endNodeId));

            if (!edgeTraversal.hasNext()) {
                throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_UPDATE_RELATION.name(),
                        "Relation not found for update");
            }

            Edge edge = edgeTraversal.next();

            // Update properties
            String currentDate = DateUtils.formatCurrentDate();
            if (metadata != null) {
                for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                    if (entry.getValue() != null) {
                        edge.property(entry.getKey(), entry.getValue());
                    }
                }
            }
            edge.property("lastUpdatedOn", currentDate);

            g.tx().commit();

            Relation updatedRelation = new Relation(graphId, edge);
            TelemetryManager.log("Updated relation: " + startNodeId + " -[" + relationType + "]-> " + endNodeId);
            return updatedRelation;

        } catch (Exception e) {
            g.tx().rollback();
            TelemetryManager.error("Error updating relation: " + e.getMessage(), e);
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_UPDATE_RELATION.name(),
                    "Error updating relation: " + e.getMessage(), e);
        }
    }

    /**
     * Delete relation
     * Replaces: generateDeleteRelationCypherQuery
     */
    public static void deleteRelation(String graphId, String startNodeId, String relationType, String endNodeId) {
        if (StringUtils.isBlank(graphId) || StringUtils.isBlank(startNodeId) || 
            StringUtils.isBlank(relationType) || StringUtils.isBlank(endNodeId)) {
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_DELETE_RELATION.name(),
                    "Invalid parameters for delete relation operation");
        }

        GremlinOperationsUtil.deleteEdge(graphId, startNodeId, relationType, endNodeId);
        TelemetryManager.log("Deleted relation: " + startNodeId + " -[" + relationType + "]-> " + endNodeId);
    }

    /**
     * Get outgoing relations from a node
     */
    public static List<Relation> getOutRelations(String graphId, String nodeId, String relationType) {
        if (StringUtils.isBlank(graphId) || StringUtils.isBlank(nodeId)) {
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_GET_RELATION.name(),
                    "Invalid parameters for get relations operation");
        }

        GraphTraversalSource g = JanusGraphDriverUtil.getTraversal(graphId);

        try {
            GraphTraversal<Vertex, Edge> traversal = g.V().has(IL_UNIQUE_ID, nodeId).outE();
            
            if (StringUtils.isNotBlank(relationType)) {
                traversal = traversal.hasLabel(relationType);
            }

            List<Edge> edges = traversal.toList();
            
            List<Relation> relations = edges.stream()
                    .map(e -> new Relation(graphId, e))
                    .collect(Collectors.toList());

            TelemetryManager.log("Retrieved " + relations.size() + " outgoing relations for node: " + nodeId);
            return relations;

        } catch (Exception e) {
            TelemetryManager.error("Error getting outgoing relations: " + e.getMessage(), e);
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_GET_RELATION.name(),
                    "Error getting relations: " + e.getMessage(), e);
        }
    }

    /**
     * Get incoming relations to a node
     */
    public static List<Relation> getInRelations(String graphId, String nodeId, String relationType) {
        if (StringUtils.isBlank(graphId) || StringUtils.isBlank(nodeId)) {
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_GET_RELATION.name(),
                    "Invalid parameters for get relations operation");
        }

        GraphTraversalSource g = JanusGraphDriverUtil.getTraversal(graphId);

        try {
            GraphTraversal<Vertex, Edge> traversal = g.V().has(IL_UNIQUE_ID, nodeId).inE();
            
            if (StringUtils.isNotBlank(relationType)) {
                traversal = traversal.hasLabel(relationType);
            }

            List<Edge> edges = traversal.toList();
            
            List<Relation> relations = edges.stream()
                    .map(e -> new Relation(graphId, e))
                    .collect(Collectors.toList());

            TelemetryManager.log("Retrieved " + relations.size() + " incoming relations for node: " + nodeId);
            return relations;

        } catch (Exception e) {
            TelemetryManager.error("Error getting incoming relations: " + e.getMessage(), e);
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_GET_RELATION.name(),
                    "Error getting relations: " + e.getMessage(), e);
        }
    }

    /**
     * Get all connected nodes (variable-length path)
     * Replaces: MATCH (n)-[*1..depth]->(m) patterns
     */
    public static List<Node> getConnectedNodes(String graphId, String startNodeId, int maxDepth, String relationType) {
        if (StringUtils.isBlank(graphId) || StringUtils.isBlank(startNodeId)) {
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_TRAVERSAL.name(),
                    "Invalid parameters for traversal operation");
        }

        GraphTraversalSource g = JanusGraphDriverUtil.getTraversal(graphId);

        try {
            GraphTraversal<Vertex, Vertex> traversal = g.V().has(IL_UNIQUE_ID, startNodeId);
            
            if (StringUtils.isNotBlank(relationType)) {
                traversal = traversal.repeat(__.out(relationType)).times(maxDepth).emit();
            } else {
                traversal = traversal.repeat(__.out()).times(maxDepth).emit();
            }

            List<Vertex> vertices = traversal.toList();
            
            List<Node> nodes = vertices.stream()
                    .map(v -> TinkerpopNodeUtil.vertexToNode(graphId, v))
                    .collect(Collectors.toList());

            TelemetryManager.log("Traversal found " + nodes.size() + " connected nodes");
            return nodes;

        } catch (Exception e) {
            TelemetryManager.error("Error in graph traversal: " + e.getMessage(), e);
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_TRAVERSAL.name(),
                    "Error in traversal: " + e.getMessage(), e);
        }
    }

    /**
     * Detect cyclic dependencies
     * Replaces: MATCH path = (n)-[*1..depth]->(n) WHERE length(path) > 0
     */
    public static boolean hasCycle(String graphId, String startNodeId, int maxDepth) {
        if (StringUtils.isBlank(graphId) || StringUtils.isBlank(startNodeId)) {
            return false;
        }

        return GremlinOperationsUtil.detectCycle(graphId, startNodeId, maxDepth);
    }

    /**
     * Get path between two nodes
     */
    public static List<List<Node>> getPathsBetweenNodes(String graphId, String startNodeId, 
                                                        String endNodeId, int maxDepth) {
        if (StringUtils.isBlank(graphId) || StringUtils.isBlank(startNodeId) || 
            StringUtils.isBlank(endNodeId)) {
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_TRAVERSAL.name(),
                    "Invalid parameters for path finding operation");
        }

        GraphTraversalSource g = JanusGraphDriverUtil.getTraversal(graphId);

        try {
            List<Path> paths = g.V().has(IL_UNIQUE_ID, startNodeId)
                    .repeat(__.out().simplePath())
                    .until(__.has(IL_UNIQUE_ID, endNodeId).or().loops().is(P.gt(maxDepth)))
                    .has(IL_UNIQUE_ID, endNodeId)
                    .path()
                    .limit(100) // Limit to avoid excessive results
                    .toList();

            List<List<Node>> nodePaths = new ArrayList<>();
            for (Path path : paths) {
                List<Node> nodePath = new ArrayList<>();
                for (Object obj : path) {
                    if (obj instanceof Vertex) {
                        nodePath.add(TinkerpopNodeUtil.vertexToNode(graphId, (Vertex) obj));
                    }
                }
                if (!nodePath.isEmpty()) {
                    nodePaths.add(nodePath);
                }
            }

            TelemetryManager.log("Found " + nodePaths.size() + " paths between nodes");
            return nodePaths;

        } catch (Exception e) {
            TelemetryManager.error("Error finding paths: " + e.getMessage(), e);
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_TRAVERSAL.name(),
                    "Error finding paths: " + e.getMessage(), e);
        }
    }

    /**
     * Get subgraph (nodes and their relations)
     */
    public static Map<String, Object> getSubgraph(String graphId, String rootNodeId, int depth) {
        if (StringUtils.isBlank(graphId) || StringUtils.isBlank(rootNodeId)) {
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_TRAVERSAL.name(),
                    "Invalid parameters for subgraph extraction");
        }

        GraphTraversalSource g = JanusGraphDriverUtil.getTraversal(graphId);

        try {
            // Get all nodes within depth
            List<Vertex> vertices = g.V().has(IL_UNIQUE_ID, rootNodeId)
                    .repeat(__.bothE().otherV().simplePath())
                    .times(depth)
                    .emit()
                    .dedup()
                    .toList();

            List<Node> nodes = vertices.stream()
                    .map(v -> TinkerpopNodeUtil.vertexToNode(graphId, v))
                    .collect(Collectors.toList());

            // Get vertex IDs for relation extraction
            Set<String> vertexIds = nodes.stream()
                    .map(Node::getIdentifier)
                    .collect(Collectors.toSet());

            // Get all edges between these vertices
            List<Edge> edges = g.V().has(IL_UNIQUE_ID, P.within(vertexIds))
                    .bothE()
                    .where(__.otherV().has(IL_UNIQUE_ID, P.within(vertexIds)))
                    .dedup()
                    .toList();

            List<Relation> relations = edges.stream()
                    .map(e -> new Relation(graphId, e))
                    .collect(Collectors.toList());

            Map<String, Object> subgraph = new HashMap<>();
            subgraph.put("nodes", nodes);
            subgraph.put("relations", relations);

            TelemetryManager.log("Extracted subgraph: " + nodes.size() + " nodes, " + relations.size() + " relations");
            return subgraph;

        } catch (Exception e) {
            TelemetryManager.error("Error extracting subgraph: " + e.getMessage(), e);
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_TRAVERSAL.name(),
                    "Error extracting subgraph: " + e.getMessage(), e);
        }
    }

    /**
     * Bulk create relations
     */
    public static void bulkCreateRelations(String graphId, List<Map<String, Object>> relationData) {
        if (StringUtils.isBlank(graphId) || relationData == null || relationData.isEmpty()) {
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_ADD_RELATION.name(),
                    "Invalid parameters for bulk relation creation");
        }

        int created = 0;
        for (Map<String, Object> data : relationData) {
            try {
                String startNodeId = (String) data.get("startNodeId");
                String endNodeId = (String) data.get("endNodeId");
                String relationType = (String) data.get("relationType");
                @SuppressWarnings("unchecked")
                Map<String, Object> metadata = (Map<String, Object>) data.get("metadata");

                createRelation(graphId, startNodeId, relationType, endNodeId, metadata);
                created++;

            } catch (Exception e) {
                TelemetryManager.error("Error creating relation in bulk: " + e.getMessage(), e);
            }
        }

        TelemetryManager.log("Bulk created " + created + " relations out of " + relationData.size());
    }
}
