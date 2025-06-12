package cn.net.pap.example.proguard.repository;

import cn.net.pap.example.proguard.entity.NumberSegment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface NumberSegmentRepository extends JpaRepository<NumberSegment, String> {

    Optional<NumberSegment> findByName(String name);

    /**
     * 批量更新号段的currentValue
     */
    @Modifying
    @Transactional
    @Query("UPDATE NumberSegment ns SET ns.currentValue = ns.currentValue + :increment WHERE ns.name = :name")
    void updateCurrentValue(@Param("name") String name, @Param("increment") int increment);

}
