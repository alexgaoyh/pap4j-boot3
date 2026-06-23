package cn.net.pap.example.proguard.controller;

import cn.net.pap.example.proguard.entity.AutoIncrePreKey;
import cn.net.pap.example.proguard.service.IAutoIncrePreKeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/autoIncrePreKey")
@Tag(name = "自增主键演示接口", description = "提供自增主键保存、批量保存、异常抛出事务回滚测试等接口")
public class AutoIncrePreKeyController {

    private final IAutoIncrePreKeyService autoIncrePreKeyService;

    public AutoIncrePreKeyController(IAutoIncrePreKeyService autoIncrePreKeyService) {
        this.autoIncrePreKeyService = autoIncrePreKeyService;
    }

    @Operation(summary = "保存并刷新单条记录")
    @GetMapping("/saveAndFlush")
    public String saveAndFlush() throws Exception {
        AutoIncrePreKey autoIncrePreKey = new AutoIncrePreKey();
        autoIncrePreKey.setName("runtimeException");
        autoIncrePreKeyService.saveAndFlush(autoIncrePreKey);
        return "success";
    }

    @Operation(summary = "批量保存并刷新记录")
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

    @Operation(summary = "触发运行时异常测试事务回滚")
    @GetMapping("/runtimeException")
    public String runtimeException() throws Exception {
        AutoIncrePreKey autoIncrePreKey = new AutoIncrePreKey();
        autoIncrePreKey.setName("runtimeException");
        autoIncrePreKeyService.saveAndFlushThrowRuntimeException(autoIncrePreKey);
        return "success";
    }

    @Operation(summary = "触发检测到IO异常测试事务回滚")
    @GetMapping("/ioException")
    public String ioException() throws Exception {
        AutoIncrePreKey autoIncrePreKey = new AutoIncrePreKey();
        autoIncrePreKey.setName("ioException");
        autoIncrePreKeyService.saveAndFlushThrowIOException(autoIncrePreKey);
        return "success";
    }

    @Operation(summary = "批量分批次插入测试")
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
