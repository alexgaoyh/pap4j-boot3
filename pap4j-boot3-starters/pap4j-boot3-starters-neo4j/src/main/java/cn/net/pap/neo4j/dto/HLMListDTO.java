package cn.net.pap.neo4j.dto;

import cn.net.pap.neo4j.entity.HLMEntity;
import cn.net.pap.neo4j.entity.HLMRelationshipEntity;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * HLMEntity 集合的集合
 */
public class HLMListDTO implements Serializable {

    private List<HLMEntity> details;

    private List<HLMRelationshipEntity> relations;

    public List<HLMEntity> getDetails() {
        return details;
    }

    public void setDetails(List<HLMEntity> details) {
        this.details = details;
    }

    public List<HLMRelationshipEntity> getRelations() {
        return relations;
    }

    public void setRelations(List<HLMRelationshipEntity> relations) {
        this.relations = relations;
    }

    /**
     * 如果 details 里面的值去重后也完全相同，则移除这个对象。 从而达到获得最纯粹的一个环。
     */
    public static List<HLMListDTO> distinct(List<HLMListDTO> inputList) {
        List<HLMListDTO> returnList = new ArrayList<>();
        Set<List<HLMEntity>> distinctSet = new HashSet<>();
        for (HLMListDTO input : inputList) {
            List<HLMEntity> inputDistinctList = input.getDetails().stream().distinct().sorted(HLMEntity::sort).collect(Collectors.toList());
            if (distinctSet.contains(inputDistinctList)) {
                continue;
            }
            distinctSet.add(inputDistinctList);
            returnList.add(input);
        }

        return returnList;
    }

}
