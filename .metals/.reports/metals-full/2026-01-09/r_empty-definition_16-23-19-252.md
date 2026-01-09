error id: file://<WORKSPACE>/ontology-engine/graph-dac-api/src/main/java/org/sunbird/graph/service/operation/AsyncNodeOperations.java:java/util/stream/Stream#toList().
file://<WORKSPACE>/ontology-engine/graph-dac-api/src/main/java/org/sunbird/graph/service/operation/AsyncNodeOperations.java
empty definition using pc, found symbol in pc: java/util/stream/Stream#toList().
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 4409
uri: file://<WORKSPACE>/ontology-engine/graph-dac-api/src/main/java/org/sunbird/graph/service/operation/AsyncNodeOperations.java
text:
```scala
package org.sunbird.graph.service.operation;

import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.sunbird.graph.dac.model.Node;
import org.sunbird.graph.dac.model.Relation;
import org.sunbird.telemetry.logger.TelemetryManager;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * AsyncNodeOperations - Asynchronous wrapper for JanusGraph operations
 * Replaces Neo4j async operations using CompletableFuture pattern
 */
public class AsyncNodeOperations {

    private static final ExecutorService executorService = 
        Executors.newFixedThreadPool(20, new ThreadFactory() {
            private int counter = 0;
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setName("janusgraph-async-" + (counter++));
                thread.setDaemon(true);
                return thread;
            }
        });

    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 100;

    /**
     * Async create node
     */
    public static CompletableFuture<Node> createNodeAsync(String graphId, Node node) {
        return executeWithRetry(() -> NodeOperations.createNode(graphId, node));
    }

    /**
     * Async upsert node
     */
    public static CompletableFuture<Node> upsertNodeAsync(String graphId, Node node) {
        return executeWithRetry(() -> NodeOperations.upsertNode(graphId, node));
    }

    /**
     * Async update node
     */
    public static CompletableFuture<Node> updateNodeAsync(String graphId, String nodeId, 
                                                          Map<String, Object> metadata) {
        return executeWithRetry(() -> NodeOperations.updateNode(graphId, nodeId, metadata));
    }

    /**
     * Async delete node
     */
    public static CompletableFuture<Void> deleteNodeAsync(String graphId, String nodeId) {
        return executeWithRetry(() -> {
            NodeOperations.deleteNode(graphId, nodeId);
            return null;
        });
    }

    /**
     * Async get node by unique ID
     */
    public static CompletableFuture<Node> getNodeByUniqueIdAsync(String graphId, String nodeId) {
        return executeWithRetry(() -> NodeOperations.getNodeByUniqueId(graphId, nodeId));
    }

    /**
     * Async get nodes by unique IDs
     */
    public static CompletableFuture<List<Node>> getNodesByUniqueIdsAsync(String graphId, 
                                                                          List<String> nodeIds) {
        return executeWithRetry(() -> NodeOperations.getNodesByUniqueIds(graphId, nodeIds));
    }

    /**
     * Async create relation
     */
    public static CompletableFuture<Relation> createRelationAsync(String graphId, String startNodeId,
                                                                   String relationType, String endNodeId,
                                                                   Map<String, Object> metadata) {
        return executeWithRetry(() -> 
            RelationOperations.createRelation(graphId, startNodeId, relationType, endNodeId, metadata)
        );
    }

    /**
     * Async update relation
     */
    public static CompletableFuture<Relation> updateRelationAsync(String graphId, String startNodeId,
                                                                   String relationType, String endNodeId,
                                                                   Map<String, Object> metadata) {
        return executeWithRetry(() -> 
            RelationOperations.updateRelation(graphId, startNodeId, relationType, endNodeId, metadata)
        );
    }

    /**
     * Async delete relation
     */
    public static CompletableFuture<Void> deleteRelationAsync(String graphId, String startNodeId,
                                                               String relationType, String endNodeId) {
        return executeWithRetry(() -> {
            RelationOperations.deleteRelation(graphId, startNodeId, relationType, endNodeId);
            return null;
        });
    }

    /**
     * Batch async node creation
     */
    public static CompletableFuture<List<Node>> createNodesBatchAsync(String graphId, List<Node> nodes) {
        List<CompletableFuture<Node>> futures = nodes.stream()
                .map(node -> createNodeAsync(graphId, node))
                .@@toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .toList());
    }

    /**
     * Batch async relation creation
     */
    public static CompletableFuture<List<Relation>> createRelationsBatchAsync(
            String graphId, List<Map<String, Object>> relationData) {
        
        List<CompletableFuture<Relation>> futures = relationData.stream()
                .map(data -> {
                    String startNodeId = (String) data.get("startNodeId");
                    String endNodeId = (String) data.get("endNodeId");
                    String relationType = (String) data.get("relationType");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> metadata = (Map<String, Object>) data.get("metadata");
                    
                    return createRelationAsync(graphId, startNodeId, relationType, endNodeId, metadata);
                })
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .toList());
    }

    /**
     * Execute operation with exponential backoff retry
     */
    private static <T> CompletableFuture<T> executeWithRetry(Callable<T> operation) {
        return CompletableFuture.supplyAsync(() -> {
            int retryCount = 0;
            long delay = INITIAL_RETRY_DELAY_MS;
            
            while (true) {
                try {
                    return operation.call();
                    
                } catch (Exception e) {
                    retryCount++;
                    
                    if (retryCount >= MAX_RETRIES) {
                        TelemetryManager.error("Operation failed after " + MAX_RETRIES + " retries: " + 
                                             e.getMessage(), e);
                        throw new CompletionException(e);
                    }
                    
                    // Check if error is retryable (CQL transient failures)
                    if (isRetryableException(e)) {
                        TelemetryManager.log("Retrying operation (attempt " + (retryCount + 1) + 
                                           "/" + MAX_RETRIES + ") after " + delay + "ms");
                        
                        try {
                            Thread.sleep(delay);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new CompletionException(ie);
                        }
                        
                        // Exponential backoff
                        delay *= 2;
                        
                    } else {
                        // Non-retryable exception, fail immediately
                        TelemetryManager.error("Non-retryable error: " + e.getMessage(), e);
                        throw new CompletionException(e);
                    }
                }
            }
        }, executorService);
    }

    /**
     * Check if exception is retryable
     */
    private static boolean isRetryableException(Exception e) {
        // CQL transient failures, connection timeouts, etc.
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        
        message = message.toLowerCase();
        return message.contains("timeout") || 
               message.contains("unavailable") ||
               message.contains("overloaded") ||
               message.contains("connection") ||
               message.contains("temporary");
    }

    /**
     * Shutdown executor service
     */
    public static void shutdown() {
        TelemetryManager.log("Shutting down AsyncNodeOperations executor service");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                    TelemetryManager.error("ExecutorService did not terminate");
                }
            }
        } catch (InterruptedException ie) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Wait for all pending operations
     */
    public static void awaitCompletion(List<CompletableFuture<?>> futures, long timeoutSeconds) {
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(timeoutSeconds, TimeUnit.SECONDS);
            TelemetryManager.log("All async operations completed successfully");
            
        } catch (TimeoutException e) {
            TelemetryManager.error("Async operations timed out after " + timeoutSeconds + " seconds", e);
            throw new CompletionException(e);
            
        } catch (Exception e) {
            TelemetryManager.error("Error waiting for async operations: " + e.getMessage(), e);
            throw new CompletionException(e);
        }
    }

    /**
     * Get executor statistics
     */
    public static Map<String, Object> getExecutorStatistics() {
        if (executorService instanceof ThreadPoolExecutor) {
            ThreadPoolExecutor tpe = (ThreadPoolExecutor) executorService;
            return Map.of(
                "activeCount", tpe.getActiveCount(),
                "poolSize", tpe.getPoolSize(),
                "queueSize", tpe.getQueue().size(),
                "completedTaskCount", tpe.getCompletedTaskCount(),
                "taskCount", tpe.getTaskCount()
            );
        }
        return Map.of();
    }
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: java/util/stream/Stream#toList().