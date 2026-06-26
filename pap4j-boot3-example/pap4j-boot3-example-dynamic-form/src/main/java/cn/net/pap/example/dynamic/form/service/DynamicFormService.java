package cn.net.pap.example.dynamic.form.service;

import cn.net.pap.example.dynamic.form.dto.DynamicFormDTO;
import cn.net.pap.example.dynamic.form.entity.DynamicForm;
import cn.net.pap.example.dynamic.form.repository.DynamicFormRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <p>动态表单元数据服务 (Dynamic Form Metadata Service)</p>
 * <p>负责管理表单的结构定义，包括保存 JSON Schema 和查询定义信息。</p>
 *
 */
@Service
@RequiredArgsConstructor
public class DynamicFormService {
    private final DynamicFormRepository formRepository;

    /**
     * 保存或更新表单定义
     *
     * @param dto 表单定义传输对象
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveForm(DynamicFormDTO dto) {
        DynamicForm form = formRepository.findByFormCode(dto.getFormCode())
                .orElse(new DynamicForm());
        form.setFormCode(dto.getFormCode());
        form.setFormName(dto.getFormName());
        form.setSchemaDefinition(dto.getSchemaDefinition());
        formRepository.save(form);
    }

    /**
     * 根据编码获取表单定义
     *
     * @param formCode 表单唯一编码
     * @return 表单实体
     */
    @Transactional(readOnly = true)
    public DynamicForm getForm(String formCode) {
        return formRepository.findByFormCode(formCode)
                .orElseThrow(() -> new RuntimeException("Form definition not found: " + formCode));
    }
}
