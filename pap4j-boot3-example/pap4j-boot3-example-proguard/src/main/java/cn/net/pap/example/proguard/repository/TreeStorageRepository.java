package cn.net.pap.example.proguard.repository;

import cn.net.pap.example.proguard.entity.TreeStorage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * TreeStorage 仓库接口
 */
@Repository
public interface TreeStorageRepository extends JpaRepository<TreeStorage, Long> {

    /**
     * 原子自增更新业务键 (Sequence / PV 计数)
     *
     * @param id 页面 ID
     * @param increment 增量
     * @return 受影响的行数
     */
    @Modifying
    @Query(value = "UPDATE tree_storage SET sequence = sequence + :increment WHERE id = :id", nativeQuery = true)
    int incrementSequence(@Param("id") Long id, @Param("increment") int increment);

}
