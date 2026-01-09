package org.sunbird.graph

import java.io.File
//import com.datastax.driver.core.Session
import com.typesafe.config.ConfigFactory
import org.apache.commons.io.FileUtils
import org.cassandraunit.utils.EmbeddedCassandraServerHelper
import org.janusgraph.core.JanusGraph
import org.janusgraph.core.JanusGraphFactory
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource
import org.scalatest.{AsyncFlatSpec, BeforeAndAfterAll, Matchers}
import org.sunbird.cassandra.CassandraConnector
import org.sunbird.common.Platform
import org.sunbird.graph.dac.util.JanusGraphSchemaManager

import java.time.Duration
import java.util.concurrent.TimeUnit

class BaseSpec extends AsyncFlatSpec with Matchers with BeforeAndAfterAll {

    var embeddedGraph: JanusGraph = _
    var g: GraphTraversalSource = _
    var session: com.datastax.driver.core.Session = null
    implicit val oec: OntologyEngineContext = new OntologyEngineContext
    val testGraphId = "domain"

    private val script_1 = "CREATE KEYSPACE IF NOT EXISTS content_store WITH replication = {'class': 'SimpleStrategy','replication_factor': '1'};"
    private val script_2 = "CREATE TABLE IF NOT EXISTS content_store.content_data (content_id text, last_updated_on timestamp,body blob,oldBody blob,screenshots blob,stageIcons blob,externallink text,PRIMARY KEY (content_id));"
    private val script_3 = "CREATE KEYSPACE IF NOT EXISTS hierarchy_store WITH replication = {'class': 'SimpleStrategy','replication_factor': '1'};"
    private val script_4 = "CREATE TABLE IF NOT EXISTS hierarchy_store.content_hierarchy (identifier text, hierarchy text, relational_metadata text, PRIMARY KEY (identifier));"
    private val script_5 = "CREATE KEYSPACE IF NOT EXISTS category_store WITH replication = {'class': 'SimpleStrategy','replication_factor': '1'};"
    private val script_6 = "CREATE TABLE IF NOT EXISTS category_store.category_definition_data (identifier text, objectmetadata map<text, text>, forms map<text,text> ,PRIMARY KEY (identifier));"
    private val script_7 = "INSERT INTO category_store.category_definition_data (identifier, objectmetadata) VALUES ('obj-cat:learning-resource_content_all', {'config': '{}', 'schema': '{\"properties\":{\"audience\":{\"type\":\"array\",\"items\":{\"type\":\"string\",\"enum\":[\"Student\",\"Teacher\"]},\"default\":[\"Student\"]},\"mimeType\":{\"type\":\"string\",\"enum\":[\"application/vnd.ekstep.ecml-archive\",\"application/vnd.ekstep.html-archive\",\"application/vnd.ekstep.h5p-archive\",\"application/pdf\",\"video/mp4\",\"video/webm\"]}}}'});"
    private val script_8 = "INSERT INTO category_store.category_definition_data (identifier, objectmetadata) VALUES ('obj-cat:course_collection_all', {'config': '{}', 'schema': '{\"properties\":{\"trackable\":{\"type\":\"object\",\"properties\":{\"enabled\":{\"type\":\"string\",\"enum\":[\"Yes\",\"No\"],\"default\":\"No\"},\"autoBatch\":{\"type\":\"string\",\"enum\":[\"Yes\",\"No\"],\"default\":\"No\"}},\"default\":{\"enabled\":\"No\",\"autoBatch\":\"No\"},\"additionalProperties\":false},\"additionalCategories\":{\"type\":\"array\",\"items\":{\"type\":\"string\",\"default\":\"Textbook\"}},\"userConsent\":{\"type\":\"string\",\"enum\":[\"Yes\",\"No\"],\"default\":\"Yes\"}}}'});"
    private val script_9 = "INSERT INTO category_store.category_definition_data (identifier, objectmetadata) VALUES ('obj-cat:course_content_all',{'config': '{}', 'schema': '{\"properties\":{\"trackable\":{\"type\":\"object\",\"properties\":{\"enabled\":{\"type\":\"string\",\"enum\":[\"Yes\",\"No\"],\"default\":\"No\"},\"autoBatch\":{\"type\":\"string\",\"enum\":[\"Yes\",\"No\"],\"default\":\"No\"}},\"default\":{\"enabled\":\"No\",\"autoBatch\":\"No\"},\"additionalProperties\":false},\"additionalCategories\":{\"type\":\"array\",\"items\":{\"type\":\"string\",\"default\":\"Textbook\"}},\"userConsent\":{\"type\":\"string\",\"enum\":[\"Yes\",\"No\"],\"default\":\"Yes\"}}}'});"
    private val script_10 = "INSERT INTO category_store.category_definition_data (identifier, objectmetadata) VALUES ('obj-cat:learning-resource_collection_all', {'config': '{}', 'schema': '{\"properties\":{\"audience\":{\"type\":\"array\",\"items\":{\"type\":\"string\",\"enum\":[\"Student\",\"Teacher\"]},\"default\":[\"Student\"]},\"mimeType\":{\"type\":\"string\",\"enum\":[\"application/vnd.ekstep.ecml-archive\",\"application/vnd.ekstep.html-archive\",\"application/vnd.ekstep.h5p-archive\",\"application/pdf\",\"video/mp4\",\"video/webm\"]}}}'});"
    private val script_11 = "INSERT INTO category_store.category_definition_data (identifier, objectmetadata) VALUES ('obj-cat:learning-resource_content_in.ekstep', {'config': '{}', 'schema': '{\"properties\":{\"audience\":{\"type\":\"array\",\"items\":{\"type\":\"string\",\"enum\":[\"Student\",\"Teacher\"]},\"default\":[\"Student\"]},\"mimeType\":{\"type\":\"string\",\"enum\":[\"application/vnd.ekstep.ecml-archive\",\"application/vnd.ekstep.html-archive\",\"application/vnd.ekstep.h5p-archive\",\"application/pdf\",\"video/mp4\",\"video/webm\"]}}}'});"
    private val script_13 = "CREATE KEYSPACE IF NOT EXISTS lock_db WITH replication = {'class': 'SimpleStrategy','replication_factor': '1'};"
    private val script_12 = "INSERT INTO category_store.category_definition_data (identifier, objectmetadata) VALUES ('obj-cat:learning-resource_collection_in.ekstep', {'config': '{}', 'schema': '{\"properties\":{\"audience\":{\"type\":\"array\",\"items\":{\"type\":\"string\",\"enum\":[\"Student\",\"Teacher\"]},\"default\":[\"Student\"]},\"mimeType\":{\"type\":\"string\",\"enum\":[\"application/vnd.ekstep.ecml-archive\",\"application/vnd.ekstep.html-archive\",\"application/vnd.ekstep.h5p-archive\",\"application/pdf\",\"video/mp4\",\"video/webm\"]}}}'});"
    private val script_14 = "CREATE TABLE IF NOT EXISTS lock_db.lock (resourceid text, createdby text, createdon timestamp, creatorinfo text, deviceid text, expiresat timestamp, lockid uuid, resourceinfo text,resourcetype text, PRIMARY KEY (resourceid));"
    private val configFile = ConfigFactory.load()
    private val graphDirectory = configFile.getString("graph.dir")

