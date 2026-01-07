# Knowledge Platform - Unified Service

This repository contains the **unified Knowledge Platform service** that combines all previously separate services into a single, comprehensive API service.

## 🚀 What's Included

The unified service provides all the following APIs in a single deployment:

### Content APIs
- **Content V3 & V4 APIs** - Create, read, update content
- **Collection V4 APIs** - Manage content collections  
- **Assets V4 APIs** - Handle digital assets
- **Channel V3 APIs** - Channel management
- **License V3 APIs** - License management
- **Event V4 & EventSet V4 APIs** - Event handling

### Assessment APIs ✨ 
- **Assessment V3, V4 & V5 APIs** - Question and QuestionSet management
- **ItemSet APIs** - Assessment item management
- All assessment functionality previously in separate inquiry service

### Search APIs
- **Composite Search APIs** - Advanced search capabilities
- **Asset Search APIs** - Search across all assets

### Taxonomy APIs
- **Framework V3 APIs** - Framework management
- **Object Category APIs** - Category definitions and management

## 🏗️ Architecture Benefits

### Before: Multiple Services
```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Content API   │    │ Assessment API  │    │   Search API    │
│   Port: 9000    │    │   Port: 9001    │    │   Port: 9002    │
└─────────────────┘    └─────────────────┘    └─────────────────┘
        │                        │                        │
        └────────────────────────┼────────────────────────┘
                                 │
                    ┌─────────────────┐
                    │   Taxonomy API  │
                    │   Port: 9003    │
                    └─────────────────┘
```

### After: Unified Service ✨
```
┌─────────────────────────────────────────────────────────────┐
│                 Knowledge Platform Unified Service           │
│                          Port: 9010                         │
│                                                             │
│  Content APIs  │  Assessment APIs  │  Search APIs  │ Taxonomy │
│     V3, V4     │    V3, V4, V5    │   Composite   │   APIs   │
└─────────────────────────────────────────────────────────────┘
```

## 🚀 Quick Start

### Prerequisites
- Java 11+
- Maven 3.6+
- Docker (for databases)

### 1. Start Database Dependencies

```bash
# Neo4j
docker run --name sunbird_neo4j -p7474:7474 -p7687:7687 -d \
  --env NEO4J_AUTH=none neo4j:3.3.0

# Redis  
docker run --name sunbird_redis -d -p 6379:6379 redis:6.0.8

# Cassandra
docker run --name sunbird_cassandra -d -p 9042:9042 cassandra:3.11.8
```

### 2. Start the Unified Service

```bash
# Option 1: Use the automated script (Recommended)
./start-unified-service.sh start

# Option 2: Manual build and run
mvn clean install -DskipTests
cd knowlg-unified-service  
mvn play2:run
```

### 3. Verify Service

```bash
# Health check
curl http://localhost:9010/health

# Test Content API
curl http://localhost:9010/content/v4/read/your-content-id

# Test Assessment API  
curl http://localhost:9010/assessment/v4/read/your-question-id

# Test Search API
curl http://localhost:9010/composite/v3/search

# Test Taxonomy API
curl http://localhost:9010/framework/v3/read/your-framework-id
```

## 📊 API Endpoints

### Content APIs (`/content/*`)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/content/v4/create` | Create content |
| GET | `/content/v4/read/{id}` | Read content |
| PATCH | `/content/v4/update/{id}` | Update content |
| DELETE | `/content/v4/retire/{id}` | Retire content |

### Assessment APIs (`/assessment/*`) ✨
| Method | Endpoint | Description |  
|--------|----------|-------------|
| POST | `/assessment/v4/create` | Create assessment |
| GET | `/assessment/v4/read/{id}` | Read assessment |
| POST | `/assessment/v5/questionset/create` | Create question set (V5) |
| GET | `/assessment/v5/question/read/{id}` | Read question (V5) |

### Search APIs (`/composite/*`)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/composite/v3/search` | Composite search |
| POST | `/composite/v4/search` | Advanced search |

### Taxonomy APIs (`/framework/*`)  
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/framework/v3/create` | Create framework |
| GET | `/framework/v3/read/{id}` | Read framework |
| POST | `/category/v4/create` | Create category |

## 🔧 Configuration

The unified service uses a single configuration file: `knowlg-unified-service/conf/application.conf`

Key configurations:
- Database connections (Neo4j, Redis, Cassandra)
- Service-specific settings
- API versioning
- Security settings

## 🏃‍♂️ Development

### Build Only
```bash
./start-unified-service.sh build
```

### Run Only (if already built)  
```bash
./start-unified-service.sh run
```

### Check Database Connectivity
```bash  
./start-unified-service.sh check
```

## 🚀 Deployment

### Development
```bash
./start-unified-service.sh start
```

### Production (Distribution)
```bash
cd knowlg-unified-service
mvn play2:dist
cd target  
unzip knowlg-unified-service-1.0-SNAPSHOT-dist.zip
cd knowlg-unified-service-1.0-SNAPSHOT
./bin/knowlg-unified-service
```

## 🔄 Migration from Separate Services

If you were previously running separate services:

1. **Stop all separate services** (content-service, assessment-service, etc.)
2. **Start the unified service** using the steps above
3. **Update your API calls** to use port 9010 instead of separate ports
4. **No API changes required** - same endpoints, just different base URL

### URL Migration Examples:
```bash
# Before
http://localhost:9000/content/v4/read/{id}  # Content service
http://localhost:9001/assessment/v4/read/{id}  # Assessment service  

# After  
http://localhost:9010/content/v4/read/{id}  # Unified service
http://localhost:9010/assessment/v4/read/{id}  # Unified service
```

## 🎯 Benefits

- **Simplified Deployment**: One service instead of multiple
- **Reduced Resource Usage**: Single JVM process
- **Easier Configuration**: One configuration file
- **Better Performance**: No inter-service network calls
- **Simplified Development**: One codebase to manage
- **Consistent Logging**: Unified logging across all APIs

## 🛠️ Troubleshooting

### Service Won't Start
1. Check database connectivity: `./start-unified-service.sh check`
2. Verify Java version: `java -version` (should be 11+)
3. Check port availability: `lsof -i :9010`

### Build Fails
```bash
# Clean and rebuild
mvn clean install -DskipTests -f platform-core/pom.xml
mvn clean install -DskipTests -f ontology-engine/pom.xml  
mvn clean install -DskipTests -f platform-modules/pom.xml
cd knowlg-unified-service && mvn clean compile
```

### Health Check Fails
```bash
# Check service logs
tail -f knowlg-unified-service/logs/application.log

# Verify database connections
curl http://localhost:9010/health
```

## 📝 Legacy Documentation

For reference, the old separate service documentation can be found in:
- `inquiry-api-service/README.md` - Assessment service (now integrated)
- Individual service READMEs in their respective directories

## 🤝 Contributing

1. All development now happens in `knowlg-unified-service/`
2. Use the unified startup script for testing
3. Ensure all API categories (content, assessment, search, taxonomy) work together
4. Update this README for any new endpoints or features