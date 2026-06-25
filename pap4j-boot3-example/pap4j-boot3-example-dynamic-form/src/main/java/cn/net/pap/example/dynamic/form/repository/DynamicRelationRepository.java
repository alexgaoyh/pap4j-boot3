package cn.net.pap.example.dynamic.form.repository;

import cn.net.pap.example.dynamic.form.entity.DynamicRelation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DynamicRelationRepository extends JpaRepository<DynamicRelation, Long> {

    List<DynamicRelation> findBySourceRecordId(Long sourceRecordId);

    void deleteBySourceRecordId(Long sourceRecordId);

}
