package org.sunbird.test;

import com.typesafe.config.ConfigFactory;
import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.janusgraph.core.JanusGraph;
import org.janusgraph.core.JanusGraphFactory;
import org.sunbird.common.Platform;
import org.sunbird.graph.dac.util.JanusGraphSchemaManager;
import org.sunbird.graph.service.util.JanusGraphDriverUtil;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;

import java.io.File;
import java.io.IOException;


public class BaseTest {

	private static JanusGraph embeddedGraph = null;
	private static GraphTraversalSource g = null;
	private static com.typesafe.config.Config configFile = ConfigFactory.load();
	private static String graphDirectory = configFile.getString("graph.dir");
	protected static String testGraphId = "domain"; // Default test graph ID

	private static String GRAPH_DIRECTORY_PROPERTY_KEY = "graph.dir";

	@AfterClass
	public static void afterTest() throws Exception {
		tearEmbeddedJanusGraphSetup();
		JanusGraphDriverUtil.closeAllGraphs();
	}

	@BeforeClass
	public static void before() throws Exception {
		setupEmbeddedJanusGraph();
	}

	private static void registerShutdownHook(final JanusGraph graphDb) {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				try {
					tearEmbeddedJanusGraphSetup();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Setup embedded JanusGraph with inmemory backend for testing
	 */
	private static void setupEmbeddedJanusGraph() throws InterruptedException {
		if (embeddedGraph == null) {
			File graphDir = new File(graphDirectory);
			if (!graphDir.exists()) {
				graphDir.mkdirs();
			}

			System.out.println("Setting up embedded JanusGraph with inmemory backend");
			
			// Create JanusGraph with inmemory backend (no external dependencies)
			embeddedGraph = JanusGraphFactory.build()
					.set("storage.backend", "inmemory")
					.set("index.search.backend", "inmemory")
					.open();

			// Initialize schema
			JanusGraphSchemaManager.initializeGraphSchema("test", embeddedGraph);

			// Get traversal source
			g = embeddedGraph.traversal();

			System.out.println("Embedded JanusGraph initialized successfully");
			
			registerShutdownHook(embeddedGraph);
		}
	}

	/**
	 * Tear down embedded JanusGraph
	 */
	private static void tearEmbeddedJanusGraphSetup() throws Exception {
		if (g != null) {
			try {
				g.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		if (embeddedGraph != null) {
			try {
				embeddedGraph.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		Thread.sleep(1000);
		deleteEmbeddedGraph(new File(Platform.config.getString(GRAPH_DIRECTORY_PROPERTY_KEY)));
	}

	private static void deleteEmbeddedGraph(final File emDb) throws IOException {
		if (emDb.exists()) {
			FileUtils.deleteDirectory(emDb);
		}
	}

	/**
	 * Get graph traversal source for tests
	 */
	protected static GraphTraversalSource getGraphTraversal() {
		return g;
	}

	/**
	 * Get embedded graph instance
	 */
	protected static JanusGraph getEmbeddedGraph() {
		return embeddedGraph;
	}

	protected static void delay(long time) {
		try {
			Thread.sleep(time);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Create bulk test nodes using Gremlin
	 */
	protected void createBulkNodes() {
		// Create test nodes using Gremlin instead of Cypher
		String[] nodeIds = {"do_0000123", "do_0000234", "do_0000345"};
		
		for (String nodeId : nodeIds) {
			g.addV(testGraphId)
				.property("IL_UNIQUE_ID", nodeId)
				.property("IL_SYS_NODE_TYPE", "DATA_NODE")
				.property("IL_FUNC_OBJECT_TYPE", "Content")
				.next();
		}
		
		// Commit transaction
		g.tx().commit();
		
		System.out.println("Created " + nodeIds.length + " test nodes");
	}

	/**
	 * Clear all test data from graph
	 */
	protected void clearTestData() {
		g.V().drop().iterate();
		g.tx().commit();
	}
}
