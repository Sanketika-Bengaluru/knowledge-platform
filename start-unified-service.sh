#!/bin/bash

# Unified Knowledge Platform Startup Script
# This script builds and starts the unified service containing all APIs:
# - Content Service APIs (V3, V4)
# - Assessment APIs (V3, V4, V5) 
# - Search/Composite Search APIs
# - Taxonomy/Object Category APIs

set -e

echo "=========================================="
echo "Starting Knowledge Platform Unified Service"
echo "=========================================="

# Function to check if databases are running
check_databases() {
    echo "Checking database connections..."
    
    # Check Neo4j
    if ! curl -s http://localhost:7474 > /dev/null; then
        echo "❌ Neo4j is not running on port 7474"
        echo "Please start Neo4j using: docker run --name sunbird_neo4j -p7474:7474 -p7687:7687 -d --env NEO4J_AUTH=none neo4j:3.3.0"
        exit 1
    else
        echo "✅ Neo4j is running"
    fi
    
    # Check Redis
    if ! redis-cli -h localhost -p 6379 ping > /dev/null 2>&1; then
        echo "❌ Redis is not running on port 6379"
        echo "Please start Redis using: docker run --name sunbird_redis -d -p 6379:6379 redis:6.0.8"
        exit 1
    else
        echo "✅ Redis is running"
    fi
    
    # Check Cassandra
    if ! nc -z localhost 9042 > /dev/null 2>&1; then
        echo "❌ Cassandra is not running on port 9042"
        echo "Please start Cassandra using: docker run --name sunbird_cassandra -d -p 9042:9042 cassandra:3.11.8"
        exit 1
    else
        echo "✅ Cassandra is running"
    fi
    
    echo "All databases are running! 🎉"
}

# Function to build the unified service
build_service() {
    echo ""
    echo "Building Knowledge Platform Unified Service..."
    echo "This includes Content, Assessment, Search, and Taxonomy APIs"
    
    cd /Users/sanketikam4/November/knowledge-platform
    
    # Build core platform modules first
    echo "Building platform-core..."
    mvn clean install -DskipTests -f platform-core/pom.xml -q
    
    echo "Building ontology-engine..." 
    mvn clean install -DskipTests -f ontology-engine/pom.xml -q
    
    echo "Building platform-modules..."
    mvn clean install -DskipTests -f platform-modules/pom.xml -q
    
    # Build unified service
    echo "Building unified service..."
    cd knowlg-unified-service
    mvn clean compile -DskipTests -q
    
    if [ $? -eq 0 ]; then
        echo "✅ Build successful!"
    else
        echo "❌ Build failed!"
        exit 1
    fi
}

# Function to start the service
start_service() {
    echo ""
    echo "Starting Unified Service..."
    echo "The service will run on port 9010"
    echo ""
    echo "Available APIs:"
    echo "- Content APIs: http://localhost:9010/content/"
    echo "- Assessment APIs: http://localhost:9010/assessment/"  
    echo "- Search APIs: http://localhost:9010/composite/"
    echo "- Taxonomy APIs: http://localhost:9010/framework/"
    echo "- Health Check: http://localhost:9010/health"
    echo ""
    
    cd /Users/sanketikam4/November/knowledge-platform/knowlg-unified-service
    mvn play2:run
}

# Function to show usage
show_usage() {
    echo "Usage: $0 [option]"
    echo ""
    echo "Options:"
    echo "  start     - Check databases, build and start the unified service"
    echo "  build     - Only build the service" 
    echo "  run       - Only start the service (assumes already built)"
    echo "  check     - Only check database connectivity"
    echo "  help      - Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0 start    # Full startup (check + build + run)"
    echo "  $0 build    # Just build"
    echo "  $0 run      # Just run (if already built)"
}

# Main script logic
case "${1:-start}" in
    "start")
        check_databases
        build_service
        start_service
        ;;
    "build")
        build_service
        ;;
    "run")
        start_service
        ;;
    "check")
        check_databases
        ;;
    "help"|"-h"|"--help")
        show_usage
        ;;
    *)
        echo "❌ Unknown option: $1"
        show_usage
        exit 1
        ;;
esac