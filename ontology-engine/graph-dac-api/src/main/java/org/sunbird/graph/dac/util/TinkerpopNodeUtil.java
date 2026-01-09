package org.sunbird.graph.dac.util;

import org.apache.commons.lang3.StringUtils;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Property;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.sunbird.common.exception.ServerException;
import org.sunbird.graph.common.enums.SystemProperties;
import org.sunbird.graph.dac.enums.GraphDACErrorCodes;
import org.sunbird.graph.dac.model.Node;
import org.sunbird.graph.dac.model.Relation;

import java.util.*;
import java.util.stream.Collectors;

/**
 * TinkerpopNodeUtil - Utility for converting between Tinkerpop/JanusGraph types and application models
 * Replaces Neo4jNodeUtil for JanusGraph migration
 *
 * @author Sunbird
 */
public class TinkerpopNodeUtil {

    /**
     * Convert a Vertex to Node object
     *
     * @param graphId The graph identifier
     * @param vertex  The JanusGraph/Tinkerpop Vertex
     * @return Node object
     */
    public static Node vertexToNode(String graphId, Vertex vertex) {
        if (null == vertex) {
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_NULL_DB_NODE.name(),
                    "Failed to create node object. Vertex from database is null.");
        }

        Node node = new Node();
        node.setGraphId(graphId);
        Object vertexId = vertex.id();
        if (vertexId instanceof Long) {
            node.setId((Long) vertexId);
        } else if (vertexId instanceof Integer) {
            node.setId(((Integer) vertexId).longValue());
        } else {
            node.setId(Long.parseLong(vertexId.toString()));
        }

        Map<String, Object> metadata = new HashMap<>();
        Iterator<VertexProperty<Object>> properties = vertex.properties();

        while (properties.hasNext()) {
            VertexProperty<Object> property = properties.next();
            String key = property.key();
            Object value = property.value();

            if (StringUtils.equalsIgnoreCase(key, SystemProperties.IL_UNIQUE_ID.name())) {
                node.setIdentifier(value.toString());
            } else if (StringUtils.equalsIgnoreCase(key, SystemProperties.IL_SYS_NODE_TYPE.name())) {
                node.setNodeType(value.toString());
            } else if (StringUtils.equalsIgnoreCase(key, SystemProperties.IL_FUNC_OBJECT_TYPE.name())) {
                node.setObjectType(value.toString());
            } else {
                metadata.put(key, convertPropertyValue(value));
            }
        }
        node.setMetadata(metadata);

