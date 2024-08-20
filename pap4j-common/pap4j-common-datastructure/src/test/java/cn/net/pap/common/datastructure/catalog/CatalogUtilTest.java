package cn.net.pap.common.datastructure.catalog;

import cn.net.pap.common.datastructure.catalog.dto.CatalogDTO;
import cn.net.pap.common.datastructure.catalog.dto.CatalogTreeDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CatalogUtilTest {

    @Test
    public void convert() throws Exception {
        List<CatalogDTO> catalogDTOList = new ArrayList<>();
        catalogDTOList.add(new CatalogDTO(UUID.randomUUID().toString(), "w", "目录一"));
        catalogDTOList.add(new CatalogDTO(UUID.randomUUID().toString(), "w1", "目录一"));
        catalogDTOList.add(new CatalogDTO(UUID.randomUUID().toString(), "f", "目录二"));
        catalogDTOList.add(new CatalogDTO(UUID.randomUUID().toString(), "e", "目录三"));
        catalogDTOList.add(new CatalogDTO(UUID.randomUUID().toString(), "v", "目录三"));
        catalogDTOList.add(new CatalogDTO(UUID.randomUUID().toString(), "t", "目录二"));
        catalogDTOList.add(new CatalogDTO(UUID.randomUUID().toString(), "b", "目录三"));
        catalogDTOList.add(new CatalogDTO(UUID.randomUUID().toString(), "aa", "目录三"));
        catalogDTOList.add(new CatalogDTO(UUID.randomUUID().toString(), "aaa", "目录四"));
        catalogDTOList.add(new CatalogDTO(UUID.randomUUID().toString(), "z", "目录一"));
        catalogDTOList.add(new CatalogDTO(UUID.randomUUID().toString(), "x", "目录二"));
        catalogDTOList.add(new CatalogDTO(UUID.randomUUID().toString(), "c", "目录三"));
        catalogDTOList.add(new CatalogDTO(UUID.randomUUID().toString(), "v", "目录三"));
        catalogDTOList.add(new CatalogDTO(UUID.randomUUID().toString(), "b", "目录二"));
        catalogDTOList.add(new CatalogDTO(UUID.randomUUID().toString(), "n", "目录三"));
        catalogDTOList.add(new CatalogDTO(UUID.randomUUID().toString(), "m", "目录三"));

        ObjectMapper objectMapper = new ObjectMapper();

        List<CatalogTreeDTO> tree = CatalogUtil.buildCatalogTree(catalogDTOList);
        System.out.println(objectMapper.writeValueAsString(tree));

        List<CatalogTreeDTO> tree2 = CatalogUtil.buildCatalogTree2(catalogDTOList);
        System.out.println(objectMapper.writeValueAsString(tree2));
    }
}
