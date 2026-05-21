package cn.net.pap.example.dynamic.form.repository;

import cn.net.pap.example.dynamic.form.entity.DynamicRelation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DynamicRelationRepository extends JpaRepository<DynamicRelation, Long> {
}
