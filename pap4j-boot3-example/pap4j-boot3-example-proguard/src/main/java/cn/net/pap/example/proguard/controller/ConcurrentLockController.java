package cn.net.pap.example.proguard.controller;

import cn.net.pap.example.proguard.aspect.annotation.ConcurrentLock;
import cn.net.pap.example.proguard.entity.Proguard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/concurrentLock")
@Tag(name = "并发锁测试接口", description = "演示通过自定义切面 @ConcurrentLock 实现的分布式锁/并发防抖测试接口")
public class ConcurrentLockController {

    @Operation(summary = "按路径参数加锁")
    @PostMapping(value = "/test1/{proguardName}")
    @ConcurrentLock("#proguardName")
    public ResponseEntity<String> test1(@PathVariable String proguardName, @RequestBody Proguard update) {
        return ResponseEntity.ok("成功");
    }

    @Operation(summary = "按请求体组合参数加锁")
    @PostMapping("/test2")
    @ConcurrentLock(value = "#request.proguardName + '_' + #request.proguardIdx", waitTime = 5, releaseTime = 30, message = "正在调整中，请等待完成后再试")
    public ResponseEntity<String> test2(@RequestBody Proguard request) {
        return ResponseEntity.ok("成功");
    }

    @Operation(summary = "系统静态锁")
    @PostMapping("/test3")
    @ConcurrentLock(value = "SYSTEM_REFRESH_LOCK", waitTime = 10, releaseTime = 60)
    public ResponseEntity<String> test3() {
        return ResponseEntity.ok("成功");
    }

}
