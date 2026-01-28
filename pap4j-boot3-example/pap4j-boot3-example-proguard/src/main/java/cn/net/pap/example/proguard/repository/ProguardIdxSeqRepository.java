package cn.net.pap.example.proguard.repository;

import cn.net.pap.example.proguard.entity.ProguardIdxSeq;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface ProguardIdxSeqRepository extends JpaRepository<ProguardIdxSeq, String>, JpaSpecificationExecutor<ProguardIdxSeq> {

    /**
     * 当一个事务读取某行数据时，加锁使其他事务 不能修改或获取写锁，必须等待锁释放。 避免多个事务同时读取并更新同一行数据导致的 数据冲突 或 重复自增。
     * 它就是让 某行数据在当前事务中被“独占”，别人想改必须等你做完，保证 seq 不会重复。
     * 使用场景： 分组自增 seq    库存扣减    并发更新计数器
     *
     * @param proguardName
     * @return
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ProguardIdxSeq> findByProguardName(String proguardName);

}
