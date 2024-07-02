package cn.net.pap.example.proguard.controller;

import cn.net.pap.example.proguard.entity.Proguard;
import cn.net.pap.example.proguard.service.IProguardService;
import org.springframework.beans.factory.annotation.Autowired;
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

        return proguardService.saveAndFlush(proguard);
    }

    @GetMapping("/searchAllByProguardName")
    public List<Proguard> searchAllByProguardName(@RequestParam(name = "proguardName") String proguardName) {
        return proguardService.searchAllByProguardName(proguardName);
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

}
