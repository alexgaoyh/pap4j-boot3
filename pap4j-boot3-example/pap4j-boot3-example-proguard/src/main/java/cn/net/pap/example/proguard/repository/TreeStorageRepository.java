package cn.net.pap.example.proguard.repository;

import cn.net.pap.example.proguard.entity.TreeStorage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * TreeStorage 仓库接口
 */
@Repository
public interface TreeStorageRepository extends JpaRepository<TreeStorage, Long> {
}
