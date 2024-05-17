package cn.net.pap.neo4j.util.kg;

import cn.net.pap.neo4j.entity.HobbyEntity;
import cn.net.pap.neo4j.entity.PersonEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PersonEntity 实体类在查询出来之后，存在循环引用无法序列化的情况。
 * 这里增加一个工具类，把 PersonEntity 转换为 知识图谱 对应的 nodes relations 节点的形式.
 */
public class PersonEntity2KGConvert {

    public static Map<String, Object> convertToKnowledgeGraph(PersonEntity person) {
        Map<String, Object> graph = new HashMap<>();
        graph.put("nodes", new ArrayList<>());
        graph.put("relations", new ArrayList<>());

        Map<String, Object> personNode = new HashMap<>();
        personNode.put("id", person.getPersonId());
        personNode.put("type", "person");
        personNode.put("properties", new HashMap<String, Object>() {{
            put("personName", person.getPersonName());
            put("description", person.getDescription());
        }});
        ((ArrayList) graph.get("nodes")).add(personNode);

        // Convert hobbies to nodes and relationships
        for (HobbyEntity hobby : person.getHobbys()) {
            Map<String, Object> hobbyNode = new HashMap<>();
            hobbyNode.put("id", hobby.getHobbyId());
            hobbyNode.put("type", "hobby");
            hobbyNode.put("properties", new HashMap<String, Object>() {{
                put("hobbyName", hobby.getHobbyName());
            }});
            ((ArrayList) graph.get("nodes")).add(hobbyNode);

            Map<String, Object> relation = new HashMap<>();
            relation.put("startNode", person.getPersonId());
            relation.put("endNode", hobby.getHobbyId());
            relation.put("type", "hobbys");
            ((ArrayList) graph.get("relations")).add(relation);
        }

        for (PersonEntity parent : person.getParents()) {
            convertPersonEntity(parent, person.getPersonId(), graph, "parents");
        }

        for (PersonEntity child : person.getChildrens()) {
            convertPersonEntity(child, person.getPersonId(), graph, "childrens");
        }

        return graph;
    }

    private static boolean isNodeExists(List<Map<String, Object>> nodes, String id) {
        for (Map<String, Object> node : nodes) {
            if (node.get("id").equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static void convertPersonEntity(PersonEntity person, String relatedTo, Map<String, Object> graph, String relationType) {
        if (isNodeExists((List<Map<String, Object>>) graph.get("nodes"), person.getPersonId())) {
            Map<String, Object> relation = new HashMap<>();
            relation.put("startNode", relatedTo);
            relation.put("endNode", person.getPersonId());
            relation.put("type", relationType);
            ((ArrayList) graph.get("relations")).add(relation);
            return;
        }

        Map<String, Object> personNode = new HashMap<>();
        personNode.put("id", person.getPersonId());
        personNode.put("type", "person");
        personNode.put("properties", new HashMap<String, Object>() {{
            put("personName", person.getPersonName());
            put("description", person.getDescription());
        }});
        ((ArrayList) graph.get("nodes")).add(personNode);

        Map<String, Object> relation = new HashMap<>();
        relation.put("startNode", relatedTo);
        relation.put("endNode", person.getPersonId());
        relation.put("type", relationType);
        ((ArrayList) graph.get("relations")).add(relation);

        for (HobbyEntity hobby : person.getHobbys()) {
            Map<String, Object> hobbyNode = new HashMap<>();
            hobbyNode.put("id", hobby.getHobbyId());
            hobbyNode.put("type", "hobby");
            hobbyNode.put("properties", new HashMap<String, Object>() {{
                put("hobbyName", hobby.getHobbyName());
            }});
            ((ArrayList) graph.get("nodes")).add(hobbyNode);

            Map<String, Object> relationH = new HashMap<>();
            relationH.put("startNode", person.getPersonId());
            relationH.put("endNode", hobby.getHobbyId());
            relationH.put("type", "hobbys");
            ((ArrayList) graph.get("relations")).add(relationH);
        }

        for (PersonEntity parent : person.getParents()) {
            convertPersonEntity(parent, person.getPersonId(), graph, "parents");
        }

        for (PersonEntity child : person.getChildrens()) {
            convertPersonEntity(child, person.getPersonId(), graph, "childrens");
        }
    }
}
