package cn.net.pap.example.proguard;

import cn.net.pap.example.proguard.dto.ProguardDTO;
import cn.net.pap.example.proguard.entity.Proguard;
import cn.net.pap.example.proguard.repository.ProguardRepository;
import cn.net.pap.example.proguard.service.IProguardService;
import cn.net.pap.example.proguard.util.SearchUtil;
import cn.net.pap.example.proguard.util.dto.SearchConditionDTO;
import jakarta.persistence.EntityManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertTrue;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {cn.net.pap.example.proguard.Pap4jBoot3ExampleProguardApplication.class})
public class ProguardTest {

    @Autowired
    ProguardRepository proguardRepository;

    @Autowired
    IProguardService proguardService;

    @Autowired
    private EntityManager entityManager;

    @Test
    public void projectionsTest() {
        Long proguardId = System.currentTimeMillis();

        Proguard proguard = new Proguard();
        proguard.setProguardId(proguardId);
        proguard.setProguardName(proguardId + "");
        Map<String, Object> extMap = new HashMap<>();
        extMap.put("timeswap", System.currentTimeMillis());
        extMap.put("threadId", Thread.currentThread().getName());
        proguard.setExtMap(extMap);
        List<String> extList = new ArrayList<>();
        extList.add("A");
        extList.add("B");
        extList.add("C");
        extList.add("D");
        proguard.setExtList(extList);
        proguardRepository.saveAndFlush(proguard);

        Optional<ProguardDTO> optional = proguardRepository.getProguardByProguardId(proguardId, ProguardDTO.class);
        if(optional.isPresent()) {
            System.out.println(optional.get().getProguardId() + " : " + optional.get().getProguardName());
        }

        List<Proguard> proguards = proguardService.searchAllByProguardNameRange(proguardId + "-" + (proguardId + 10l) + "," + proguardId);
        assertTrue(proguards.size() == 1);


        Proguard proguard1 = proguard;
        proguard1.setProguardId(proguardId + 1);
        proguardRepository.saveAndFlush(proguard1);

        Proguard proguard2 = proguard;
        proguard2.setProguardId(proguardId + 2);
        proguardRepository.saveAndFlush(proguard2);

        Proguard proguard3 = proguard;
        proguard3.setProguardId(proguardId + 3);
        proguardRepository.saveAndFlush(proguard3);

        Proguard proguard4 = proguard;
        proguard4.setProguardId(proguardId + 3);
        proguardRepository.saveAndFlush(proguard4);

        Pageable pageable = PageRequest.of(0, 3);
        Page<Proguard> proguardsPageable = proguardService.searchAllByNaiveSQL("select * from proguard order by proguard_id desc", pageable);
        System.out.println(proguardsPageable);

        Pageable pageable2 = PageRequest.of(1, 3);
        Page<Proguard> proguardsPageable2 = proguardService.searchAllByNaiveSQL("select * from proguard order by proguard_id desc", pageable2);
        System.out.println(proguardsPageable2);

        Pageable pageable3 = PageRequest.of(1, 3);
        Page<Map> proguardsPageable3 = proguardService.searchAllByNaiveSQLMap("select proguard_id, proguard_name from proguard order by proguard_id desc", pageable3);
        System.out.println(proguardsPageable3);

        String updateSQL = "update proguard set proguard_name = ? where proguard_id = ?";
        List<Object> params1 = Arrays.asList("alexgaoyh", proguardId);
        // alexgaoyh2 -> null 的时候，验证事务
        List<Object> params2 = Arrays.asList("alexgaoyh2", proguardId);
        List<List<Object>> paramsList = new ArrayList<>();
        paramsList.add(params1);
        paramsList.add(params2);
        List<String> naiveSQLList = Arrays.asList(updateSQL, updateSQL);
        Boolean b = proguardService.executeNaiveSQLBatch(naiveSQLList, paramsList);
        System.out.println(b);
    }

    /**
     *
     */
    @Test
    public void crudTest() {
        Long proguardId = System.currentTimeMillis();

        Proguard proguard = new Proguard();
        proguard.setProguardId(proguardId);
        proguard.setProguardName(proguardId + "");
        Map<String, Object> extMap = new HashMap<>();
        extMap.put("timeswap", System.currentTimeMillis());
        proguard.setExtMap(extMap);
        List<String> extList = new ArrayList<>();
        extList.add("A");
        proguard.setExtList(extList);
        proguardRepository.saveAndFlush(proguard);

        proguard.setProguardName("update");
        proguardRepository.saveAndFlush(proguard);

        proguardRepository.delete(proguard);


    }

    @Test
    public void searchUtilTest() {
        Map<String, Object> extMap = new HashMap<>();
        extMap.put("timeswap", System.currentTimeMillis());
        List<String> extList = new ArrayList<>();
        extList.add("A");

        SearchConditionDTO idEqual = new SearchConditionDTO("proguardId", SearchConditionDTO.Operator.EQUAL, 1l);
        SearchConditionDTO greaterEqual = new SearchConditionDTO("proguardId", SearchConditionDTO.Operator.GREATER_THAN, 1l);
        SearchConditionDTO nameLike = new SearchConditionDTO("proguardName", SearchConditionDTO.Operator.LIKE, "gao");

        Proguard proguard1 = new Proguard();
        proguard1.setProguardId(1l);
        proguard1.setProguardName("alexgaoyh");
        proguard1.setExtMap(extMap);
        proguard1.setExtList(extList);
        proguardRepository.saveAndFlush(proguard1);

        List<SearchConditionDTO> conditions = new ArrayList<>();
        conditions.add(idEqual);

        List<Proguard> proguards1 = SearchUtil.filterEntities(conditions, entityManager, Proguard.class);
        System.out.println(proguards1);

        conditions.clear();
        conditions.add(nameLike);

        List<Proguard> proguards2 = SearchUtil.filterEntities(conditions, entityManager, Proguard.class);
        System.out.println(proguards2);

        conditions.clear();
        conditions.add(idEqual);
        conditions.add(nameLike);

        List<Proguard> proguards3 = SearchUtil.filterEntities(conditions, entityManager, Proguard.class);
        System.out.println(proguards3);

        conditions.clear();
        conditions.add(greaterEqual);

        List<Proguard> proguards4 = SearchUtil.filterEntities(conditions, entityManager, Proguard.class);
        System.out.println(proguards4);
    }


}
