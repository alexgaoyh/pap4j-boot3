package cn.net.pap.example.proguard.repository;

import cn.net.pap.example.proguard.entity.Proguard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProguardRepository extends JpaRepository<Proguard,Long>, JpaSpecificationExecutor<Proguard> {

    List<Proguard> searchAllByProguardName(@Param("proguardName") String proguardName);

}
