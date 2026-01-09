error id: file://<WORKSPACE>/ontology-engine/graph-dac-api/src/main/java/org/sunbird/graph/service/operation/SearchOperations.java
file://<WORKSPACE>/ontology-engine/graph-dac-api/src/main/java/org/sunbird/graph/service/operation/SearchOperations.java
### com.thoughtworks.qdox.parser.ParseException: syntax error @[274,20]

error in qdox parser
file content:
```java
offset: 11705
uri: file://<WORKSPACE>/ontology-engine/graph-dac-api/src/main/java/org/sunbird/graph/service/operation/SearchOperations.java
text:
```scala
package org.sunbird.graph.service.operation;

import org.apache.commons.lang3.StringUtils;
import org.apache.tinkerpop.gremlin.process.traversal.P;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.sunbird.common.dto.Property;
import org.sunbird.common.exception.ServerException;
import org.sunbird.graph.common.enums.SystemProperties;
import org.sunbird.graph.dac.enums.GraphDACErrorCodes;
import org.sunbird.graph.dac.model.Node;
import org.sunbird.graph.dac.model.Relation;
import org.sunbird.graph.dac.model.SearchCriteria;
import org.sunbird.graph.dac.util.TinkerpopNodeUtil;
import org.sunbird.graph.service.util.JanusGraphDriverUtil;
import org.sunbird.telemetry.logger.TelemetryManager;

import java.util.*;
import java.util.stream.Collectors;

/**
 * SearchOperations - Gremlin-based search operations
 * Replaces Cypher query generation in SearchQueryGenerationUtil
 */
public class SearchOperations {

    private static final String IL_UNIQUE_ID = "IL_UNIQUE_ID";

    /**
     * Get node by unique ID with relations
     * Replaces: generateGetNodeByUniqueIdCypherQuery
     */
    public static Node getNodeByUniqueIdWithRelations(String graphId, String nodeId) {
        if (StringUtils.isBlank(graphId) || StringUtils.isBlank(nodeId)) {
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_GET_NODE.name(),
                    "Invalid graph ID or node ID");
        }

        GraphTraversalSource g = JanusGraphDriverUtil.getTraversal(graphId);

