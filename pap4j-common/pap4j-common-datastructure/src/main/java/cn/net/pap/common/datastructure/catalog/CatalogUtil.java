package cn.net.pap.common.datastructure.catalog;

import cn.net.pap.common.datastructure.catalog.dto.CatalogDTO;
import cn.net.pap.common.datastructure.catalog.dto.CatalogTreeDTO;

import java.util.*;

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
            map.put(dto.getType(), node);

            if (TYPE_LEVEL.get(dto.getType()) == 1) {
                roots.add(node);
            } else {
                CatalogTreeDTO parent = findParent(map, dto);
                if (parent != null) {
                    parent.addChild(node);
                } else {
                    roots.add(node);
                }
            }
        }

        return roots;
    }

    private static CatalogTreeDTO findParent(Map<String, CatalogTreeDTO> map, CatalogDTO dto) {
        int currentLevel = TYPE_LEVEL.get(dto.getType()) - 1;

        while (currentLevel > 0) {
            String parentType = getTypeByLevel(currentLevel);
            CatalogTreeDTO potentialParent = map.get(parentType);
            return potentialParent;
        }

        return null;
    }

    public static List<CatalogTreeDTO> buildCatalogTree2(List<CatalogDTO> catalogDTOList) {
        Map<String, CatalogTreeDTO> map = new HashMap<>();
        List<CatalogTreeDTO> roots = new ArrayList<>();

        for (CatalogDTO dto : catalogDTOList) {
            CatalogTreeDTO node = new CatalogTreeDTO(dto.getText(), dto.getType());
            map.put(dto.getType(), node);

            if (TYPE_LEVEL.get(dto.getType()) == 1) {
                roots.add(node);
            } else {
                CatalogTreeDTO parent = findParent2(map, dto);
                if (parent != null) {
                    parent.addChild(node);
                } else {
                    roots.add(node);
                }
            }
        }

        return roots;
    }

    /**
     * 此方法相对于 findParent，区别在于允许层级不连续，允许'目录一'下面直接跟着'目录三'。
     * @param map
     * @param dto
     * @return
     */
    private static CatalogTreeDTO findParent2(Map<String, CatalogTreeDTO> map, CatalogDTO dto) {
        CatalogTreeDTO returnDTO = null;
        int currentLevel = TYPE_LEVEL.get(dto.getType()) - 1;

        while (currentLevel > 0) {
            String parentType = getTypeByLevel(currentLevel);
            CatalogTreeDTO potentialParent = map.get(parentType);
            returnDTO = potentialParent;
            if(returnDTO != null) {
                break;
            } else {
                currentLevel--;
            }
        }

        return returnDTO;
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
