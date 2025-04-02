package cn.net.pap.example.proguard.repository;

import cn.net.pap.example.proguard.entity.AutoIncrePreKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AutoIncrePreKeyRepository extends JpaRepository<AutoIncrePreKey, Long>, JpaSpecificationExecutor<AutoIncrePreKey> {

}
