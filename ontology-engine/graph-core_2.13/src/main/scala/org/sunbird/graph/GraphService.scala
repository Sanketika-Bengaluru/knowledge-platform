package org.sunbird.graph

import org.sunbird.common.Platform
import org.sunbird.common.dto.{Property, Request, Response, ResponseHandler}
import org.sunbird.common.exception.ResponseCode
import org.sunbird.graph.dac.model.{Node, Relation, SearchCriteria, SubGraph}
import org.sunbird.graph.external.ExternalPropsManager
import org.sunbird.graph.service.operation.{AsyncNodeOperations, RelationOperations, SearchOperations, NodeOperations}
import org.sunbird.graph.util.CSPMetaUtil

import java.lang
import java.util.concurrent.CompletableFuture
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.FutureConverters._

class GraphService {
    implicit  val ec: ExecutionContext = ExecutionContext.global
    val isrRelativePathEnabled: lang.Boolean = Platform.getBoolean("cloudstorage.metadata.replace_absolute_path", false)

    def addNode(graphId: String, node: Node): Future[Node] = {
        if(isrRelativePathEnabled) {
            val metadata = CSPMetaUtil.updateRelativePath(node.getMetadata)
            node.setMetadata(metadata)
        }
        AsyncNodeOperations.createNodeAsync(graphId, node).asScala
            .map(resNode => if(isrRelativePathEnabled) CSPMetaUtil.updateAbsolutePath(resNode) else resNode)
    }

    def upsertNode(graphId: String, node: Node, request: Request): Future[Node] = {
        if(isrRelativePathEnabled) {
            val metadata = CSPMetaUtil.updateRelativePath(node.getMetadata)
            node.setMetadata(metadata)
        }
        AsyncNodeOperations.upsertNodeAsync(graphId, node).asScala
            .map(resNode => if(isrRelativePathEnabled) CSPMetaUtil.updateAbsolutePath(resNode) else resNode)
    }

    def upsertRootNode(graphId: String, request: Request): Future[Node] = {
        // For root node, we use synchronous operation wrapped in Future
        Future {
            val rootNode = new Node(graphId, null, "ROOT_NODE")
            NodeOperations.upsertNode(graphId, rootNode)
        }
    }

    def getNodeByUniqueId(graphId: String, nodeId: String, getTags: Boolean, request: Request): Future[Node] = {
        AsyncNodeOperations.getNodeByUniqueIdAsync(graphId, nodeId).asScala
            .map(node => if(isrRelativePathEnabled) CSPMetaUtil.updateAbsolutePath(node) else node)
    }

    def deleteNode(graphId: String, nodeId: String, request: Request): Future[java.lang.Boolean] = {
        AsyncNodeOperations.deleteNodeAsync(graphId, nodeId).asScala
            .map(_ => java.lang.Boolean.TRUE)
    }

    def getNodeProperty(graphId: String, identifier: String, property: String): Future[Property] = {
        Future {
            val node = NodeOperations.getNodeByUniqueId(graphId, identifier)
            val prop = new Property(property, node.getMetadata.get(property))
            if(isrRelativePathEnabled) CSPMetaUtil.updateAbsolutePath(prop) else prop
        }
    }

    def updateNodes(graphId: String, identifiers:java.util.List[String], metadata:java.util.Map[String,AnyRef]):Future[java.util.Map[String, Node]] = {
        val updatedMetadata = if(isrRelativePathEnabled) CSPMetaUtil.updateRelativePath(metadata) else metadata
        Future {
            val resultMap = new java.util.HashMap[String, Node]()
            val it = identifiers.iterator()
            while (it.hasNext) {
                val id = it.next()
                val node = NodeOperations.updateNode(graphId, id, 
                    updatedMetadata.asInstanceOf[java.util.Map[String, Object]])
                resultMap.put(id, node)
            }
            resultMap
        }
    }

    def getNodeByUniqueIds(graphId:String, searchCriteria: SearchCriteria): Future[java.util.List[Node]] = {
        Future {
            val nodes = SearchOperations.searchNodes(graphId, searchCriteria)
            val javaList: java.util.List[Node] = new java.util.ArrayList[Node]()
            nodes.forEach(node => javaList.add(node))
            if(isrRelativePathEnabled) CSPMetaUtil.updateAbsolutePath(javaList) else javaList
        }
    }

