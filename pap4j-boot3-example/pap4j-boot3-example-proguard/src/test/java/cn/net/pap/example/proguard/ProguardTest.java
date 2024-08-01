package cn.net.pap.example.proguard;

import cn.net.pap.example.proguard.dto.ProguardDTO;
import cn.net.pap.example.proguard.entity.Proguard;
import cn.net.pap.example.proguard.repository.ProguardRepository;
import cn.net.pap.example.proguard.service.IProguardService;
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

    }

}
