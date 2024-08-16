package cn.net.pap.common.datastructure.catalog;

import cn.net.pap.common.datastructure.catalog.dto.CatalogDTO;
import cn.net.pap.common.datastructure.catalog.dto.CatalogTreeDTO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class CatalogUtilTest {

    @Test
    public void convert(){
        List<CatalogDTO> catalogDTOList = new ArrayList<>();
        catalogDTOList.add(new CatalogDTO("w", "目录一"));
        catalogDTOList.add(new CatalogDTO("w1", "目录一"));
        catalogDTOList.add(new CatalogDTO("f", "目录二"));
        catalogDTOList.add(new CatalogDTO("e", "目录三"));
        catalogDTOList.add(new CatalogDTO("v", "目录三"));
        catalogDTOList.add(new CatalogDTO("t", "目录二"));
        catalogDTOList.add(new CatalogDTO("b", "目录三"));
        catalogDTOList.add(new CatalogDTO("aa", "目录三"));
        catalogDTOList.add(new CatalogDTO("aaa", "目录四"));
        catalogDTOList.add(new CatalogDTO("z", "目录一"));
        catalogDTOList.add(new CatalogDTO("x", "目录二"));
        catalogDTOList.add(new CatalogDTO("c", "目录三"));
        catalogDTOList.add(new CatalogDTO("v", "目录三"));
        catalogDTOList.add(new CatalogDTO("b", "目录二"));
        catalogDTOList.add(new CatalogDTO("n", "目录三"));
        catalogDTOList.add(new CatalogDTO("m", "目录三"));

        List<CatalogTreeDTO> tree = CatalogUtil.buildCatalogTree(catalogDTOList);
        System.out.println(tree);

        List<CatalogTreeDTO> tree2 = CatalogUtil.buildCatalogTree2(catalogDTOList);
        System.out.println(tree2);
    }
}
