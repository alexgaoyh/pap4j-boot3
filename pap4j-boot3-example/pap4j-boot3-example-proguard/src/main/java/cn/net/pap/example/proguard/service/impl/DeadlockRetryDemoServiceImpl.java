package cn.net.pap.example.proguard.service.impl;

import cn.net.pap.example.proguard.service.IDeadlockRetryDemoService;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeadlockRetryDemoServiceImpl implements IDeadlockRetryDemoService {

    private final JdbcTemplate jdbcTemplate;

    public DeadlockRetryDemoServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Retryable(
            value = {
                    DeadlockLoserDataAccessException.class,
                    CannotAcquireLockException.class
            },
            maxAttempts = 3,
            backoff = @Backoff(delay = 200, multiplier = 2)
    )
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateTwoRowsOrderly(Long id1, Long id2) {
        // 先锁第一行
        jdbcTemplate.queryForObject(
                "select id from auto_incre_pre_key where id = ? for update",
                Long.class,
                id1
        );

        // 故意 sleep，拉大死锁窗口
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        // 再锁第二行
        jdbcTemplate.queryForObject(
                "select id from auto_incre_pre_key where id = ? for update",
                Long.class,
                id2
        );

        // 更新
        jdbcTemplate.update(
                "update auto_incre_pre_key set name = 'alexgaoyh' where id in (?, ?)",
                id1, id2
        );
    }

    @Recover
    public void recover(Exception e, Long id1, Long id2) {
        System.out.println(String.format("!!! 重试耗尽，恢复逻辑触发，参数：%s -> %s，异常： %s", id1, id2, e));
        // todo maybe throw exception
    }

}
