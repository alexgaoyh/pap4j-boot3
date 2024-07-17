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
        catalogDTOList.add(new CatalogDTO("1", "目录一"));
        catalogDTOList.add(new CatalogDTO("1.1", "目录二"));
        catalogDTOList.add(new CatalogDTO("1.1.1", "目录三"));
        catalogDTOList.add(new CatalogDTO("1.1.2", "目录三"));
        catalogDTOList.add(new CatalogDTO("1.2", "目录二"));
        catalogDTOList.add(new CatalogDTO("1.2.1", "目录三"));
        catalogDTOList.add(new CatalogDTO("1.2.2", "目录三"));
        catalogDTOList.add(new CatalogDTO("1.2.2.1", "目录四"));
        catalogDTOList.add(new CatalogDTO("2", "目录一"));
        catalogDTOList.add(new CatalogDTO("2.1", "目录二"));
        catalogDTOList.add(new CatalogDTO("2.1.1", "目录三"));
        catalogDTOList.add(new CatalogDTO("2.1.2", "目录三"));
        catalogDTOList.add(new CatalogDTO("2.2", "目录二"));
        catalogDTOList.add(new CatalogDTO("2.2.1", "目录三"));
        catalogDTOList.add(new CatalogDTO("2.2.2", "目录三"));

        List<CatalogTreeDTO> tree = CatalogUtil.buildCatalogTree(catalogDTOList);
        System.out.println(tree);
    }
}
