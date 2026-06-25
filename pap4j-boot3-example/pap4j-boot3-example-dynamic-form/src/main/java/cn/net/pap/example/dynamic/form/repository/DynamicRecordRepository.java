package cn.net.pap.example.dynamic.form.repository;

import cn.net.pap.example.dynamic.form.entity.DynamicRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DynamicRecordRepository extends JpaRepository<DynamicRecord, Long> {
    Page<DynamicRecord> findByFormCode(String formCode, Pageable pageable);
}