        return node;
    }

    /**
     * Convert a Vertex with relations to Node object
     *
     * @param graphId The graph identifier
     * @param vertex  The JanusGraph/Tinkerpop Vertex
     * @param includeRelations Whether to include relations
     * @return Node object with relations
     */
    public static Node vertexToNodeWithRelations(String graphId, Vertex vertex, boolean includeRelations) {
        Node node = vertexToNode(graphId, vertex);

        if (includeRelations) {
            // Get outgoing relations
            Iterator<Edge> outEdges = vertex.edges(Direction.OUT);
            if (outEdges.hasNext()) {
                List<Relation> outRelations = new ArrayList<>();
                while (outEdges.hasNext()) {
                    outRelations.add(edgeToRelation(graphId, outEdges.next()));
                }
                node.setOutRelations(outRelations);
            }

            // Get incoming relations
            Iterator<Edge> inEdges = vertex.edges(Direction.IN);
            if (inEdges.hasNext()) {
                List<Relation> inRelations = new ArrayList<>();
                while (inEdges.hasNext()) {
                    inRelations.add(edgeToRelation(graphId, inEdges.next()));
                }
                node.setInRelations(inRelations);
            }
        }

        return node;
    }

    /**
     * Convert an Edge to Relation object
     *
     * @param graphId The graph identifier
     * @param edge    The JanusGraph/Tinkerpop Edge
     * @return Relation object
     */
    public static Relation edgeToRelation(String graphId, Edge edge) {
        if (null == edge) {
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_INVALID_RELATION.name(),
                    "Failed to create relation object. Edge from database is null.");
        }

        Relation relation = new Relation(graphId, edge);
        return relation;
    }

    /**
     * Convert a Map<String, Object> from Gremlin valueMap to Node metadata format
     *
     * @param graphId    The graph identifier
     * @param valueMap   The result from g.V().valueMap()
     * @param vertexId   The vertex ID
     * @return Node object
     */
    public static Node valueMapToNode(String graphId, Map<String, Object> valueMap, Object vertexId) {
        if (null == valueMap || valueMap.isEmpty()) {
            throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_NULL_DB_NODE.name(),
                    "Failed to create node object. ValueMap is null or empty.");
        }

        Node node = new Node();
        node.setGraphId(graphId);
        if (vertexId instanceof Long) {
            node.setId((Long) vertexId);
        } else if (vertexId instanceof Integer) {
            node.setId(((Integer) vertexId).longValue());
        } else {
            node.setId(Long.parseLong(vertexId.toString()));
        }

        Map<String, Object> metadata = new HashMap<>();

        for (Map.Entry<String, Object> entry : valueMap.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            // Gremlin valueMap returns lists by default, extract single values
            if (value instanceof List) {
                List<?> list = (List<?>) value;
                value = list.isEmpty() ? null : list.get(0);
            }

            if (value == null) {
                continue;
            }

            if (StringUtils.equalsIgnoreCase(key, SystemProperties.IL_UNIQUE_ID.name())) {
                node.setIdentifier(value.toString());
            } else if (StringUtils.equalsIgnoreCase(key, SystemProperties.IL_SYS_NODE_TYPE.name())) {
                node.setNodeType(value.toString());
            } else if (StringUtils.equalsIgnoreCase(key, SystemProperties.IL_FUNC_OBJECT_TYPE.name())) {
                node.setObjectType(value.toString());
            } else {
                metadata.put(key, convertPropertyValue(value));
            }
        }

        node.setMetadata(metadata);
        return node;
    }

    /**
     * Prepare vertex properties from Node metadata for JanusGraph storage
     * Converts Java types to JanusGraph-compatible types
     *
     * @param metadata The metadata map
     * @return Prepared properties map
     */
    public static Map<String, Object> prepareVertexProperties(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return new HashMap<>();
        }

        Map<String, Object> properties = new HashMap<>();

        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value == null) {
                continue;
            }

            // Convert complex types to JanusGraph-compatible types
            if (value instanceof List) {
                properties.put(key, convertListToArray((List<?>) value));
            } else if (value instanceof Map) {
                // Flatten nested maps or serialize
                properties.put(key, flattenMap((Map<?, ?>) value));
            } else if (isSimpleType(value)) {
                properties.put(key, value);
            } else {
                // Convert to string for complex objects
                properties.put(key, value.toString());
            }
        }

        return properties;
    }

    /**
     * Convert property value from JanusGraph to Java type
     *
     * @param value The property value
     * @return Converted value
     */
    private static Object convertPropertyValue(Object value) {
        if (value == null) {
            return null;
        }

        // Handle arrays
        if (value.getClass().isArray()) {
            Class<?> componentType = value.getClass().getComponentType();
            
            if (componentType == String.class) {
                return (String[]) value;
            } else if (componentType == Integer.class || componentType == int.class) {
                return value;
            } else if (componentType == Long.class || componentType == long.class) {
                return value;
            } else if (componentType == Double.class || componentType == double.class) {
                return value;
            } else if (componentType == Boolean.class || componentType == boolean.class) {
                return value;
            }
        }

        return value;
    }

    /**
     * Check if value is a simple type supported by JanusGraph
     *
     * @param value The value to check
     * @return true if simple type
     */
    private static boolean isSimpleType(Object value) {
        return value instanceof String
                || value instanceof Integer
                || value instanceof Long
                || value instanceof Double
                || value instanceof Float
                || value instanceof Boolean
                || value instanceof Short
                || value instanceof Byte
                || value instanceof Character;
    }

    /**
     * Convert a List to array for JanusGraph storage
     *
     * @param list The list to convert
     * @return Array representation
     */
    private static Object convertListToArray(List<?> list) {
        if (list.isEmpty()) {
            return new String[0];
        }

        Object first = list.get(0);
        if (first instanceof String) {
            return list.toArray(new String[0]);
        } else if (first instanceof Integer) {
            return list.stream().map(o -> (Integer) o).toArray(Integer[]::new);
        } else if (first instanceof Long) {
            return list.stream().map(o -> (Long) o).toArray(Long[]::new);
        } else if (first instanceof Double) {
            return list.stream().map(o -> (Double) o).toArray(Double[]::new);
        } else if (first instanceof Boolean) {
            return list.stream().map(o -> (Boolean) o).toArray(Boolean[]::new);
        } else {
            // Convert to string array
            return list.stream().map(Object::toString).toArray(String[]::new);
        }
    }

    /**
     * Flatten a nested map for storage (simple strategy: convert to JSON-like string)
     *
     * @param map The map to flatten
     * @return Flattened string representation
     */
    private static String flattenMap(Map<?, ?> map) {
        if (map.isEmpty()) {
            return "{}";
        }
        
        // Simple flattening - convert to key=value pairs
        return map.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", ", "{", "}"));
    }

    /**
     * Extract metadata from Edge properties
     *
     * @param edge The edge
     * @return Metadata map
     */
    public static Map<String, Object> extractEdgeMetadata(Edge edge) {
        Map<String, Object> metadata = new HashMap<>();
        
        Iterator<Property<Object>> properties = edge.properties();
        while (properties.hasNext()) {
            Property<Object> property = properties.next();
            String key = property.key();
            Object value = property.value();
            
            // Skip system properties
            if (!key.equals("index")) {
                metadata.put(key, convertPropertyValue(value));
            }
        }
        
        return metadata;
    }

    /**
     * Get the index property from an edge (for sequence ordering)
     *
     * @param edge The edge
     * @return Index value or null
     */
    public static Integer getEdgeIndex(Edge edge) {
        Property<Object> indexProp = edge.property("index");
        if (indexProp.isPresent()) {
            Object value = indexProp.value();
            if (value instanceof Integer) {
                return (Integer) value;
            } else if (value instanceof Number) {
                return ((Number) value).intValue();
            }
        }
        return null;
    }
}
