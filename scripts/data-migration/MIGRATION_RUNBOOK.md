# Neo4j to JanusGraph Data Migration Runbook

## Overview
This runbook guides you through migrating data from Neo4j 3.3.0 to JanusGraph 1.0.0 with Yugabyte backend.

## Prerequisites
- Neo4j 3.3.0 with APOC plugin installed
- Yugabyte cluster running and accessible
- Knowledge platform built: `mvn clean install -DskipTests`
- Python 3.7+ installed
- Sufficient disk space (2x your Neo4j data size)

## Migration Steps

### Step 1: Pre-Migration Validation (30 minutes)

1. **Backup Neo4j Database**
   ```bash
   # Stop Neo4j
   neo4j stop
   
   # Create backup
   neo4j-admin backup --backup-dir=/path/to/backup --name=neo4j-backup
   
   # Restart Neo4j
   neo4j start
   ```

2. **Verify Neo4j Statistics**
   ```bash
   cypher-shell -u neo4j -p password <<EOF
   MATCH (n) RETURN count(n) as nodeCount;
   MATCH ()-[r]->() RETURN count(r) as relCount;
   MATCH (n) RETURN labels(n)[0] as label, count(*) as count ORDER BY count DESC;
   EOF
   ```

3. **Check Yugabyte Connectivity**
   ```bash
   cqlsh localhost 9042 -e "DESCRIBE KEYSPACES"
   ```

4. **Verify Disk Space**
   ```bash
   df -h
   # Ensure 2x Neo4j data size available
   ```

### Step 2: Export from Neo4j (1-4 hours depending on data size)

1. **Set Environment Variables**
   ```bash
   export NEO4J_HOST=localhost
   export NEO4J_PORT=7687
   export NEO4J_USER=neo4j
   export NEO4J_PASSWORD=password
   export OUTPUT_DIR=./neo4j-export
   ```

2. **Run Export Script**
   ```bash
   cd /path/to/knowledge-platform
   bash scripts/data-migration/export-neo4j.sh
   ```

3. **Verify Export**
   ```bash
   ls -lh ./neo4j-export/
   # Should see: graph-export.graphml, export-metadata.json
   
   cat ./neo4j-export/export-metadata.json
   # Verify nodeCount and relationshipCount match Neo4j
   ```

### Step 3: Convert GraphML to GraphSON (30-60 minutes)

1. **Set Environment Variables**
   ```bash
   export INPUT_FILE=./neo4j-export/graph-export.graphml
   export OUTPUT_FILE=./neo4j-export/graph-export.json
   ```

2. **Run Conversion Script**
   ```bash
   python3 scripts/data-migration/graphml-to-graphson.py
   ```

3. **Verify Conversion**
   ```bash
   ls -lh ./neo4j-export/graph-export.json
   # Should be larger than GraphML file
   
   jq '.vertices | length' ./neo4j-export/graph-export.json
   jq '.edges | length' ./neo4j-export/graph-export.json
   # Counts should match export metadata
   ```

### Step 4: Prepare JanusGraph (15 minutes)

1. **Verify Yugabyte is Running**
   ```bash
   docker-compose ps sunbird-yugabyte
   # Or for Kubernetes:
   kubectl get pods -n knowlg-db | grep yugabyte
   ```

2. **Test JanusGraph Connection**
   ```bash
   mvn exec:java -Dexec.mainClass="org.sunbird.graph.service.util.JanusGraphDriverUtil" \
     -Dexec.args="test-connection"
   ```

3. **Initialize Schema**
   ```bash
   # Schema is auto-initialized on first connection
   # Verify in logs: "Schema initialization completed for graph: domain"
   ```

### Step 5: Import to JanusGraph (2-8 hours depending on data size)

1. **Set Environment Variables**
   ```bash
   export YUGABYTE_HOST=localhost
   export YUGABYTE_PORT=9042
   export GRAPH_ID=domain
   export INPUT_FILE=./neo4j-export/graph-export.json
   export BATCH_SIZE=500
   ```

2. **Run Import Script**
   ```bash
   bash scripts/data-migration/import-to-janusgraph.sh
   ```
   
   This will prepare the import program. Then run:
   ```bash
   mvn exec:java -Dexec.mainClass="DataImporter" \
     -DgraphId=$GRAPH_ID \
     -DinputFile=$INPUT_FILE \
     -DbatchSize=$BATCH_SIZE
   ```

3. **Monitor Import Progress**
   ```bash
   # Watch logs for batch progress
   tail -f target/import-log.txt
   
   # Check Yugabyte metrics
   curl http://localhost:7000/metrics
   ```

### Step 6: Validation (30 minutes)

1. **Run Validation Script**
   ```bash
   export NEO4J_HOST=localhost
   export NEO4J_PORT=7687
   export NEO4J_USER=neo4j
   export NEO4J_PASSWORD=password
   export YUGABYTE_HOST=localhost
   export YUGABYTE_PORT=9042
   export GRAPH_ID=domain
   
   bash scripts/data-migration/validate-import.sh
   ```

2. **Generate Detailed Validation Report**
   ```bash
   mvn exec:java -Dexec.mainClass="ValidationReport" \
     -DgraphId=$GRAPH_ID \
     -DreportFile=./validation-report.txt
   
   cat ./validation-report.txt
   ```

3. **Compare Metrics**
   ```bash
   # Compare counts
   cat ./neo4j-export/export-metadata.json
   cat ./validation-report.txt
   
   # Should match:
   # - nodeCount == Total Vertices
   # - relationshipCount == Total Edges
   # - Label distributions
   ```

