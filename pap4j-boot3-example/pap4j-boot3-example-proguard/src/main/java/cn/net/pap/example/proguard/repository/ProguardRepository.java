package cn.net.pap.example.proguard.repository;

import cn.net.pap.example.proguard.entity.Proguard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface ProguardRepository extends JpaRepository<Proguard,Long>, JpaSpecificationExecutor<Proguard> {

    List<Proguard> searchAllByProguardName(@Param("proguardName") String proguardName);

    Proguard getProguardByProguardId(@Param("proguardId") Long proguardId);

    @Query("SELECT o FROM Proguard o WHERE o.proguardId IN :proguardIds")
    List<Proguard> getProguardByProguardIds(@Param("proguardIds") List<Long> proguardIds);

    /**
     * 查询指定字段
     * @param proguardId
     * @param type 形如 ProguardDTO.java 这个 interface
     * @return
     */
    <T> Optional<T> getProguardByProguardId(@Param("proguardId") Long proguardId, Class<T> type);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Proguard a WHERE a.proguardId = :proguardId")
    Proguard getProguardByProguardIdForUpdate(@Param("proguardId") Long proguardId);

}
