package cn.net.pap.neo4j.util.kg;

import org.neo4j.driver.internal.value.PathValue;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Path;
import org.neo4j.driver.types.Relationship;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PathValue2KGConvert {

    /**
     * 将 List<List<PathValue>> shortestPaths 类型的数据转换为 知识图谱需要的数据结构
     *
     * @param shortestPaths
     * @return
     */
    public static Map<String, Object> convertToKnowledgeGraph(List<List<PathValue>> shortestPaths) {
        Map<String, Object> knowledgeGraph = new HashMap<>();
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> relations = new ArrayList<>();

        for (List<PathValue> pathValues : shortestPaths) {
            for (PathValue pathValue : pathValues) {
                Path path = pathValue.asPath();
                for (Node node : path.nodes()) {
                    Map<String, Object> nodeData = convertNodeToMap(node);
                    nodes.add(nodeData);
                }
                for (Relationship relationship : path.relationships()) {
                    Map<String, Object> relationData = convertRelationshipToMap(relationship);
                    relations.add(relationData);
                }
            }
        }

        knowledgeGraph.put("nodes", nodes);
        knowledgeGraph.put("relations", relations);
        return knowledgeGraph;
    }

    private static Map<String, Object> convertNodeToMap(Node node) {
        Map<String, Object> nodeData = new HashMap<>();
        nodeData.put("id", node.id());
        nodeData.put("properties", node.asMap());
        return nodeData;
    }

    private static Map<String, Object> convertRelationshipToMap(Relationship relationship) {
        Map<String, Object> relationData = new HashMap<>();
        relationData.put("startNode", relationship.startNodeId());
        relationData.put("endNode", relationship.endNodeId());
        relationData.put("properties", relationship.asMap());
        return relationData;
    }

}
