package cn.net.pap.example.proguard.controller;

import cn.net.pap.example.proguard.entity.Proguard;
import cn.net.pap.example.proguard.service.IProguardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class ProguardController {

    @Autowired
    private IProguardService proguardService;

    @GetMapping("/saveAndFlush")
    public Proguard saveAndFlush() {
        Proguard proguard = new Proguard();
        proguard.setProguardId(System.currentTimeMillis());
        proguard.setProguardName(proguard.getProguardId() + "");

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

        return proguardService.saveAndFlush(proguard);
    }

    @GetMapping("getProguardByProguardId")
    public Proguard getProguardByProguardId(@RequestParam(name = "proguardId") Long proguardId) {
        return proguardService.getProguardByProguardId(proguardId);
    }

    @GetMapping("updateProguardByProguardId")
    public Proguard updateProguardByProguardId(@RequestParam(name = "proguardId") Long proguardId) {
        Proguard proguardByProguardId = proguardService.getProguardByProguardId(proguardId);
        proguardByProguardId.setProguardName("update : " + System.currentTimeMillis());
        return proguardService.updateProguardByProguardId(proguardByProguardId);
    }

    @GetMapping("/searchAllByProguardName")
    public List<Proguard> searchAllByProguardName(@RequestParam(name = "proguardName") String proguardName) {
        return proguardService.searchAllByProguardName(proguardName);
    }

    @GetMapping("/findAll")
    public List<Proguard> findAll() {
        return proguardService.findAll();
    }

    @GetMapping("/findNaive")
    public Page<Proguard> findNaive() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Proguard> proguardsPageable = proguardService.searchAllByNaiveSQL("select * from proguard order by proguard_id desc", pageable);
        return proguardsPageable;
    }

    @GetMapping("/saveAllAndFlush")
    public List<Proguard> saveAllAndFlush() {
        List<Proguard> proguards = new ArrayList<>();
        for(int idx = 0; idx < 100; idx++) {
            Proguard proguard = new Proguard();
            proguard.setProguardId(System.currentTimeMillis());
            proguard.setProguardName(proguard.getProguardId() + "");
            proguards.add(proguard);
        }
        return proguardService.saveAllAndFlush(proguards);
    }

    @GetMapping("/saveAndUpdate")
    public Proguard saveAndUpdate() {
        Proguard proguard = new Proguard();
        proguard.setProguardId(System.currentTimeMillis());
        proguard.setProguardName(proguard.getProguardId() + "");

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

        proguardService.saveAndFlush(proguard);

        proguard.setProguardName("update");
        proguardService.saveAndFlush(proguard);

        return proguard;
    }

}
