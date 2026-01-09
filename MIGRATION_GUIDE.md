# Neo4j to JanusGraph + Yugabyte Migration Guide

## Executive Summary

This document tracks the migration from **Neo4j 3.3.0** to **JanusGraph 1.0.0** with **Yugabyte CQL backend** for the Sunbird Knowledge Platform.

**Migration Status**: Core Infrastructure Complete (22/75 tasks)  
**Target Completion**: All remaining tasks documented below

---

## Table of Contents

1. [Architecture Changes](#architecture-changes)
2. [Completed Tasks](#completed-tasks)
3. [Pending Implementation](#pending-implementation)
4. [Query Migration Patterns](#query-migration-patterns)
5. [Data Migration Strategy](#data-migration-strategy)
6. [Testing Strategy](#testing-strategy)
7. [Rollback Plan](#rollback-plan)

---

## Architecture Changes

### Database Stack

| Component | Before | After |
|-----------|--------|-------|
| Graph DB | Neo4j 3.3.0 | JanusGraph 1.0.0 |
| Storage Backend | N/A (embedded) | Yugabyte CQL (port 9042) |
| Query Language | Cypher | Gremlin (Apache Tinkerpop) |
| Driver | neo4j-java-driver 1.7.5 | gremlin-driver 3.6.2 |
| Connection Pattern | Session-based | Traversal Source |

### Schema Management

**Neo4j Approach**: Schema-optional, dynamic labels  
**JanusGraph Approach**: Explicit schema with PropertyKeys, VertexLabels, EdgeLabels, and Composite Indexes

**Schema Initialization**: Automatic on first graph access via `JanusGraphSchemaManager.initializeGraphSchema()`

### Key Design Decisions

1. **Connection Pooling**: ConcurrentHashMap-based graph caching per graphId
2. **Transaction Management**: Explicit commit/rollback replacing Neo4j auto-commit
3. **Type System**: Custom type conversion layer (TinkerpopNodeUtil) for compatibility
4. **Backward Compatibility**: Dual Neo4j/Tinkerpop constructors in Relation.java during transition

---

## Completed Tasks

### ✅ Task 1-7: Schema Management
- **File**: [graph-dac-api/src/main/java/org/sunbird/graph/dac/util/JanusGraphSchemaManager.java](ontology-engine/graph-dac-api/src/main/java/org/sunbird/graph/dac/util/JanusGraphSchemaManager.java)
- **Status**: Complete (365 lines)
- **Features**:
  - 17 PropertyKeys (IL_UNIQUE_ID, IL_FUNC_OBJECT_TYPE, IL_SYS_NODE_TYPE + 14 common properties)
  - 19 VertexLabels (domain, content, collection, concept, asset, etc.)
  - 2 EdgeLabels (associatedTo, hasSequenceMember with index property)
  - Unique composite indexes on IL_UNIQUE_ID per vertex label
  - Search indexes on objectType, status, contentType, channel, framework
- **Code Example**:
```java
PropertyKey uniqueIdKey = mgmt.makePropertyKey("IL_UNIQUE_ID")
    .dataType(String.class)
    .make();

VertexLabel contentLabel = mgmt.makeVertexLabel("Content").make();

mgmt.buildIndex("byUniqueId_Content", Vertex.class)
    .addKey(uniqueIdKey)
    .indexOnly(contentLabel)
    .unique()
    .buildCompositeIndex();
```

### ✅ Task 8: graph-dac-api Dependencies
- **File**: [ontology-engine/graph-dac-api/pom.xml](ontology-engine/graph-dac-api/pom.xml)
- **Status**: Complete
- **Changes**:
  - ❌ Removed: neo4j-java-driver, neo4j-graphdb-api, neo4j, neo4j-bolt, neo4j-cypher
  - ✅ Added: janusgraph-core:1.0.0, janusgraph-cql:1.0.0, gremlin-core:3.6.2, gremlin-driver:3.6.2
  - ✅ Test scope: janusgraph-inmemory:1.0.0

### ✅ Task 9: graph-engine_2.13 Dependencies
- **File**: [ontology-engine/graph-engine_2.13/pom.xml](ontology-engine/graph-engine_2.13/pom.xml)
- **Status**: Complete
- **Changes**: Removed all Neo4j test dependencies (neo4j-bolt, neo4j-kernel, neo4j-lucene-index), added janusgraph-inmemory for testing

### ✅ Task 10: content-service Dependencies
- **File**: [content-api/content-service/pom.xml](content-api/content-service/pom.xml)
- **Status**: Complete
- **Changes**: Removed transitive Neo4j dependencies, cleaned up unused graph DB imports

### ✅ Task 11-14: JanusGraph Driver Utility
- **File**: [graph-service/util/JanusGraphDriverUtil.java](ontology-engine/graph-dac-api/src/main/java/org/sunbird/graph/service/util/JanusGraphDriverUtil.java)
- **Status**: Complete (285 lines)
- **Features**:
  - `getGraph(graphId)`: Returns cached JanusGraph instance with CQL configuration
  - `getTraversal(graphId)`: Returns GraphTraversalSource for query execution
  - `closeAllGraphs()`: Cleanup on application shutdown
  - Connection config: storage.backend=cql, storage.hostname=localhost, storage.port=9042
  - Keyspace isolation: janusgraph_{graphId} per graph
- **Code Example**:
```java
JanusGraph graph = JanusGraphFactory.build()
    .set("storage.backend", "cql")
    .set("storage.hostname", "localhost")
    .set("storage.port", 9042)
    .set("storage.cql.keyspace", "janusgraph_" + graphId)
    .open();

graphCache.put(graphId, graph);
JanusGraphSchemaManager.initializeGraphSchema(graph);
```

### ✅ Task 15-22: Gremlin Operations Utility
- **File**: [graph-dac-api/src/main/java/org/sunbird/graph/dac/util/GremlinOperationsUtil.java](ontology-engine/graph-dac-api/src/main/java/org/sunbird/graph/dac/util/GremlinOperationsUtil.java)
- **Status**: Complete (430 lines)
- **Operations**:
  - `createVertex()`: Vertex creation with property validation
  - `upsertVertex()`: Merge pattern with ON CREATE/ON MATCH logic
  - `updateVertexProperties()`: Bulk property updates
  - `deleteVertex()`: Vertex deletion with transaction handling
  - `createEdge()`: Relation creation with metadata
  - `deleteEdge()`: Edge removal with vertex validation
  - `bulkImportVertices()`: Batched import (500 vertices per commit)
  - `detectCycle()`: Cyclic dependency detection

### ✅ Task 39-43: Type Conversion Layer
- **File**: [graph-dac-api/src/main/java/org/sunbird/graph/dac/util/TinkerpopNodeUtil.java](ontology-engine/graph-dac-api/src/main/java/org/sunbird/graph/dac/util/TinkerpopNodeUtil.java)
- **Status**: Complete (315 lines)
- **Features**:
  - `vertexToNode()`: Converts Vertex to Node with metadata extraction
  - `edgeToRelation()`: Converts Edge to Relation model
  - `prepareVertexProperties()`: Type normalization for JanusGraph compatibility
  - Handles: String, Integer, Long, Double, Boolean, arrays, nested objects
- **File**: [graph-dac-api/src/main/java/org/sunbird/graph/dac/model/Relation.java](ontology-engine/graph-dac-api/src/main/java/org/sunbird/graph/dac/model/Relation.java)
- **Changes**: Added Relation(String graphId, Edge edge) constructor with Vertex helper methods

### ✅ Task 50-52: Docker Configuration
- **File**: [docker-compose.yml](docker-compose.yml)
- **Status**: Complete
- **Changes**:
  - ❌ Removed: sunbird-neo4j service (ports 7473, 7474, 7687)
  - ✅ Added: sunbird-yugabyte service
    - Image: yugabytedb/yugabyte:latest
    - Ports: 7000 (YB-Master), 9000 (Admin UI), 9042 (YCQL), 5433 (YSQL), 12000 (TServer)
    - Command: `yugabyted start --advertise_address=127.0.0.1 --join=127.0.0.1 --ui=false`
  - Changed Cassandra port: 9042 → 9043 (avoid conflict with Yugabyte)

---

## Pending Implementation

### 🔄 Query Migration (Tasks 23-38)

#### NodeQueryGenerationUtil.java (15 methods to migrate)

**Status**: GremlinOperationsUtil created with core operations. Need to update these legacy methods:

1. `generateCreateNodeCypherQuery()` → Use `GremlinOperationsUtil.createVertex()`
2. `generateUpdateNodeCypherQuery()` → Use `GremlinOperationsUtil.updateVertexProperties()`
3. `generateDeleteNodeCypherQuery()` → Use `GremlinOperationsUtil.deleteVertex()`
4. `generateAddPropertyCypherQuery()` → Use `GremlinOperationsUtil.updateVertexProperty()`
5. `generateRemovePropertyCypherQuery()` → Use `GremlinOperationsUtil.removeVertexProperty()`

**Migration Pattern**:
```java
// Before (Cypher)
String query = "MATCH (n:domain {IL_UNIQUE_ID: $uniqueId}) " +
               "SET n.status = $status RETURN n";

// After (Gremlin)
g.V().has("IL_UNIQUE_ID", uniqueId)
    .property("status", status)
    .next();
```

#### SearchQueryGenerationUtil.java (12 methods to migrate)

**Key Challenges**:
1. OPTIONAL MATCH → Two-step traversal with `bothE().has().project()`
2. Dynamic WHERE clauses → Recursive `.has()/.where()/.not()` chains
3. Multi-label queries → Use `.hasLabel(P.within("label1", "label2"))`
4. Result pagination → `.range(skip, limit)`

**Migration Example**:
```java
// Before: OPTIONAL MATCH (n)-[r:associatedTo]->(m) WHERE r.index > 0
// After:
g.V().has("IL_UNIQUE_ID", startId)
    .outE("associatedTo")
    .has("index", P.gt(0))
    .as("relation")
    .inV()
    .as("endNode")
    .select("relation", "endNode")
```

#### GraphQueryGenerationUtil.java (13 methods to migrate)

**Complex Patterns**:
1. Variable-length paths → `repeat(out()).times(maxDepth).emit()`
2. Subgraph extraction → Multiple traversals with result aggregation
3. Transitive relations → `repeat(out()).emit().path()`

### 🔄 Async Operations (Tasks 44-49)

**Files to Update**:
- `NodeAsyncOperations.java` → Replace `CompletionStage<Record>` with `CompletableFuture<Vertex>`
- Create `ExecutorService` thread pool (size 20) for async JanusGraph operations
- Implement retry logic with exponential backoff for CQL transient failures

**Pattern**:
```java
CompletableFuture<Node> future = CompletableFuture.supplyAsync(() -> {
    Vertex vertex = GremlinOperationsUtil.createVertex(graphId, node);
    return TinkerpopNodeUtil.vertexToNode(graphId, vertex);
}, executorService);
```

### 🔄 Kubernetes Configuration (Tasks 53-57)

**Changes Required**:
1. Delete `kubernetes/sunbird-dbs/neo4j/` directory
2. Create `kubernetes/sunbird-dbs/yugabyte/` with StatefulSet (3 replicas) and PVCs
3. Update `kubernetes/content/content-service_application.conf`:
   - Remove: `graph.db.connectionString = "bolt://sunbird-neo4j:7687"`
   - Add: `janusgraph.cql.hostname = "sunbird-yugabyte"`
4. Update Helm charts:
   - `kubernetes/taxonomy/values.yaml`
   - `knowlg-automation/helm_charts/content/values.yaml`
   - `knowlg-automation/helm_charts/search/values.yaml`

### 🔄 Test Infrastructure (Tasks 58-63)

**BaseTest.java Migration**:
```java
// Before
private static EmbeddedTestServer server = new EmbeddedTestServer();

// After
private static JanusGraph embeddedGraph;

@BeforeClass
public static void setupEmbeddedJanusGraph() {
    embeddedGraph = JanusGraphFactory.build()
        .set("storage.backend", "inmemory")
        .open();
    JanusGraphSchemaManager.initializeGraphSchema(embeddedGraph);
}
```

**Test Files to Update**:
- `ontology-engine/graph-engine_2.13/src/test/scala/org/sunbird/graph/BaseSpec.scala`
- `platform-core/platform-common/src/test/java/org/sunbird/common/mgr/BaseManagerTest.java`
- 7+ test files with Cypher assertions → Gremlin assertions

### 🔄 Data Migration (Tasks 64-71)

**Migration Phases**:

1. **Export from Neo4j** (2-4 hours for 1M nodes):
```cypher
CALL apoc.export.graphml.all("export.graphml", {
    useTypes: true,
    storeNodeIds: true,
    readLabels: true
});
```

2. **Transform GraphML → GraphSON** (Python script):
```python
import xml.etree.ElementTree as ET
import json

# Parse GraphML, extract nodes/edges, convert to GraphSON format
# Preserve all properties, labels, and edge directions
```

3. **Import to JanusGraph** (BulkLoaderVertexProgram or batched):
```java
GremlinOperationsUtil.bulkImportVertices(graphId, nodes);
// Batch size: 500 vertices per commit
// Expected time: 3-6 hours for 1M nodes
```

4. **Validation**:
```java
// Count comparison
long neo4jCount = session.run("MATCH (n) RETURN count(n)").single().get(0).asLong();
long janusCount = g.V().count().next();

// Random sampling (100 nodes)
// Verify all properties, relationships, metadata match
```

---

## Query Migration Patterns

### Pattern 1: Simple MATCH → has() Traversal

```java
// Cypher
MATCH (n:Content {IL_UNIQUE_ID: $id}) RETURN n

// Gremlin
g.V().has("IL_UNIQUE_ID", id).next()
```

### Pattern 2: MATCH with WHERE → Predicates

```java
// Cypher
MATCH (n:Content) WHERE n.status = 'Live' AND n.contentType IN ['Story', 'Game'] RETURN n

// Gremlin
g.V().hasLabel("Content")
    .has("status", "Live")
    .has("contentType", P.within("Story", "Game"))
    .toList()
```

### Pattern 3: CREATE → addV()

```java
// Cypher
CREATE (n:Content {IL_UNIQUE_ID: $id, name: $name}) RETURN n

// Gremlin
g.addV("Content")
    .property("IL_UNIQUE_ID", id)
    .property("name", name)
    .next()
```

### Pattern 4: CREATE Relation → addE()

```java
// Cypher
MATCH (a:Content {IL_UNIQUE_ID: $startId}), (b:Content {IL_UNIQUE_ID: $endId})
CREATE (a)-[r:associatedTo {index: $index}]->(b) RETURN r

// Gremlin
g.V().has("IL_UNIQUE_ID", startId)
    .addE("associatedTo")
    .to(__.V().has("IL_UNIQUE_ID", endId))
    .property("index", index)
    .next()
```

### Pattern 5: MERGE (Upsert)

```java
// Cypher
MERGE (n:Content {IL_UNIQUE_ID: $id})
ON CREATE SET n.createdOn = timestamp()
ON MATCH SET n.lastUpdatedOn = timestamp()
RETURN n

// Gremlin
g.V().has("IL_UNIQUE_ID", id)
    .fold()
    .coalesce(
        unfold(),
        __.addV("Content").property("IL_UNIQUE_ID", id)
    )
    .property("lastUpdatedOn", System.currentTimeMillis())
    .next()
```

### Pattern 6: OPTIONAL MATCH

```java
// Cypher
MATCH (n:Content {IL_UNIQUE_ID: $id})
OPTIONAL MATCH (n)-[r:associatedTo]->(m)
RETURN n, collect(r), collect(m)

// Gremlin (two-step)
Vertex node = g.V().has("IL_UNIQUE_ID", id).next();
List<Map<String, Object>> relations = g.V().has("IL_UNIQUE_ID", id)
    .outE("associatedTo")
    .as("r")
    .inV()
    .as("m")
    .select("r", "m")
    .by(valueMap())
    .toList();
```

### Pattern 7: Variable-Length Paths

```java
// Cypher
MATCH (n:Content {IL_UNIQUE_ID: $id})-[*1..5]->(m)
RETURN m

// Gremlin
g.V().has("IL_UNIQUE_ID", id)
    .repeat(out())
    .times(5)
    .emit()
    .toList()
```

### Pattern 8: Cycle Detection

```java
// Cypher
MATCH path = (n:Content {IL_UNIQUE_ID: $id})-[*1..10]->(n)
RETURN length(path) > 0

// Gremlin
boolean hasCycle = g.V().has("IL_UNIQUE_ID", id)
    .repeat(out())
    .times(10)
    .emit()
    .path()
    .where(loops().is(P.gt(0)))
    .limit(1)
    .hasNext();
```

---

## Testing Strategy

### Phase 1: Unit Tests (Tasks 58-60)
- Update all BaseTest.java files with embedded JanusGraph (inmemory backend)
- Migrate Scala BaseSpec.scala test fixtures
- Run existing test suite: `mvn clean test`
- Target: 100% test pass rate

### Phase 2: Integration Tests (Task 61)
- Run API functional tests against local Yugabyte instance
- Test scenarios: content creation, collection hierarchy, search, taxonomy operations
- Validate: Response schemas, relationship integrity, search indexing

### Phase 3: Performance Benchmarking (Task 73)
**Metrics to Compare**:
| Operation | Neo4j Baseline | JanusGraph Target | Actual |
|-----------|----------------|-------------------|--------|
| Create Node | 5ms | <6ms | TBD |
| Query by ID | 2ms | <3ms | TBD |
| Traverse Depth-3 | 15ms | <20ms | TBD |
| Bulk Import (1000 nodes) | 500ms | <600ms | TBD |

**Acceptance Criteria**: <20% performance regression on P95 latency

### Phase 4: Data Migration Validation (Task 69)
```bash
# Count validation
NEO4J_COUNT=$(cypher-shell "MATCH (n) RETURN count(n)" | tail -1)
JANUS_COUNT=$(gremlin "g.V().count()" | tail -1)

# Sample validation (100 random nodes)
for id in $(shuf -n 100 node_ids.txt); do
    # Compare properties, relationships, metadata
done
```

---

## Rollback Plan

### Pre-Migration Backup
1. **Neo4j Snapshot**: `neo4j-admin backup --from=bolt://localhost:7687 --backup-dir=/backup`
2. **Database Dump**: Export all data to GraphML format
3. **Configuration Backup**: Save all `application.conf` files

### Rollback Triggers
- Test pass rate <95%
- Performance regression >20%
- Data validation failures >1%
- Critical production bugs within 48 hours

### Rollback Procedure (1-2 hours)
1. Stop all services
2. Revert POM files: `git checkout HEAD~10 */pom.xml`
3. Revert docker-compose: `git checkout HEAD~10 docker-compose.yml`
4. Restore Neo4j data: `neo4j-admin restore --from=/backup`
5. Restart services with Neo4j configuration
6. Run smoke tests

### Read-Only Period
- Keep Neo4j instance read-only for 1 week post-migration
- Monitor JanusGraph for issues during this period
- Allows fast rollback if needed

---

## Next Steps

### Immediate (Week 1)
1. Complete NodeQueryGenerationUtil migration (Task 23-27)
2. Complete SearchQueryGenerationUtil migration (Task 28-30)
3. Complete GraphQueryGenerationUtil migration (Task 31-38)
4. Update test infrastructure (Task 58-63)

### Short-Term (Week 2-3)
1. Migrate async operations (Task 44-49)
2. Update Kubernetes configurations (Task 53-57)
3. Run full test suite and fix failures (Task 72)
4. Performance benchmarking (Task 73)

### Long-Term (Week 4+)
1. Export Neo4j data (Task 64-66)
2. Import to JanusGraph (Task 67-68)
3. Data validation (Task 69-70)
4. Production deployment (Task 71)
5. Documentation updates (Task 75)

---

## Contact & Support

**Migration Lead**: Development Team  
**JanusGraph Resources**: https://docs.janusgraph.org/  
**Gremlin Documentation**: https://tinkerpop.apache.org/gremlin.html  
**Issue Tracking**: Project JIRA board

---

**Last Updated**: 2025-01-19  
**Document Version**: 1.0
