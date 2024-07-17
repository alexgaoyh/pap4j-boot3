package cn.net.pap.common.datastructure.catalog;

import cn.net.pap.common.datastructure.catalog.dto.CatalogDTO;
import cn.net.pap.common.datastructure.catalog.dto.CatalogTreeDTO;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CatalogUtil {

    private static final Map<String, Integer> TYPE_LEVEL = new HashMap<>();

    static {
        TYPE_LEVEL.put("目录一", 1);
        TYPE_LEVEL.put("目录二", 2);
        TYPE_LEVEL.put("目录三", 3);
        TYPE_LEVEL.put("目录四", 4);
    }

    public static List<CatalogTreeDTO> buildCatalogTree(List<CatalogDTO> catalogDTOList) {
        Map<String, CatalogTreeDTO> map = new HashMap<>();
        List<CatalogTreeDTO> roots = new ArrayList<>();

        for (CatalogDTO dto : catalogDTOList) {
            CatalogTreeDTO node = new CatalogTreeDTO(dto.getText(), dto.getType());
            map.put(dto.getText(), node);

            if (TYPE_LEVEL.get(dto.getType()) == 1) {
                roots.add(node);
            } else {
                String parentText = findParent(map, dto.getText(), dto.getType());
                CatalogTreeDTO parent = map.get(parentText);
                if (parent != null) {
                    parent.addChild(node);
                }
            }
        }

        return roots;
    }

    private static String findParent(Map<String, CatalogTreeDTO> map, String text, String type) {
        int level = TYPE_LEVEL.get(type) - 1;
        while (level > 0) {
            text = text.substring(0, text.lastIndexOf('.'));
            String parentType = getTypeByLevel(level);
            CatalogTreeDTO potentialParent = map.get(text);
            if (potentialParent != null && potentialParent.getType().equals(parentType)) {
                break;
            }
            level--;
        }
        return text;
    }

    private static String getTypeByLevel(int level) {
        for (Map.Entry<String, Integer> entry : TYPE_LEVEL.entrySet()) {
            if (entry.getValue() == level) {
                return entry.getKey();
            }
        }
        return null;
    }


}
