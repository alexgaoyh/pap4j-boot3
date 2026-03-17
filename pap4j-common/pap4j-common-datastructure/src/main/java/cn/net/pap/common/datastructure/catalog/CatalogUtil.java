package cn.net.pap.common.datastructure.catalog;

import cn.net.pap.common.datastructure.catalog.dto.CatalogDTO;
import cn.net.pap.common.datastructure.catalog.dto.CatalogTreeDTO;

import java.util.*;

/**
 * <h1>目录工具类 (Catalog Utility)</h1>
 * <p>提供将扁平化的目录列表转换为树形结构目录的实用静态方法。</p>
 * <ul>
 *     <li>构建标准目录树: {@link #buildCatalogTree(List)}</li>
 *     <li>构建允许跨层级的目录树: {@link #buildCatalogTree2(List)}</li>
 * </ul>
 *
 * @author alexgaoyh
 */
public class CatalogUtil {

    private static final Map<String, Integer> TYPE_LEVEL = new HashMap<>();

    static {
        TYPE_LEVEL.put("目录一", 1);
        TYPE_LEVEL.put("目录二", 2);
        TYPE_LEVEL.put("目录三", 3);
        TYPE_LEVEL.put("目录四", 4);
    }

    /**
     * <p>根据传入的目录数据列表，构建标准的树形目录结构。</p>
     * <p>此方法要求目录层级必须是连续的，不能跨越层级。</p>
     *
     * @param catalogDTOList 扁平化的目录数据列表
     * @return 构建好的树形目录根节点列表
     */
    public static List<CatalogTreeDTO> buildCatalogTree(List<CatalogDTO> catalogDTOList) {
        Map<String, CatalogTreeDTO> map = new HashMap<>();
        List<CatalogTreeDTO> roots = new ArrayList<>();

        for (CatalogDTO dto : catalogDTOList) {
            CatalogTreeDTO node = new CatalogTreeDTO(dto.getId(), dto.getText(), dto.getType());
            map.put(dto.getType(), node);

            if (TYPE_LEVEL.get(dto.getType()) == 1) {
                roots.add(node);
            } else {
                CatalogTreeDTO parent = findParent(map, dto);
                if (parent != null) {
                    node.setPid(parent.getId());
                    parent.addChild(node);
                } else {
                    roots.add(node);
                }
            }
        }

        return roots;
    }

    /**
     * <p>查找当前目录节点的父节点（标准模式）。</p>
     *
     * @param map 包含已处理节点的映射，键为目录类型
     * @param dto 当前的目录数据对象
     * @return 找到的父节点对象，如果没有找到则返回 {@code null}
     */
    private static CatalogTreeDTO findParent(Map<String, CatalogTreeDTO> map, CatalogDTO dto) {
        int currentLevel = TYPE_LEVEL.get(dto.getType()) - 1;

        while (currentLevel > 0) {
            String parentType = getTypeByLevel(currentLevel);
            CatalogTreeDTO potentialParent = map.get(parentType);
            return potentialParent;
        }

        return null;
    }

    /**
     * <p>根据传入的目录数据列表，构建允许跨层级的树形目录结构。</p>
     * <p>相对于 {@link #buildCatalogTree(List)}，此方法允许层级不连续，例如允许 <strong>'目录一'</strong> 下面直接跟着 <strong>'目录三'</strong>。</p>
     *
     * @param catalogDTOList 扁平化的目录数据列表
     * @return 构建好的树形目录根节点列表
     */
    public static List<CatalogTreeDTO> buildCatalogTree2(List<CatalogDTO> catalogDTOList) {
        Map<String, CatalogTreeDTO> map = new HashMap<>();
        List<CatalogTreeDTO> roots = new ArrayList<>();

        for (CatalogDTO dto : catalogDTOList) {
            CatalogTreeDTO node = new CatalogTreeDTO(dto.getId(), dto.getText(), dto.getType());
            map.put(dto.getType(), node);

            if (TYPE_LEVEL.get(dto.getType()) == 1) {
                roots.add(node);
            } else {
                CatalogTreeDTO parent = findParent2(map, dto);
                if (parent != null) {
                    node.setPid(parent.getId());
                    parent.addChild(node);
                } else {
                    roots.add(node);
                }
            }
        }

        return roots;
    }

    /**
     * <p>查找当前目录节点的父节点（允许跨层级模式）。</p>
     * <p>此方法相对于 {@link #findParent(Map, CatalogDTO)}，区别在于允许层级不连续，允许向上跳跃查找父级。</p>
     * 
     * @param map 包含已处理节点的映射，键为目录类型
     * @param dto 当前的目录数据对象
     * @return 找到的父节点对象，如果没有找到则返回 {@code null}
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

    /**
     * <p>根据层级数值获取对应的目录类型字符串。</p>
     *
     * @param level 目录的层级数值
     * @return 对应的目录类型字符串（如 "目录一"），如果没有匹配的则返回 {@code null}
     */
    private static String getTypeByLevel(int level) {
        for (Map.Entry<String, Integer> entry : TYPE_LEVEL.entrySet()) {
            if (entry.getValue() == level) {
                return entry.getKey();
            }
        }
        return null;
    }

}
