package cn.net.pap.example.proguard.controller;

import cn.net.pap.example.proguard.entity.AutoIncrePreKey;
import cn.net.pap.example.proguard.service.IAutoIncrePreKeyService;
import cn.net.pap.example.proguard.service.IDeadlockRetryDemoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/retry")
public class DeadLockRetryDemoController {

    @Autowired
    private IDeadlockRetryDemoService deadlockRetryDemoService;

    @Autowired
    private IAutoIncrePreKeyService autoIncrePreKeyService;

    @GetMapping("/insert")
    public String insert() throws InterruptedException {
        AutoIncrePreKey autoIncrePreKey1 = new AutoIncrePreKey();
        autoIncrePreKey1.setName("autoIncrePreKey1");
        autoIncrePreKeyService.saveAndFlush(autoIncrePreKey1);

        AutoIncrePreKey autoIncrePreKey2 = new AutoIncrePreKey();
        autoIncrePreKey2.setName("autoIncrePreKey2");
        autoIncrePreKeyService.saveAndFlush(autoIncrePreKey2);

        return "done";
    }

    /**
     * h2 的数据库，可能不能重现到这个死锁，但是写法思路是相同的
     * 可以将 updateTwoRowsOrderly 这个函数内部的某一个sql的表名改为不存在的表名，然后是会出发异常到 recover 的。
     * @return
     * @throws InterruptedException
     */
    @GetMapping("/test")
    public String test() throws InterruptedException {
        Thread t1 = new Thread(() -> deadlockRetryDemoService.updateTwoRowsOrderly(1L, 2L));
        Thread t2 = new Thread(() -> deadlockRetryDemoService.updateTwoRowsOrderly(2L, 1L));

        t1.start();
        t2.start();

        t1.join();
        t2.join();

        return "done";
    }

}
