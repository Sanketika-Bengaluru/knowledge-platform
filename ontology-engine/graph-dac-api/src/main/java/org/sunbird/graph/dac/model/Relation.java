package org.sunbird.graph.dac.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Property;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.sunbird.common.exception.ServerException;
import org.sunbird.graph.common.enums.SystemProperties;
import org.sunbird.graph.dac.enums.GraphDACErrorCodes;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class Relation implements Serializable {

	private static final long serialVersionUID = -7207054262120122453L;
	private long id;
	private String graphId;
	private String relationType;
	private String startNodeId;
	private String endNodeId;
	private String startNodeName;
	private String endNodeName;
	private String startNodeType;
	private String endNodeType;
	private String startNodeObjectType;
	private String endNodeObjectType;
	private Map<String, Object> metadata;
	private Map<String, Object> startNodeMetadata;
	private Map<String, Object> endNodeMetadata;

	public Relation() {

	}

	public Relation(String startNodeId, String relationType, String endNodeId) {
		this.startNodeId = startNodeId;
		this.endNodeId = endNodeId;
		this.relationType = relationType;
	}

	// Tinkerpop Edge constructor for JanusGraph
	public Relation(String graphId, Edge edge) {
		if (null == edge)
			throw new ServerException(GraphDACErrorCodes.ERR_GRAPH_NULL_DB_REL.name(),
					"Failed to create relation object. Relation from database is null.");
		this.graphId = graphId;

		// Get start and end vertices
		Vertex startVertex = edge.outVertex();
		Vertex endVertex = edge.inVertex();

		// Extract IDs and names
		this.startNodeId = getVertexProperty(startVertex, SystemProperties.IL_UNIQUE_ID.name());
		this.endNodeId = getVertexProperty(endVertex, SystemProperties.IL_UNIQUE_ID.name());
		this.startNodeName = getVertexName(startVertex);
		this.endNodeName = getVertexName(endVertex);
		this.startNodeType = getVertexProperty(startVertex, SystemProperties.IL_SYS_NODE_TYPE.name());
		this.endNodeType = getVertexProperty(endVertex, SystemProperties.IL_SYS_NODE_TYPE.name());
		this.startNodeObjectType = getVertexProperty(startVertex, SystemProperties.IL_FUNC_OBJECT_TYPE.name());
		this.endNodeObjectType = getVertexProperty(endVertex, SystemProperties.IL_FUNC_OBJECT_TYPE.name());
		this.relationType = edge.label();

		// Extract metadata from edge properties
		this.metadata = new HashMap<String, Object>();
		Iterator<Property<Object>> properties = edge.properties();
		while (properties.hasNext()) {
			Property<Object> property = properties.next();
			this.metadata.put(property.key(), property.value());
		}

		// Extract node metadata
		this.startNodeMetadata = getVertexMetadata(startVertex);
		this.endNodeMetadata = getVertexMetadata(endVertex);
	}

	// Helper methods for Tinkerpop Vertex

	private String getVertexProperty(Vertex vertex, String propertyKey) {
		if (vertex == null) return null;
		Iterator<VertexProperty<Object>> props = vertex.properties(propertyKey);
		if (props.hasNext()) {
			Object value = props.next().value();
			return value != null ? value.toString() : null;
		}
		return null;
	}

	private String getVertexName(Vertex vertex) {
		String name = getVertexProperty(vertex, "name");
		if (StringUtils.isBlank(name)) {
			name = getVertexProperty(vertex, "title");
			if (StringUtils.isBlank(name)) {
				name = getVertexProperty(vertex, SystemProperties.IL_FUNC_OBJECT_TYPE.name());
				if (StringUtils.isBlank(name))
					name = getVertexProperty(vertex, SystemProperties.IL_SYS_NODE_TYPE.name());
			}
		}
		return name;
	}

	private Map<String, Object> getVertexMetadata(Vertex vertex) {
		Map<String, Object> metadata = new HashMap<>();
		if (vertex != null) {
			Iterator<VertexProperty<Object>> properties = vertex.properties();
			while (properties.hasNext()) {
				VertexProperty<Object> property = properties.next();
				metadata.put(property.key(), property.value());
			}
		}
		return metadata;
	}

	public String getRelationType() {
		return relationType;
	}

	public void setRelationType(String relationType) {
		this.relationType = relationType;
	}

	public String getStartNodeId() {
		return startNodeId;
	}

	public void setStartNodeId(String startNodeId) {
		this.startNodeId = startNodeId;
	}

	public String getEndNodeId() {
		return endNodeId;
	}

	public void setEndNodeId(String endNodeId) {
		this.endNodeId = endNodeId;
	}

	// TODO: In 3.0 if metadata is empty set it with new HashMap and return (to handle NPE.
//	public Map<String, Object> getMetadata() {
//		if (MapUtils.isEmpty(metadata))
//			metadata = new HashMap<String, Object>();
//		return metadata;
//	}

	public Map<String, Object> getMetadata() {
		if (!MapUtils.isEmpty(metadata))
			return metadata;
		else
			return new HashMap<String, Object>();
	}

	public Relation updateMetadata(Map<String, Object> metadata) {
		if (!MapUtils.isEmpty(metadata))
			this.metadata = metadata;
		return this;
	}

	public void setMetadata(Map<String, Object> metadata) {
		this.metadata = metadata;
	}

	public String getGraphId() {
		return graphId;
	}

	public void setGraphId(String graphId) {
		this.graphId = graphId;
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public String getStartNodeName() {
		return startNodeName;
	}

	public void setStartNodeName(String startNodeName) {
		this.startNodeName = startNodeName;
	}

	public String getEndNodeName() {
		return endNodeName;
	}

	public void setEndNodeName(String endNodeName) {
		this.endNodeName = endNodeName;
	}

	public String getStartNodeType() {
		return startNodeType;
	}

	public void setStartNodeType(String startNodeType) {
		this.startNodeType = startNodeType;
	}

	public String getEndNodeType() {
		return endNodeType;
	}

	public void setEndNodeType(String endNodeType) {
		this.endNodeType = endNodeType;
	}

	public String getStartNodeObjectType() {
		return startNodeObjectType;
	}

	public void setStartNodeObjectType(String startNodeObjectType) {
		this.startNodeObjectType = startNodeObjectType;
	}

	public String getEndNodeObjectType() {
		return endNodeObjectType;
	}

	public void setEndNodeObjectType(String endNodeObjectType) {
		this.endNodeObjectType = endNodeObjectType;
	}

	@JsonIgnore
	public Map<String, Object> getStartNodeMetadata() {
		return startNodeMetadata;
	}

	@JsonIgnore
	public void setStartNodeMetadata(Map<String, Object> startNodeMetadata) {
		this.startNodeMetadata = startNodeMetadata;
	}

	@JsonIgnore
	public Map<String, Object> getEndNodeMetadata() {
		return endNodeMetadata;
	}

	@JsonIgnore
	public void setEndNodeMetadata(Map<String, Object> endNodeMetadata) {
		this.endNodeMetadata = endNodeMetadata;
	}
}
