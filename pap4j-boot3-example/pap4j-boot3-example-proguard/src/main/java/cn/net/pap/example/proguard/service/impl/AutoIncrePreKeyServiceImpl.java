package cn.net.pap.example.proguard.service.impl;

import cn.net.pap.example.proguard.entity.AutoIncrePreKey;
import cn.net.pap.example.proguard.repository.AutoIncrePreKeyRepository;
import cn.net.pap.example.proguard.service.IAutoIncrePreKeyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AutoIncrePreKeyServiceImpl implements IAutoIncrePreKeyService {

    @Autowired
    private AutoIncrePreKeyRepository autoIncrePreKeyRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Override
    public AutoIncrePreKey saveAndFlush(AutoIncrePreKey entity) {
        return autoIncrePreKeyRepository.saveAndFlush(entity);
    }

    @Override
    @Transactional
    public AutoIncrePreKey saveAndFlushThrowRuntimeException(AutoIncrePreKey entity) throws RuntimeException {
        try {
            AutoIncrePreKey autoIncrePreKey = autoIncrePreKeyRepository.saveAndFlush(entity);
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionSynchronizationManager.registerSynchronization(
                        new TransactionSynchronization() {
                            @Override
                            public void beforeCommit(boolean readOnly) {
                                System.out.println("beforeCommit");
                            }
                            @Override
                            public void afterCommit() {
                                System.out.println("afterCommit");
                            }
                            @Override
                            public void afterCompletion(int status) {
                                // value in org.springframework.transaction.support.TransactionSynchronization
                                System.out.println("afterCompletion : " + status);
                            }
                        }
                );
            }
            throw new RuntimeException("ASDF");
        } catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 非受检异常（RuntimeException, Error）→ 回滚
     * 受检异常（Checked Exception）→ 不回滚    本例的 IOException
     * @param entity
     * @return
     * @throws IOException
     */
    @Override
    @Transactional
    public AutoIncrePreKey saveAndFlushThrowIOException(AutoIncrePreKey entity) throws IOException {
        try {
            AutoIncrePreKey autoIncrePreKey = autoIncrePreKeyRepository.saveAndFlush(entity);
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionSynchronizationManager.registerSynchronization(
                        new TransactionSynchronization() {
                            @Override
                            public void beforeCommit(boolean readOnly) {
                                System.out.println("beforeCommit");
                            }
                            @Override
                            public void afterCommit() {
                                System.out.println("afterCommit");
                            }
                            @Override
                            public void afterCompletion(int status) {
                                // value in org.springframework.transaction.support.TransactionSynchronization
                                System.out.println("afterCompletion : " + status);
                            }
                        }
                );
            }
            throw new IOException("ASDF");
        } catch (IOException e) {
            throw new IOException(e);
        }
    }

    @Override
    public List<AutoIncrePreKey> findAll() {
        return autoIncrePreKeyRepository.findAll();
    }

    @Override
    public Map<String, List<AutoIncrePreKey>> batch(List<AutoIncrePreKey> autoIncrePreKeyList) {

        Map<String, List<AutoIncrePreKey>> returnMap = new HashMap<>();

        List<AutoIncrePreKey> success = new ArrayList<>();
        List<AutoIncrePreKey> failed = new ArrayList<>();

        for (AutoIncrePreKey autoIncrePreKey : autoIncrePreKeyList) {
            DefaultTransactionDefinition def = new DefaultTransactionDefinition();
            // 明确指定：始终新事务
            def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            // 明确隔离级别
            def.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
            // 设置合理超时
            def.setTimeout(30);  // 30秒

            TransactionStatus status = null;
            try {
                status = transactionManager.getTransaction(def);

                autoIncrePreKeyRepository.save(autoIncrePreKey);

                transactionManager.commit(status);
                success.add(autoIncrePreKey);

            } catch (Exception e) {
                if (status != null && !status.isCompleted()) {
                    transactionManager.rollback(status);
                }
                failed.add(autoIncrePreKey);
            } finally {

            }
        }

        returnMap.put("success", success);
        returnMap.put("failed", failed);
        return returnMap;
    }

}
