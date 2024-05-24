package cn.net.pap.common.excel.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 父子关系， 类似上下级部门的处理
 */
public class ParentChildDTO implements Serializable {

    private String remark;

    private ParentChildDTO parent;

    private List<ParentChildDTO> child;

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public ParentChildDTO getParent() {
        return parent;
    }

    public void setParent(ParentChildDTO parent) {
        this.parent = parent;
    }

    public List<ParentChildDTO> getChild() {
        return child;
    }

    public void setChild(List<ParentChildDTO> child) {
        this.child = child;
    }

    /**
     * 父子数据结构转换
     *
     * @param rowList 是使用EasyExcel解析出来的数据， Excel包含三列： 部门、上级部门、备注， 其中'部门'和'上级部门'这两个数据是有关联关系的，备注是用来表示额外的属性
     * @return
     */
    public static List<ParentChildDTO> convertToParentChildList(List<Map<String, Object>> rowList) {
        Map<String, ParentChildDTO> dtoMap = new HashMap<>();

        for (Map<String, Object> row : rowList) {
            String department = (String) row.get("部门");
            String remark = (String) row.get("备注");

            ParentChildDTO dto = new ParentChildDTO();
            dto.setRemark(remark);
            dtoMap.put(department, dto);
        }

        List<ParentChildDTO> rootList = new ArrayList<>();
        for (Map<String, Object> row : rowList) {
            String department = (String) row.get("部门");
            String parentDepartment = (String) row.get("上级部门");

            ParentChildDTO dto = dtoMap.get(department);

            if (parentDepartment == null) {
                rootList.add(dto);
            } else {
                ParentChildDTO parentDto = dtoMap.get(parentDepartment);
                if (parentDto != null) {
                    List<ParentChildDTO> child1 = parentDto.getChild();
                    if (child1 == null) {
                        child1 = new ArrayList<>();
                    }
                    child1.add(dto);
                    parentDto.setChild(child1);
                    dto.setParent(parentDto);
                }
            }
        }

        return rootList;
    }

}