        try {
            // Get the vertex
            Vertex vertex = g.V().has(IL_UNIQUE_ID, nodeId).next();
            Node node = TinkerpopNodeUtil.vertexToNode(graphId, vertex);

            // Get all relations (both incoming and outgoing)
            List<Edge> edges = g.V().has(IL_UNIQUE_ID, nodeId)
                    .bothE()
                    .toList();

            // Convert edges to relations and attach to node
            List<Relation> outRelations = new ArrayList<>();
            List<Relation> inRelations = new ArrayList<>();

            for (Edge edge : edges) {
                Vertex outVertex = edge.outVertex();
                Vertex inVertex = edge.inVertex();
                
                String outNodeId = outVertex.value(IL_UNIQUE_ID);
                String inNodeId = inVertex.value(IL_UNIQUE_ID);

                Relation relation = new Relation(graphId, edge);
                
                if (outNodeId.equals(nodeId)) {
                    outRelations.add(relation);
                } else {
                    inRelations.add(relation);
                }
            }

            node.setOutRelations(outRelations);
            node.setInRelations(inRelations);

            TelemetryManager.log("Retrieved node with relations: " + nodeId);
            return node;

        } catch (NoSuchElementException e) {
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_GET_NODE.name(),
                    "Node not found: " + nodeId);
        } catch (Exception e) {
            TelemetryManager.error("Error getting node with relations: " + e.getMessage(), e);
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_GET_NODE.name(),
                    "Error retrieving node: " + e.getMessage(), e);
        }
    }

    /**
     * Get nodes by unique IDs with optional relations
     * Replaces: generateGetNodeByUniqueIdsCypherQuery
     */
    public static List<Node> getNodesByUniqueIds(String graphId, List<String> nodeIds, boolean includeRelations) {
        if (StringUtils.isBlank(graphId) || nodeIds == null || nodeIds.isEmpty()) {
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_GET_NODE.name(),
                    "Invalid parameters for bulk get operation");
        }

        GraphTraversalSource g = JanusGraphDriverUtil.getTraversal(graphId);

        try {
            List<Vertex> vertices = g.V()
                    .has(IL_UNIQUE_ID, P.within(nodeIds))
                    .toList();

            List<Node> nodes = vertices.stream()
                    .map(v -> TinkerpopNodeUtil.vertexToNode(graphId, v))
                    .collect(Collectors.toList());

            if (includeRelations) {
                for (Node node : nodes) {
                    attachRelations(g, graphId, node);
                }
            }

            TelemetryManager.log("Retrieved " + nodes.size() + " nodes by unique IDs");
            return nodes;

        } catch (Exception e) {
            TelemetryManager.error("Error getting nodes by IDs: " + e.getMessage(), e);
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_GET_NODE.name(),
                    "Error retrieving nodes: " + e.getMessage(), e);
        }
    }

    /**
     * Get nodes by property with optional relations
     * Replaces: generateGetNodesByPropertyCypherQuery
     */
    public static List<Node> getNodesByProperty(String graphId, Property property, boolean includeRelations) {
        if (StringUtils.isBlank(graphId) || property == null) {
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_GET_NODE.name(),
                    "Invalid parameters for get by property operation");
        }

        GraphTraversalSource g = JanusGraphDriverUtil.getTraversal(graphId);

        try {
            List<Vertex> vertices = g.V()
                    .has(property.getPropertyName(), property.getPropertyValue())
                    .toList();

            List<Node> nodes = vertices.stream()
                    .map(v -> TinkerpopNodeUtil.vertexToNode(graphId, v))
                    .collect(Collectors.toList());

            if (includeRelations) {
                for (Node node : nodes) {
                    attachRelations(g, graphId, node);
                }
            }

            TelemetryManager.log("Retrieved " + nodes.size() + " nodes by property: " + property.getPropertyName());
            return nodes;

        } catch (Exception e) {
            TelemetryManager.error("Error getting nodes by property: " + e.getMessage(), e);
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_GET_NODE.name(),
                    "Error retrieving nodes: " + e.getMessage(), e);
        }
    }

    /**
     * Search nodes by criteria
     * Replaces: Dynamic Cypher WHERE clause generation
     */
    public static List<Node> searchNodes(String graphId, SearchCriteria searchCriteria) {
        if (StringUtils.isBlank(graphId) || searchCriteria == null) {
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_SEARCH_NODE.name(),
                    "Invalid parameters for search operation");
        }

        GraphTraversalSource g = JanusGraphDriverUtil.getTraversal(graphId);

        try {
            GraphTraversal<Vertex, Vertex> traversal = g.V();

            // Apply search criteria
            traversal = applySearchCriteria(traversal, searchCriteria);

            // Apply pagination if specified
            if (searchCriteria.getStartPosition() > 0 && searchCriteria.getResultSize() > 0) {
                int start = searchCriteria.getStartPosition();
                int size = searchCriteria.getResultSize();
                traversal = traversal.range(start, start + size);
            }

            List<Vertex> vertices = traversal.toList();

            List<Node> nodes = vertices.stream()
                    .map(v -> TinkerpopNodeUtil.vertexToNode(graphId, v))
                    .collect(Collectors.toList());

            TelemetryManager.log("Search returned " + nodes.size() + " nodes");
            return nodes;

        } catch (Exception e) {
            TelemetryManager.error("Error searching nodes: " + e.getMessage(), e);
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_SEARCH_NODE.name(),
                    "Error searching nodes: " + e.getMessage(), e);
        }
    }

    /**
     * Apply search criteria to traversal
     */
    private static GraphTraversal<Vertex, Vertex> applySearchCriteria(
            GraphTraversal<Vertex, Vertex> traversal, SearchCriteria criteria) {

        // Object type filter
        if (StringUtils.isNotBlank(criteria.getObjectType())) {
            traversal = traversal.has("IL_FUNC_OBJECT_TYPE", criteria.getObjectType());
        }

        // Node type filter
        if (StringUtils.isNotBlank(criteria.getNodeType())) {
            traversal = traversal.has("IL_SYS_NODE_TYPE", criteria.getNodeType());
        }

        // Metadata criteria
        if (criteria.getMetadata() != null && !criteria.getMetadata().isEmpty()) {
            for (MetadataCriterion mc : criteria.getMetadata()) {
                String key = mc.getPropertyName();
                Object value = mc.getPropertyValue();
                String operator = mc.getOperator();
                
                if (value != null && key != null) {
                    if (SearchConditions.OPERATOR_EQUAL.equals(operator)) {
                        if (value instanceof List) {
                            traversal = traversal.has(key, P.within((List<?>) value));
                        } else {
                            traversal = traversal.has(key, value);
                        }
                    } else if (SearchConditions.OPERATOR_NOT_EQUAL.equals(operator)) {
                        traversal = traversal.has(key, P.neq(value));
                    } else if (SearchConditions.OPERATOR_GREATER_THAN.equals(operator) && value instanceof Number) {
                        traversal = traversal.has(key, P.gt((Number) value));
                    } else if (SearchConditions.OPERATOR_GREATER_THAN_EQUAL.equals(operator) && value instanceof Number) {
                        traversal = traversal.has(key, P.gte((Number) value));
                    } else if (SearchConditions.OPERATOR_LESS_THAN.equals(operator) && value instanceof Number) {
                        traversal = traversal.has(key, P.lt((Number) value));
                    } else if (SearchConditions.OPERATOR_LESS_THAN_EQUAL.equals(operator) && value instanceof Number) {
                        traversal = traversal.has(key, P.lte((Number) value));
                    } else if (SearchConditions.OPERATOR_STARTS_WITH.equals(operator) && value instanceof String) {
                        traversal = traversal.has(key, P.test((a, b) -> a.toString().startsWith(b.toString()), value));
                    } else if (SearchConditions.OPERATOR_ENDS_WITH.equals(operator) && value instanceof String) {
                        traversal = traversal.has(key, P.test((a, b) -> a.toString().endsWith(b.toString()), value));
                    } else if (SearchConditions.OPERATOR_CONTAINS.equals(operator) && value instanceof String) {
                        traversal = traversal.has(key, P.test((a, b) -> a.toString().contains(b.toString()), value));
                    }
                }
            }
        }

        // Keep empty block for compatibility
        if (false) {
            String key = null;
            Object value = null;
                
                if (value instanceof Number) {
                    traversal = traversal.has(key, P.lt((Number) value));
                }
            }
        }

        return traversal;
    }

    /**
     * Attach relations to node
     */
    private static v@@oid attachRelations(GraphTraversalSource g, String graphId, Node node) {
        try {
            List<Edge> edges = g.V().has(IL_UNIQUE_ID, node.getIdentifier())
                    .bothE()
                    .toList();

            List<Relation> outRelations = new ArrayList<>();
            List<Relation> inRelations = new ArrayList<>();

            for (Edge edge : edges) {
                Vertex outVertex = edge.outVertex();
                Vertex inVertex = edge.inVertex();
                
                String outNodeId = outVertex.value(IL_UNIQUE_ID);
                String inNodeId = inVertex.value(IL_UNIQUE_ID);

                Relation relation = new Relation(graphId, edge);
                
                if (outNodeId.equals(node.getIdentifier())) {
                    outRelations.add(relation);
                } else {
                    inRelations.add(relation);
                }
            }

            node.setOutRelations(outRelations);
            node.setInRelations(inRelations);

        } catch (Exception e) {
            TelemetryManager.error("Error attaching relations: " + e.getMessage(), e);
        }
    }

    /**
     * Count nodes matching criteria
     */
    public static long countNodesByCriteria(String graphId, SearchCriteria searchCriteria) {
        if (StringUtils.isBlank(graphId) || searchCriteria == null) {
            return 0;
        }

        GraphTraversalSource g = JanusGraphDriverUtil.getTraversal(graphId);

        try {
            GraphTraversal<Vertex, Vertex> traversal = g.V();
            traversal = applySearchCriteria(traversal, searchCriteria);
            return traversal.count().next();

        } catch (Exception e) {
            TelemetryManager.error("Error counting nodes: " + e.getMessage(), e);
            return 0;
        }
    }
}

