package cn.net.pap.example.dynamic.form.repository;

import cn.net.pap.example.dynamic.form.entity.DynamicForm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DynamicFormRepository extends JpaRepository<DynamicForm, Long> {
    Optional<DynamicForm> findByFormCode(String formCode);
}
