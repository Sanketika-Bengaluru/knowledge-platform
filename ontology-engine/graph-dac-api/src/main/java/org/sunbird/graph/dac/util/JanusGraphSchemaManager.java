package org.sunbird.graph.dac.util;

import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.janusgraph.core.Cardinality;
import org.janusgraph.core.EdgeLabel;
import org.janusgraph.core.JanusGraph;
import org.janusgraph.core.Multiplicity;
import org.janusgraph.core.PropertyKey;
import org.janusgraph.core.VertexLabel;
import org.janusgraph.core.schema.JanusGraphIndex;
import org.janusgraph.core.schema.JanusGraphManagement;
import org.sunbird.common.exception.ServerException;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * JanusGraphSchemaManager - Manages schema initialization and management for JanusGraph
 * Defines PropertyKeys, VertexLabels, EdgeLabels, and Indexes
 * 
 * @author Sunbird
 */
public class JanusGraphSchemaManager {
    
    private static final Logger logger = Logger.getLogger(JanusGraphSchemaManager.class.getName());
    
    // System property keys
    public static final String IL_UNIQUE_ID = "IL_UNIQUE_ID";
    public static final String IL_FUNC_OBJECT_TYPE = "IL_FUNC_OBJECT_TYPE";
    public static final String IL_SYS_NODE_TYPE = "IL_SYS_NODE_TYPE";
    public static final String SYS_INTERNAL_LAST_UPDATED_ON = "SYS_INTERNAL_LAST_UPDATED_ON";
    public static final String VERSION_KEY = "versionKey";
    public static final String CREATED_ON = "createdOn";
    public static final String LAST_UPDATED_ON = "lastUpdatedOn";
    
    // Common search property keys
    public static final String OBJECT_TYPE = "objectType";
    public static final String STATUS = "status";
    public static final String CONTENT_TYPE = "contentType";
    public static final String CHANNEL = "channel";
    public static final String FRAMEWORK = "framework";
    public static final String NAME = "name";
    public static final String CODE = "code";
    public static final String MIME_TYPE = "mimeType";
    public static final String CREATED_BY = "createdBy";
    public static final String LAST_UPDATED_BY = "lastUpdatedBy";
    
    // Edge labels
    public static final String EDGE_ASSOCIATED_TO = "associatedTo";
    public static final String EDGE_HAS_SEQUENCE_MEMBER = "hasSequenceMember";
    public static final String EDGE_INDEX_PROPERTY = "index";
    
    // Vertex labels - common graph domains
    public static final List<String> DEFAULT_VERTEX_LABELS = Arrays.asList(
        "domain", "language", "content", "collection", "assessmentitem", 
        "channel", "framework", "category", "term", "asset", "event",
        "eventset", "itemset", "license", "lock", "objectcategory",
        "objectcategorydefinition", "categoryinstance", "dialcode"
    );
    
    // Track initialized graphs to avoid redundant schema creation
    private static final Map<String, Boolean> initializedGraphs = new ConcurrentHashMap<>();
    
    /**
     * Initialize schema for a specific graph with default configuration
     * Creates PropertyKeys, VertexLabels, EdgeLabels, and Indexes
     * 
     * @param graphId The graph identifier
     * @param graph The JanusGraph instance
     */
    public static void initializeGraphSchema(String graphId, JanusGraph graph) {
        initializeGraphSchema(graphId, graph, DEFAULT_VERTEX_LABELS, null);
    }
    
    /**
     * Initialize schema for a specific graph with custom vertex labels
     * 
     * @param graphId The graph identifier
     * @param graph The JanusGraph instance
     * @param vertexLabels List of vertex labels to create
     * @param additionalProperties Additional property keys to create beyond defaults
     */
    public static void initializeGraphSchema(String graphId, JanusGraph graph, 
                                            List<String> vertexLabels, 
                                            List<String> additionalProperties) {
        if (StringUtils.isBlank(graphId) || graph == null) {
            throw new ServerException("ERR_INVALID_GRAPH_CONFIG", 
                "GraphId and JanusGraph instance are required for schema initialization");
        }
        
        // Check if schema already initialized for this graph
        String cacheKey = graphId + "_" + graph.toString();
        if (initializedGraphs.containsKey(cacheKey)) {
            logger.info("Schema already initialized for graph: " + graphId);
            return;
        }
        
        logger.info("Initializing JanusGraph schema for graph: " + graphId);
        
        JanusGraphManagement mgmt = graph.openManagement();
        
        try {
            // Step 1: Create PropertyKeys
            createPropertyKeys(mgmt, additionalProperties);
            
            // Step 2: Create VertexLabels
            createVertexLabels(mgmt, vertexLabels);
            
            // Step 3: Create EdgeLabels
            createEdgeLabels(mgmt);
            
            // Step 4: Create Indexes
            createIndexes(mgmt, vertexLabels);
            
            // Commit schema changes
            mgmt.commit();
            
            // Mark as initialized
            initializedGraphs.put(cacheKey, true);
            
            logger.info("Successfully initialized schema for graph: " + graphId);
            
        } catch (Exception e) {
            if (mgmt != null && mgmt.isOpen()) {
                mgmt.rollback();
            }
            logger.severe("Failed to initialize schema for graph " + graphId + ": " + e.getMessage());
            throw new ServerException("ERR_SCHEMA_INITIALIZATION_FAILED", 
                "Failed to initialize JanusGraph schema: " + e.getMessage(), e);
        }
    }
    
