package cn.net.pap.example.dynamic.form.repository;

import cn.net.pap.example.dynamic.form.entity.DynamicFieldValue;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DynamicFieldValueRepository extends JpaRepository<DynamicFieldValue, Long> {
}
