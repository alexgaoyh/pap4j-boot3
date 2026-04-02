package cn.net.pap.example.proguard.controller;

import cn.net.pap.example.proguard.entity.AutoIncrePreKey;
import cn.net.pap.example.proguard.service.IAutoIncrePreKeyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/autoIncrePreKey")
public class AutoIncrePreKeyController {

    private final IAutoIncrePreKeyService autoIncrePreKeyService;

    public AutoIncrePreKeyController(IAutoIncrePreKeyService autoIncrePreKeyService) {
        this.autoIncrePreKeyService = autoIncrePreKeyService;
    }

    @GetMapping("/saveAndFlush")
    public String saveAndFlush() throws Exception {
        AutoIncrePreKey autoIncrePreKey = new AutoIncrePreKey();
        autoIncrePreKey.setName("runtimeException");
        autoIncrePreKeyService.saveAndFlush(autoIncrePreKey);
        return "success";
    }

    @GetMapping("/saveAndFlushBatch")
    public String saveAndFlushBatch() throws Exception {
        List<AutoIncrePreKey> autoIncrePreKeyList = new ArrayList<>();
        for(int i = 45; i < 55; i++) {
            AutoIncrePreKey autoIncrePreKey = new AutoIncrePreKey();
            autoIncrePreKey.setName( "x".repeat(i+1));
            autoIncrePreKeyList.add(autoIncrePreKey);
        }
        autoIncrePreKeyService.saveAndFlushBatch(autoIncrePreKeyList);
        return "success";
    }

    @GetMapping("/runtimeException")
    public String runtimeException() throws Exception {
        AutoIncrePreKey autoIncrePreKey = new AutoIncrePreKey();
        autoIncrePreKey.setName("runtimeException");
        autoIncrePreKeyService.saveAndFlushThrowRuntimeException(autoIncrePreKey);
        return "success";
    }

    @GetMapping("/ioException")
    public String ioException() throws Exception {
        AutoIncrePreKey autoIncrePreKey = new AutoIncrePreKey();
        autoIncrePreKey.setName("ioException");
        autoIncrePreKeyService.saveAndFlushThrowIOException(autoIncrePreKey);
        return "success";
    }

    @GetMapping("/batch")
    @ResponseBody
    public Map<String, List<AutoIncrePreKey>> batch()  {
        List<AutoIncrePreKey> autoIncrePreKeyList = new ArrayList<>();
        for(int i = 0; i < 100; i++) {
            AutoIncrePreKey autoIncrePreKey = new AutoIncrePreKey();
            autoIncrePreKey.setName( "x".repeat(i+1));
            autoIncrePreKeyList.add(autoIncrePreKey);
        }
        Map<String, List<AutoIncrePreKey>> batch = autoIncrePreKeyService.batch(autoIncrePreKeyList);
        return batch;
    }

}
