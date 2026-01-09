package org.sunbird.graph.service.util;

import org.apache.commons.lang3.StringUtils;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.janusgraph.core.JanusGraph;
import org.janusgraph.core.JanusGraphFactory;
import org.sunbird.common.Platform;
import org.sunbird.common.exception.ServerException;
import org.sunbird.graph.dac.util.JanusGraphSchemaManager;
import org.sunbird.graph.service.common.DACConfigurationConstants;
import org.sunbird.graph.service.common.DACErrorCodeConstants;
import org.sunbird.graph.service.common.DACErrorMessageConstants;
import org.sunbird.telemetry.logger.TelemetryManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JanusGraphDriverUtil - Manages JanusGraph instance creation and caching
 * Replaces Neo4j DriverUtil with JanusGraph + Yugabyte CQL backend
 *
 * @author Sunbird
 */
public class JanusGraphDriverUtil {

    private static final Map<String, JanusGraph> graphCache = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> schemaInitialized = new ConcurrentHashMap<>();

    // Configuration keys
    private static final String CQL_CONTACT_POINTS_KEY = "graph.cql.contact-points";
    private static final String CQL_PORT_KEY = "graph.cql.port";
    private static final String CQL_KEYSPACE_PREFIX_KEY = "graph.cql.keyspace-prefix";
    private static final String CQL_REPLICATION_FACTOR_KEY = "graph.cql.replication-factor";
    private static final String CQL_READ_CONSISTENCY_KEY = "graph.cql.read-consistency-level";
    private static final String DB_CACHE_ENABLED_KEY = "graph.db-cache.enabled";
    private static final String DB_CACHE_SIZE_KEY = "graph.db-cache.size";

    // Default values
    private static final String DEFAULT_CONTACT_POINTS = "localhost";
    private static final int DEFAULT_PORT = 9042;
    private static final String DEFAULT_KEYSPACE_PREFIX = "janusgraph";
    private static final int DEFAULT_REPLICATION_FACTOR = 1;
    private static final String DEFAULT_READ_CONSISTENCY = "QUORUM";
    private static final boolean DEFAULT_CACHE_ENABLED = true;
    private static final double DEFAULT_CACHE_SIZE = 0.5;

    /**
     * Get or create a JanusGraph instance for the given graphId
     *
     * @param graphId The graph identifier
     * @return JanusGraph instance
     */
    public static JanusGraph getGraph(String graphId) {
        if (StringUtils.isBlank(graphId)) {
            throw new ServerException(DACErrorCodeConstants.INVALID_GRAPH.name(),
                    DACErrorMessageConstants.INVALID_GRAPH_ID + " | [Graph Id: " + graphId + "]");
        }

        TelemetryManager.log("Get JanusGraph instance for Graph Id: " + graphId);

        // Return cached instance if available
        JanusGraph graph = graphCache.get(graphId);
        if (graph != null && graph.isOpen()) {
            TelemetryManager.log("Returning cached JanusGraph instance for: " + graphId);
            return graph;
        }

        // Create new instance
        synchronized (JanusGraphDriverUtil.class) {
            // Double-check after acquiring lock
            graph = graphCache.get(graphId);
            if (graph != null && graph.isOpen()) {
                return graph;
            }

            TelemetryManager.log("Creating new JanusGraph instance for Graph Id: " + graphId);
            graph = createGraph(graphId);
            graphCache.put(graphId, graph);

            // Initialize schema if not already done
            initializeSchema(graphId, graph);

            // Register shutdown hook
            registerShutdownHook(graphId, graph);

            return graph;
        }
    }

    /**
     * Get a GraphTraversalSource for executing Gremlin queries
     *
     * @param graphId The graph identifier
     * @return GraphTraversalSource for query execution
     */
    public static GraphTraversalSource getTraversal(String graphId) {
        JanusGraph graph = getGraph(graphId);
        return graph.traversal();
    }

    /**
     * Create a new JanusGraph instance with CQL backend configuration
     *
     * @param graphId The graph identifier
     * @return Configured JanusGraph instance
     */
    private static JanusGraph createGraph(String graphId) {
        try {
            JanusGraphFactory.Builder builder = JanusGraphFactory.build();

            // Storage backend configuration - Yugabyte CQL
            builder.set("storage.backend", "cql");
            builder.set("storage.hostname", getContactPoints());
            builder.set("storage.port", getPort());
            builder.set("storage.cql.keyspace", getKeyspace(graphId));

            // CQL-specific configuration
            builder.set("storage.cql.replication-factor", getReplicationFactor());
            builder.set("storage.cql.read-consistency-level", getReadConsistencyLevel());

            // Cache configuration for performance
            if (isCacheEnabled()) {
                builder.set("cache.db-cache", true);
                builder.set("cache.db-cache-size", getCacheSize());
                builder.set("cache.db-cache-clean-wait", 20);
                builder.set("cache.db-cache-time", 180000);
            }

            // Query optimization
            builder.set("query.batch", true);
            builder.set("query.force-index", false);

            // Schema configuration
            builder.set("schema.default", "none");

            TelemetryManager.log("JanusGraph configuration - Keyspace: " + getKeyspace(graphId) +
                    ", Contact Points: " + getContactPoints() + ", Port: " + getPort());

            JanusGraph graph = builder.open();
            TelemetryManager.log("Successfully created JanusGraph instance for: " + graphId);

            return graph;

        } catch (Exception e) {
            TelemetryManager.error("Failed to create JanusGraph instance for " + graphId + ": " + e.getMessage(), e);
            throw new ServerException("ERR_GRAPH_INIT_FAILED",
                    "Failed to initialize JanusGraph for graph: " + graphId, e);
        }
    }