    /**
     * Create PropertyKeys for system and common properties
     */
    private static void createPropertyKeys(JanusGraphManagement mgmt, List<String> additionalProperties) {
        logger.info("Creating PropertyKeys...");
        
        // System property keys
        createPropertyKeyIfNotExists(mgmt, IL_UNIQUE_ID, String.class, Cardinality.SINGLE);
        createPropertyKeyIfNotExists(mgmt, IL_FUNC_OBJECT_TYPE, String.class, Cardinality.SINGLE);
        createPropertyKeyIfNotExists(mgmt, IL_SYS_NODE_TYPE, String.class, Cardinality.SINGLE);
        createPropertyKeyIfNotExists(mgmt, SYS_INTERNAL_LAST_UPDATED_ON, String.class, Cardinality.SINGLE);
        createPropertyKeyIfNotExists(mgmt, VERSION_KEY, String.class, Cardinality.SINGLE);
        createPropertyKeyIfNotExists(mgmt, CREATED_ON, String.class, Cardinality.SINGLE);
        createPropertyKeyIfNotExists(mgmt, LAST_UPDATED_ON, String.class, Cardinality.SINGLE);
        
        // Common search property keys
        createPropertyKeyIfNotExists(mgmt, OBJECT_TYPE, String.class, Cardinality.SINGLE);
        createPropertyKeyIfNotExists(mgmt, STATUS, String.class, Cardinality.SINGLE);
        createPropertyKeyIfNotExists(mgmt, CONTENT_TYPE, String.class, Cardinality.SINGLE);
        createPropertyKeyIfNotExists(mgmt, CHANNEL, String.class, Cardinality.SINGLE);
        createPropertyKeyIfNotExists(mgmt, FRAMEWORK, String.class, Cardinality.SINGLE);
        createPropertyKeyIfNotExists(mgmt, NAME, String.class, Cardinality.SINGLE);
        createPropertyKeyIfNotExists(mgmt, CODE, String.class, Cardinality.SINGLE);
        createPropertyKeyIfNotExists(mgmt, MIME_TYPE, String.class, Cardinality.SINGLE);
        createPropertyKeyIfNotExists(mgmt, CREATED_BY, String.class, Cardinality.SINGLE);
        createPropertyKeyIfNotExists(mgmt, LAST_UPDATED_BY, String.class, Cardinality.SINGLE);
        
        // Edge property for sequence ordering
        createPropertyKeyIfNotExists(mgmt, EDGE_INDEX_PROPERTY, Integer.class, Cardinality.SINGLE);
        
        // Additional properties if provided
        if (additionalProperties != null && !additionalProperties.isEmpty()) {
            for (String propKey : additionalProperties) {
                createPropertyKeyIfNotExists(mgmt, propKey, String.class, Cardinality.SINGLE);
            }
        }
        
        logger.info("PropertyKeys creation completed");
    }
    
    /**
     * Create a PropertyKey if it doesn't exist
     */
    private static void createPropertyKeyIfNotExists(JanusGraphManagement mgmt, String keyName, 
                                                     Class<?> dataType, Cardinality cardinality) {
        if (!mgmt.containsPropertyKey(keyName)) {
            PropertyKey key = mgmt.makePropertyKey(keyName)
                .dataType(dataType)
                .cardinality(cardinality)
                .make();
            logger.fine("Created PropertyKey: " + keyName);
        } else {
            logger.fine("PropertyKey already exists: " + keyName);
        }
    }
    
    /**
     * Create VertexLabels for graph domains
     */
    private static void createVertexLabels(JanusGraphManagement mgmt, List<String> vertexLabels) {
        logger.info("Creating VertexLabels...");
        
        List<String> labelsToCreate = vertexLabels != null ? vertexLabels : DEFAULT_VERTEX_LABELS;
        
        for (String label : labelsToCreate) {
            if (!mgmt.containsVertexLabel(label)) {
                VertexLabel vLabel = mgmt.makeVertexLabel(label).make();
                logger.fine("Created VertexLabel: " + label);
            } else {
                logger.fine("VertexLabel already exists: " + label);
            }
        }
        
        logger.info("VertexLabels creation completed");
    }
    
