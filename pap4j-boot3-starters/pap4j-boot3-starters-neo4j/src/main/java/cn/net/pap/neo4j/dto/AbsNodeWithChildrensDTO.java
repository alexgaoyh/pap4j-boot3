package cn.net.pap.neo4j.dto;

import cn.net.pap.neo4j.entity.AbsNodeEntity;
import org.neo4j.driver.internal.InternalNode;
import org.neo4j.driver.internal.value.MapValue;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class AbsNodeWithChildrensDTO implements Serializable {

    /**
     * 节点 node 父
     */
    private AbsNodeEntity parentNode;

    /**
     * 节点 node 子
     */
    private List<AbsNodeEntity> childrenNodes;

    /**
     * 构造函数
     * @param parentNode
     * @param childrenNodes
     */
    public AbsNodeWithChildrensDTO(AbsNodeEntity parentNode, List<AbsNodeEntity> childrenNodes) {
        this.parentNode = parentNode;
        this.childrenNodes = childrenNodes;
    }

    public AbsNodeEntity getParentNode() {
        return parentNode;
    }

    public void setParentNode(AbsNodeEntity parentNode) {
        this.parentNode = parentNode;
    }

    public List<AbsNodeEntity> getChildrenNodes() {
        return childrenNodes;
    }

    public void setChildrenNodes(List<AbsNodeEntity> childrenNodes) {
        this.childrenNodes = childrenNodes;
    }

    /**
     * 对象转换 convert(absNodeRepository.getParentWithChildrens("parent1"));
     *
     * @param inputObjectArrayList
     * @return
     */
    public static List<AbsNodeWithChildrensDTO> convert(List<Object[]> inputObjectArrayList) {
        List<AbsNodeWithChildrensDTO> dtoList = new ArrayList<>();
        for (Object[] result : inputObjectArrayList) {
            InternalNode parentInternalNode = (InternalNode) ((MapValue) result[0]).get("parentNode").asNode();
            AbsNodeEntity parentNode = convertInternal(parentInternalNode);

            List<AbsNodeEntity> childrenNodes = new ArrayList<>();
            for (Object childrenNodeObject : ((MapValue) result[0]).get("childrenNodes").asList()) {
                AbsNodeEntity childrenNode = convertInternal((InternalNode) childrenNodeObject);
                childrenNodes.add(childrenNode);
            }

            AbsNodeWithChildrensDTO absNodeWithChildrensDTO = new AbsNodeWithChildrensDTO(parentNode, childrenNodes);
            dtoList.add(absNodeWithChildrensDTO);

        }
        return dtoList;
    }

    private static AbsNodeEntity convertInternal(InternalNode internalNode) {
        AbsNodeEntity absNodeEntity = new AbsNodeEntity(
                internalNode.get("absNodeId").toString(),
                internalNode.get("absNodeLabel") != null ? internalNode.get("absNodeLabel").toString() : "",
                internalNode.get("absNodeType") != null ? internalNode.get("absNodeType").toString() : ""
        );
        return absNodeEntity;
    }
}
