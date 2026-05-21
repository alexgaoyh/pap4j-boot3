package cn.net.pap.example.dynamic.form.repository;

import cn.net.pap.example.dynamic.form.entity.DynamicRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DynamicRecordRepository extends JpaRepository<DynamicRecord, Long> {
    List<DynamicRecord> findByFormCode(String formCode);
}