    /**
     * Create EdgeLabels for relationships
     */
    private static void createEdgeLabels(JanusGraphManagement mgmt) {
        logger.info("Creating EdgeLabels...");
        
        // associatedTo - generic association relationship
        if (!mgmt.containsEdgeLabel(EDGE_ASSOCIATED_TO)) {
            EdgeLabel associatedTo = mgmt.makeEdgeLabel(EDGE_ASSOCIATED_TO)
                .multiplicity(Multiplicity.MULTI)
                .make();
            logger.fine("Created EdgeLabel: " + EDGE_ASSOCIATED_TO);
        } else {
            logger.fine("EdgeLabel already exists: " + EDGE_ASSOCIATED_TO);
        }
        
        // hasSequenceMember - ordered sequence relationship (for collections)
        if (!mgmt.containsEdgeLabel(EDGE_HAS_SEQUENCE_MEMBER)) {
            EdgeLabel hasSequenceMember = mgmt.makeEdgeLabel(EDGE_HAS_SEQUENCE_MEMBER)
                .multiplicity(Multiplicity.MULTI)
                .make();
            logger.fine("Created EdgeLabel: " + EDGE_HAS_SEQUENCE_MEMBER);
        } else {
            logger.fine("EdgeLabel already exists: " + EDGE_HAS_SEQUENCE_MEMBER);
        }
        
        logger.info("EdgeLabels creation completed");
    }
    
    /**
     * Create indexes for efficient querying
     */
    private static void createIndexes(JanusGraphManagement mgmt, List<String> vertexLabels) {
        logger.info("Creating Indexes...");
        
        PropertyKey uniqueIdKey = mgmt.getPropertyKey(IL_UNIQUE_ID);
        if (uniqueIdKey == null) {
            throw new ServerException("ERR_PROPERTY_KEY_NOT_FOUND", 
                "PropertyKey IL_UNIQUE_ID must be created before indexes");
        }
        
        List<String> labelsToIndex = vertexLabels != null ? vertexLabels : DEFAULT_VERTEX_LABELS;
        
        // Create unique composite index on IL_UNIQUE_ID per VertexLabel
        for (String label : labelsToIndex) {
            String indexName = "byUniqueId_" + label;
            if (mgmt.getGraphIndex(indexName) == null) {
                VertexLabel vLabel = mgmt.getVertexLabel(label);
                if (vLabel != null) {
                    mgmt.buildIndex(indexName, Vertex.class)
                        .addKey(uniqueIdKey)
                        .indexOnly(vLabel)
                        .unique()
                        .buildCompositeIndex();
                    logger.fine("Created unique index: " + indexName);
                }
            } else {
                logger.fine("Index already exists: " + indexName);
            }
        }
        
        // Create composite indexes for common search properties
        createCompositeIndexIfNotExists(mgmt, "byObjectType", OBJECT_TYPE);
        createCompositeIndexIfNotExists(mgmt, "byStatus", STATUS);
        createCompositeIndexIfNotExists(mgmt, "byContentType", CONTENT_TYPE);
        createCompositeIndexIfNotExists(mgmt, "byChannel", CHANNEL);
        createCompositeIndexIfNotExists(mgmt, "byFramework", FRAMEWORK);
        createCompositeIndexIfNotExists(mgmt, "byCode", CODE);
        createCompositeIndexIfNotExists(mgmt, "byMimeType", MIME_TYPE);
        
        logger.info("Indexes creation completed");
    }
    
    /**
     * Create a composite index if it doesn't exist
     */
    private static void createCompositeIndexIfNotExists(JanusGraphManagement mgmt, 
                                                        String indexName, 
                                                        String propertyKeyName) {
        if (mgmt.getGraphIndex(indexName) == null) {
            PropertyKey propKey = mgmt.getPropertyKey(propertyKeyName);
            if (propKey != null) {
                mgmt.buildIndex(indexName, Vertex.class)
                    .addKey(propKey)
                    .buildCompositeIndex();
                logger.fine("Created composite index: " + indexName);
            } else {
                logger.warning("Cannot create index " + indexName + 
                              " - PropertyKey " + propertyKeyName + " not found");
            }
        } else {
            logger.fine("Index already exists: " + indexName);
        }
    }
    
    /**
     * Clear the initialization cache for a graph (useful for testing)
     */
    public static void clearInitializationCache(String graphId) {
        initializedGraphs.entrySet().removeIf(entry -> entry.getKey().startsWith(graphId + "_"));
    }
    
    /**
     * Clear all initialization cache
     */
    public static void clearAllInitializationCache() {
        initializedGraphs.clear();
    }
}