4. **Sample Data Validation**
   ```bash
   # Test 10 random nodes
   cypher-shell -u neo4j -p password <<EOF
   MATCH (n) WHERE exists(n.IL_UNIQUE_ID)
   RETURN n.IL_UNIQUE_ID, labels(n), properties(n)
   LIMIT 10;
   EOF
   
   # Compare with JanusGraph using ValidationReport
   ```

### Step 7: Application Testing (1-2 hours)

1. **Update Application Configuration**
   ```bash
   # Already done in previous tasks
   # Verify content-service_application.conf has janusgraph block
   ```

2. **Run Unit Tests**
   ```bash
   cd knowledge-platform
   mvn test
   ```

3. **Run API Tests**
   ```bash
   cd content-api/api-tests
   newman run Collections/ContentAPI.postman_collection.json \
     -e Environments/local.postman_environment.json
   ```

4. **Test Key Operations**
   ```bash
   # Create content
   curl -X POST http://localhost:9000/content/v3/create \
     -H "Content-Type: application/json" \
     -d '{"request": {"content": {"name": "Test Content"}}}'
   
   # Read content
   curl http://localhost:9000/content/v3/read/do_test_123
   
   # Search content
   curl -X POST http://localhost:9000/content/v3/search \
     -H "Content-Type: application/json" \
     -d '{"request": {"filters": {"status": ["Live"]}}}'
   ```

### Step 8: Performance Benchmarking (1 hour)

1. **Run Benchmarks**
   ```bash
   # Compare Neo4j vs JanusGraph latency
   bash scripts/benchmarks/compare-performance.sh
   ```

2. **Analyze Results**
   - Node creation latency
   - Node read latency
   - Search query latency
   - Bulk import throughput
   - Target: <20% regression

### Step 9: Rollback Plan (if needed)

If issues are found:

1. **Stop Application**
   ```bash
   docker-compose down
   # Or: kubectl scale deployment content-service --replicas=0
   ```

2. **Restore Neo4j Configuration**
   ```bash
   git checkout content-api/content-service/conf/content-service_application.conf
   ```

3. **Restart with Neo4j**
   ```bash
   docker-compose up -d sunbird-neo4j
   docker-compose up -d content-service
   ```

4. **Verify Rollback**
   ```bash
   curl http://localhost:9000/health
   ```

### Step 10: Production Deployment

Once validation passes:

1. **Schedule Maintenance Window** (4-8 hours)

2. **Enable Read-Only Mode** (keep Neo4j as backup)
   ```cypher
   CALL dbms.readOnly(true);
   ```

3. **Run Migration Steps 1-6** on production

4. **Deploy Updated Application**
   ```bash
   kubectl apply -f kubernetes/content/
   kubectl apply -f kubernetes/taxonomy/
   kubectl rollout status deployment content-service
   ```

5. **Monitor Application**
   ```bash
   kubectl logs -f deployment/content-service
   watch kubectl get pods
   ```

6. **Run Smoke Tests**
   ```bash
   bash scripts/smoke-tests.sh production
   ```

7. **Monitor for 1 Week**
   - Check error rates
   - Check latency metrics
   - Check data consistency

8. **Decommission Neo4j** (after 1 week)
   ```bash
   kubectl delete -f kubernetes/sunbird-dbs/neo4j/
   ```

## Troubleshooting

### Import Fails with Connection Timeout
```bash
# Increase Yugabyte timeout
export JANUSGRAPH_STORAGE_CQL_READ_TIMEOUT=60000
export JANUSGRAPH_STORAGE_CQL_WRITE_TIMEOUT=60000
```

### Out of Memory During Import
```bash
# Reduce batch size
export BATCH_SIZE=100

# Increase JVM heap
export MAVEN_OPTS="-Xmx8g -Xms4g"
```

### Property Type Mismatch
```bash
# Check schema definitions in JanusGraphSchemaManager.java
# Ensure property types match Neo4j

# Example: if Integer in Neo4j, use Integer in JanusGraph
mgmt.makePropertyKey("myProp").dataType(Integer.class).make();
```

### Count Mismatch After Import
```bash
# Check for failed transactions
grep "ERROR" target/import-log.txt

# Verify transaction commits
g.tx().commit()  # Must be called after batch inserts
```

## Success Criteria

✅ Vertex count matches node count (±0%)
✅ Edge count matches relationship count (±0%)
✅ All labels migrated
✅ All properties migrated with correct types
✅ Sample validation: 100 random nodes match exactly
✅ All unit tests pass
✅ All API tests pass
✅ Performance regression <20%
✅ No data loss
✅ Application functionally equivalent

## Post-Migration Checklist

- [ ] Neo4j export completed successfully
- [ ] GraphML to GraphSON conversion successful
- [ ] JanusGraph import completed successfully
- [ ] Validation report shows 100% match
- [ ] Unit tests: 100% pass rate
- [ ] API tests: 100% pass rate
- [ ] Performance benchmarks acceptable
- [ ] Production deployment successful
- [ ] Monitoring alerts configured
- [ ] Documentation updated
- [ ] Team trained on JanusGraph operations
- [ ] Neo4j backup archived
- [ ] Neo4j decommissioned (after 1 week)

## Rollback Triggers

Rollback immediately if:
- Data loss detected (count mismatch >1%)
- Critical functionality broken
- Performance degradation >50%
- Application errors >5%
- Unable to resolve issues within maintenance window

## Support Contacts

- Database Team: db-support@example.com
- DevOps Team: devops@example.com
- On-call Engineer: +1-xxx-xxx-xxxx

## References

- [JanusGraph Documentation](https://docs.janusgraph.org/)
- [Yugabyte Documentation](https://docs.yugabyte.com/)
- [Migration Guide](MIGRATION_GUIDE.md)
- [Implementation Status](IMPLEMENTATION_STATUS.md)
