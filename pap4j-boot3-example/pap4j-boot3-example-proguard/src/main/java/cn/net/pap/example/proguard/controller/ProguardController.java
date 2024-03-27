package cn.net.pap.example.proguard.controller;

import cn.net.pap.example.proguard.entity.Proguard;
import cn.net.pap.example.proguard.service.IProguardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ProguardController {

    @Autowired
    private IProguardService proguardService;

    @GetMapping("/saveAndFlush")
    public Proguard saveAndFlush() {
        Proguard proguard = new Proguard();
        proguard.setProguardId(System.currentTimeMillis());
        proguard.setProguardName(proguard.getProguardId() + "");
        return proguardService.saveAndFlush(proguard);
    }

    @GetMapping("/searchAllByProguardName")
    public List<Proguard> searchAllByProguardName(@RequestParam(name = "proguardName") String proguardName) {
        return proguardService.searchAllByProguardName(proguardName);
    }

}
