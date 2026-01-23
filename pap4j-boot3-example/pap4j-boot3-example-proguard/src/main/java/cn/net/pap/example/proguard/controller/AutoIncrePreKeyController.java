package cn.net.pap.example.proguard.controller;

import cn.net.pap.example.proguard.entity.AutoIncrePreKey;
import cn.net.pap.example.proguard.service.IAutoIncrePreKeyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/autoIncrePreKey")
public class AutoIncrePreKeyController {

    @Autowired
    private IAutoIncrePreKeyService autoIncrePreKeyService;

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

}
