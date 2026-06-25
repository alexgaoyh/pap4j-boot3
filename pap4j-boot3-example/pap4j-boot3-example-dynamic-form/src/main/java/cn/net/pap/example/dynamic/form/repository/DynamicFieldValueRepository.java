package cn.net.pap.example.dynamic.form.repository;

import cn.net.pap.example.dynamic.form.entity.DynamicFieldValue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DynamicFieldValueRepository extends JpaRepository<DynamicFieldValue, Long> {

    List<DynamicFieldValue> findByRecordId(Long recordId);

    void deleteByRecordId(Long recordId);

}