```

```



#### Error stacktrace:

```
com.thoughtworks.qdox.parser.impl.Parser.yyerror(Parser.java:2025)
	com.thoughtworks.qdox.parser.impl.Parser.yyparse(Parser.java:2147)
	com.thoughtworks.qdox.parser.impl.Parser.parse(Parser.java:2006)
	com.thoughtworks.qdox.library.SourceLibrary.parse(SourceLibrary.java:232)
	com.thoughtworks.qdox.library.SourceLibrary.parse(SourceLibrary.java:190)
	com.thoughtworks.qdox.library.SourceLibrary.addSource(SourceLibrary.java:94)
	com.thoughtworks.qdox.library.SourceLibrary.addSource(SourceLibrary.java:89)
	com.thoughtworks.qdox.library.SortedClassLibraryBuilder.addSource(SortedClassLibraryBuilder.java:162)
	com.thoughtworks.qdox.JavaProjectBuilder.addSource(JavaProjectBuilder.java:174)
	scala.meta.internal.mtags.JavaMtags.indexRoot(JavaMtags.scala:49)
	scala.meta.internal.metals.SemanticdbDefinition$.foreachWithReturnMtags(SemanticdbDefinition.scala:99)
	scala.meta.internal.metals.Indexer.indexSourceFile(Indexer.scala:546)
	scala.meta.internal.metals.Indexer.$anonfun$reindexWorkspaceSources$3(Indexer.scala:677)
	scala.meta.internal.metals.Indexer.$anonfun$reindexWorkspaceSources$3$adapted(Indexer.scala:674)
	scala.collection.IterableOnceOps.foreach(IterableOnce.scala:630)
	scala.collection.IterableOnceOps.foreach$(IterableOnce.scala:628)
	scala.collection.AbstractIterator.foreach(Iterator.scala:1313)
	scala.meta.internal.metals.Indexer.reindexWorkspaceSources(Indexer.scala:674)
	scala.meta.internal.metals.MetalsLspService.$anonfun$onChange$2(MetalsLspService.scala:912)
	scala.runtime.java8.JFunction0$mcV$sp.apply(JFunction0$mcV$sp.scala:18)
	scala.concurrent.Future$.$anonfun$apply$1(Future.scala:691)
	scala.concurrent.impl.Promise$Transformation.run(Promise.scala:500)
	java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1136)
	java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:635)
	java.base/java.lang.Thread.run(Thread.java:840)
```
#### Short summary: 

QDox parse error in file://<WORKSPACE>/ontology-engine/graph-dac-api/src/main/java/org/sunbird/graph/service/operation/SearchOperations.java