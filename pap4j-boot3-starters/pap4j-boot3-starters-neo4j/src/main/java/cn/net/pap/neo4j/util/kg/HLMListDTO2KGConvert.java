package cn.net.pap.neo4j.util.kg;

import cn.net.pap.neo4j.dto.HLMListDTO;
import cn.net.pap.neo4j.entity.HLMEntity;
import cn.net.pap.neo4j.entity.HLMRelationshipEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HLMListDTO2KGConvert {

    public static Map<String, Object> convertToKnowledgeGraph(List<HLMListDTO> hlmList) {
        Map<String, Object> graph = new HashMap<>();
        graph.put("nodes", new ArrayList<>());
        graph.put("relations", new ArrayList<>());

        for (HLMListDTO hlm : hlmList) {
            for (HLMEntity hlmEntity : hlm.getDetails()) {
                if (isNodeExists((List<Map<String, Object>>) graph.get("nodes"), hlmEntity.getName())) {
                    continue;
                }
                Map<String, Object> hlmNode = new HashMap<>();
                hlmNode.put("name", hlmEntity.getName());
                hlmNode.put("type", "hlm");
                ((ArrayList) graph.get("nodes")).add(hlmNode);
            }

            for (HLMRelationshipEntity relationship : hlm.getRelations()) {
                Map<String, Object> relation = new HashMap<>();
                relation.put("startNode", relationship.getStartNode().getName());
                relation.put("endNode", relationship.getEndNode().getName());
                relation.put("type", relationship.getType());
                ((ArrayList) graph.get("relations")).add(relation);
            }
        }

        return graph;
    }

    public static Map<String, Object> convertToKnowledgeGraph(HLMListDTO hlm) {
        Map<String, Object> graph = new HashMap<>();
        graph.put("nodes", new ArrayList<>());
        graph.put("relations", new ArrayList<>());

        for (HLMEntity hlmEntity : hlm.getDetails()) {
            if (isNodeExists((List<Map<String, Object>>) graph.get("nodes"), hlmEntity.getName())) {
                continue;
            }
            Map<String, Object> hlmNode = new HashMap<>();
            hlmNode.put("name", hlmEntity.getName());
            hlmNode.put("type", "hlm");
            ((ArrayList) graph.get("nodes")).add(hlmNode);
        }

        for (HLMRelationshipEntity relationship : hlm.getRelations()) {
            Map<String, Object> relation = new HashMap<>();
            relation.put("startNode", relationship.getStartNode().getName());
            relation.put("endNode", relationship.getEndNode().getName());
            relation.put("type", relationship.getType());
            ((ArrayList) graph.get("relations")).add(relation);
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

}