    def setUpEmbeddedJanusGraph(): Unit = {
        if (null == embeddedGraph) {
            embeddedGraph = JanusGraphFactory.build()
              .set("storage.backend", "inmemory")
              .set("index.search.backend", "inmemory")
              .open()
            
            JanusGraphSchemaManager.initializeGraphSchema("domain", embeddedGraph)
            g = embeddedGraph.traversal()
        }
    }

    @throws[Exception]
    private def tearEmbeddedJanusGraphSetup(): Unit = {
        if (null != g) {
            g.close()
            g = null
        }
        if (null != embeddedGraph) {
            embeddedGraph.close()
            embeddedGraph = null
        }
    }

    private def deleteEmbeddedGraph(emDb: File): Unit = {
        try{
            if(emDb.exists() && emDb.isDirectory)
                FileUtils.deleteDirectory(emDb)
        }catch{
            case e: Exception =>
                e.printStackTrace()
        }
    }


    def setUpEmbeddedCassandra(): Unit = {
        System.setProperty("cassandra.unsafesystem", "true")
        EmbeddedCassandraServerHelper.startEmbeddedCassandra("/cassandra-unit.yaml", 100000L)
    }

    override def beforeAll(): Unit = {
        tearEmbeddedJanusGraphSetup()
        setUpEmbeddedJanusGraph()
        setUpEmbeddedCassandra()
        executeGremlinQuery()
        executeCassandraQuery(script_1, script_2, script_3, script_4, script_5, script_6, script_7, script_8, script_9, script_10, script_11, script_12, script_13, script_14)
    }

    override def afterAll(): Unit = {
        tearEmbeddedJanusGraphSetup()
        if(null != session && !session.isClosed)
            session.close()
        EmbeddedCassandraServerHelper.cleanEmbeddedCassandra()
    }


    def executeCassandraQuery(queries: String*): Unit = {
        if(null == session || session.isClosed){
            session = CassandraConnector.getSession
        }
        for(query <- queries) {
            session.execute(query)
        }
    }

    def createRelationData(): Unit = {
        // Create test vertices for relation data
        g.addV(testGraphId)
          .property("IL_UNIQUE_ID", "Num:C3:SC2")
          .property("IL_FUNC_OBJECT_TYPE", "Concept")
          .property("IL_SYS_NODE_TYPE", "DATA_NODE")
          .property("name", "Multiplication")
          .property("status", "Live")
          .next()
        g.tx().commit()
    }

	def createBulkNodes(): Unit ={
		val nodeIds = Array("do_0000123", "do_0000234", "do_0000345")
		for (nodeId <- nodeIds) {
		    g.addV(testGraphId)
		      .property("IL_UNIQUE_ID", nodeId)
		      .property("IL_SYS_NODE_TYPE", "DATA_NODE")
		      .property("IL_FUNC_OBJECT_TYPE", "Content")
		      .next()
		}
		g.tx().commit()
	}

    def executeGremlinQuery(): Unit = {
        // Create test category definition nodes
        g.addV(testGraphId)
          .property("identifier", "obj-cat:course_collection_all")
          .property("IL_UNIQUE_ID", "obj-cat:course_collection_all")
          .property("IL_FUNC_OBJECT_TYPE", "ObjectCategoryDefinition")
          .property("IL_SYS_NODE_TYPE", "DATA_NODE")
          .property("status", "Live")
          .next()
        g.tx().commit()
    }

    def getGraphTraversal(): GraphTraversalSource = g
    def getEmbeddedGraph(): JanusGraph = embeddedGraph
}