    /**
     * Initialize schema for the graph
     */
    private static void initializeSchema(String graphId, JanusGraph graph) {
        String schemaKey = graphId + "_schema";
        if (!schemaInitialized.containsKey(schemaKey)) {
            try {
                TelemetryManager.log("Initializing schema for graph: " + graphId);
                JanusGraphSchemaManager.initializeGraphSchema(graphId, graph);
                schemaInitialized.put(schemaKey, true);
                TelemetryManager.log("Schema initialized successfully for: " + graphId);
            } catch (Exception e) {
                TelemetryManager.warn("Schema initialization failed for " + graphId + ": " + e.getMessage());
                // Don't throw exception - schema might already exist
            }
        }
    }

    /**
     * Close a specific graph instance
     *
     * @param graphId The graph identifier
     */
    public static void closeGraph(String graphId) {
        JanusGraph graph = graphCache.remove(graphId);
        if (graph != null && graph.isOpen()) {
            try {
                TelemetryManager.log("Closing JanusGraph instance for: " + graphId);
                graph.close();
            } catch (Exception e) {
                TelemetryManager.error("Error closing graph " + graphId + ": " + e.getMessage(), e);
            }
        }
    }

    /**
     * Close all graph instances
     */
    public static void closeAllGraphs() {
        TelemetryManager.log("Closing all JanusGraph instances...");
        for (Map.Entry<String, JanusGraph> entry : graphCache.entrySet()) {
            try {
                if (entry.getValue() != null && entry.getValue().isOpen()) {
                    entry.getValue().close();
                    TelemetryManager.log("Closed graph: " + entry.getKey());
                }
            } catch (Exception e) {
                TelemetryManager.error("Error closing graph " + entry.getKey() + ": " + e.getMessage(), e);
            }
        }
        graphCache.clear();
        schemaInitialized.clear();
    }

    /**
     * Register shutdown hook to close graph on JVM exit
     */
    private static void registerShutdownHook(String graphId, JanusGraph graph) {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                TelemetryManager.log("Shutdown hook: Closing JanusGraph for " + graphId);
                if (graph != null && graph.isOpen()) {
                    try {
                        graph.close();
                    } catch (Exception e) {
                        TelemetryManager.error("Error in shutdown hook for " + graphId, e);
                    }
                }
            }
        });
    }

    // Configuration getters with fallback to defaults

    private static String getContactPoints() {
        if (Platform.config.hasPath(CQL_CONTACT_POINTS_KEY)) {
            return Platform.config.getString(CQL_CONTACT_POINTS_KEY);
        }
        return DEFAULT_CONTACT_POINTS;
    }

    private static int getPort() {
        if (Platform.config.hasPath(CQL_PORT_KEY)) {
            return Platform.config.getInt(CQL_PORT_KEY);
        }
        return DEFAULT_PORT;
    }

    private static String getKeyspace(String graphId) {
        String prefix = DEFAULT_KEYSPACE_PREFIX;
        if (Platform.config.hasPath(CQL_KEYSPACE_PREFIX_KEY)) {
            prefix = Platform.config.getString(CQL_KEYSPACE_PREFIX_KEY);
        }
        return prefix + "_" + graphId.toLowerCase().replaceAll("[^a-z0-9_]", "_");
    }

    private static int getReplicationFactor() {
        if (Platform.config.hasPath(CQL_REPLICATION_FACTOR_KEY)) {
            return Platform.config.getInt(CQL_REPLICATION_FACTOR_KEY);
        }
        return DEFAULT_REPLICATION_FACTOR;
    }

    private static String getReadConsistencyLevel() {
        if (Platform.config.hasPath(CQL_READ_CONSISTENCY_KEY)) {
            return Platform.config.getString(CQL_READ_CONSISTENCY_KEY);
        }
        return DEFAULT_READ_CONSISTENCY;
    }

    private static boolean isCacheEnabled() {
        if (Platform.config.hasPath(DB_CACHE_ENABLED_KEY)) {
            return Platform.config.getBoolean(DB_CACHE_ENABLED_KEY);
        }
        return DEFAULT_CACHE_ENABLED;
    }

    private static double getCacheSize() {
        if (Platform.config.hasPath(DB_CACHE_SIZE_KEY)) {
            return Platform.config.getDouble(DB_CACHE_SIZE_KEY);
        }
        return DEFAULT_CACHE_SIZE;
    }

    /**
     * Clear the graph cache (useful for testing)
     */
    public static void clearCache() {
        graphCache.clear();
        schemaInitialized.clear();
    }

    /**
     * Check if a graph instance exists and is open
     *
     * @param graphId The graph identifier
     * @return true if graph exists and is open
     */
    public static boolean isGraphOpen(String graphId) {
        JanusGraph graph = graphCache.get(graphId);
        return graph != null && graph.isOpen();
    }
}