    def readExternalProps(request: Request, fields: List[String]): Future[Response] = {
        ExternalPropsManager.fetchProps(request, fields).map(res => {
            if(isrRelativePathEnabled && res.getResponseCode == ResponseCode.OK) {
                val updatedResult = CSPMetaUtil.updateExternalAbsolutePath(res.getResult)
                val response = ResponseHandler.OK()
                response.putAll(updatedResult)
                response
            } else res})
    }

    def saveExternalProps(request: Request): Future[Response] = {
        val externalProps: java.util.Map[String, AnyRef] = request.getRequest
        val updatedExternalProps = if(isrRelativePathEnabled) CSPMetaUtil.saveExternalRelativePath(externalProps) else externalProps
        request.setRequest(updatedExternalProps)
        ExternalPropsManager.saveProps(request)
    }

    def saveExternalPropsWithTtl(request: Request, ttl: Int): Future[Response] = {
        val externalProps: java.util.Map[String, AnyRef] = request.getRequest
        val updatedExternalProps = if (isrRelativePathEnabled) CSPMetaUtil.saveExternalRelativePath(externalProps) else externalProps
        request.setRequest(updatedExternalProps)
        ExternalPropsManager.savePropsWithTtl(request, ttl)
    }
    def updateExternalProps(request: Request): Future[Response] = {
        val externalProps: java.util.Map[String, AnyRef] = request.getRequest
        val updatedExternalProps = if (isrRelativePathEnabled) CSPMetaUtil.updateExternalRelativePath(externalProps) else externalProps
        request.setRequest(updatedExternalProps)
        ExternalPropsManager.update(request)
    }

    def updateExternalPropsWithTtl(request: Request, ttl: Int): Future[Response] = {
        val externalProps: java.util.Map[String, AnyRef] = request.getRequest
        val updatedExternalProps = if (isrRelativePathEnabled) CSPMetaUtil.updateExternalRelativePath(externalProps) else externalProps
        request.setRequest(updatedExternalProps)
        ExternalPropsManager.updateWithTtl(request, ttl)
    }

    def deleteExternalProps(request: Request): Future[Response] = {
        ExternalPropsManager.deleteProps(request)
    }
    def checkCyclicLoop(graphId:String, endNodeId: String, startNodeId: String, relationType: String) = {
        Future {
            RelationOperations.hasCycle(graphId, startNodeId, 10)
        }
    }

    def removeRelation(graphId: String, relationMap: java.util.List[java.util.Map[String, AnyRef]]) = {
        Future {
            val it = relationMap.iterator()
            while (it.hasNext) {
                val map = it.next()
                val startNodeId = map.get("startNodeId").asInstanceOf[String]
                val endNodeId = map.get("endNodeId").asInstanceOf[String]
                val relationType = map.get("relationType").asInstanceOf[String]
                RelationOperations.deleteRelation(graphId, startNodeId, relationType, endNodeId)
            }
        }
    }

    def createRelation(graphId: String, relationMap: java.util.List[java.util.Map[String, AnyRef]]) = {
        AsyncNodeOperations.createRelationsBatchAsync(graphId, 
            relationMap.asInstanceOf[java.util.List[java.util.Map[String, Object]]]
        ).asScala
    }

    def getSubGraph(graphId: String, nodeId: String, depth: Int): Future[SubGraph] = {
        Future {
            val subgraphMap = RelationOperations.getSubgraph(graphId, nodeId, depth)
            // Convert List[Node] to Map[String, Node]
            val nodesList = subgraphMap.get("nodes").asInstanceOf[java.util.List[Node]]
            val nodesMap = new java.util.HashMap[String, Node]()
            nodesList.forEach(node => nodesMap.put(node.getIdentifier, node))
            
            val relationsList = subgraphMap.get("relations").asInstanceOf[java.util.List[Relation]]
            new SubGraph(nodesMap, relationsList)
        }
    }
}

