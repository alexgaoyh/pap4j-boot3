package cn.net.pap.neo4j.util.kg;

import cn.net.pap.neo4j.entity.HLMEntity;
import cn.net.pap.neo4j.entity.HLMRelationshipEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HLMEntity 实体类在查询出来之后，存在循环引用无法序列化的情况。
 * 这里增加一个工具类，把 HLMEntity 转换为 知识图谱 对应的 nodes relations 节点的形式.
 */
public class HLMEntity2KGConvert {

    public static Map<String, Object> convertToKnowledgeGraph(HLMEntity hlm) {
        Map<String, Object> graph = new HashMap<>();
        graph.put("nodes", new ArrayList<>());
        graph.put("relations", new ArrayList<>());

        Map<String, Object> hlmNode = new HashMap<>();
        hlmNode.put("name", hlm.getName());
        hlmNode.put("type", "hlm");
        ((ArrayList) graph.get("nodes")).add(hlmNode);

        for (HLMRelationshipEntity relationship : hlm.getRelationships()) {
            convertHLMEntity(relationship, hlm.getName(), graph, relationship.getType());
        }

        return graph;
    }

    private static boolean isNodeExists(List<Map<String, Object>> nodes, String id) {
        for (Map<String, Object> node : nodes) {
            if (node.get("name").equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static void convertHLMEntity(HLMRelationshipEntity hlm, String relatedTo, Map<String, Object> graph, String relationType) {
        if (isNodeExists((List<Map<String, Object>>) graph.get("nodes"), hlm.getEndNode().getName())) {
            Map<String, Object> relation = new HashMap<>();
            relation.put("startNode", relatedTo);
            relation.put("endNode", hlm.getEndNode().getName());
            relation.put("type", relationType);
            ((ArrayList) graph.get("relations")).add(relation);
            return;
        }

        Map<String, Object> hlmNode = new HashMap<>();
        hlmNode.put("name", hlm.getEndNode().getName());
        hlmNode.put("type", "hlm");
        ((ArrayList) graph.get("nodes")).add(hlmNode);

        Map<String, Object> relation = new HashMap<>();
        relation.put("startNode", relatedTo);
        relation.put("endNode", hlm.getEndNode().getName());
        relation.put("type", relationType);
        ((ArrayList) graph.get("relations")).add(relation);

        for (HLMRelationshipEntity relationship : hlm.getEndNode().getRelationships()) {
            convertHLMEntity(relationship, hlm.getEndNode().getName(), graph, relationship.getType());
        }

    }
}
