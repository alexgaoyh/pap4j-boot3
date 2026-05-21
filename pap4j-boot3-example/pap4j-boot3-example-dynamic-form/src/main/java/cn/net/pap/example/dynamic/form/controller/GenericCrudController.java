package cn.net.pap.example.dynamic.form.controller;

import cn.net.pap.example.dynamic.form.service.DynamicRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * <p>通用 CRUD 控制器 (Generic CRUD Controller)</p>
 * <p>基于 EAV 模型提供通用的数据操作 API，支持任意结构的表单数据存储与查询。</p>
 *
 * <p><b>API 路径规则:</b> /api/generic/{formCode}/...</p>
 *
 */
@RestController
@RequestMapping("/api/generic/{formCode}")
@RequiredArgsConstructor
@Tag(name = "通用动态数据接口", description = "基于 EAV 模型实现的通用增删改查接口")
public class GenericCrudController {

    private final DynamicRecordService recordService;

    /**
     * 保存动态记录
     *
     * @param formCode 表单编码
     * @param payload  业务数据 Map
     * @return 记录主键 ID
     */
    @PostMapping("/save")
    @Operation(summary = "保存/提交动态数据", description = "支持递归解析嵌套的 JSON 对象和数组")
    public Long save(
            @Parameter(description = "表单编码") @PathVariable String formCode,
            @RequestBody Map<String, Object> payload) {
        return recordService.saveComplexRecord(formCode, payload);
    }

    /**
     * 查询记录列表
     *
     * @param formCode 表单编码
     * @return 数据 Map 列表
     */
    @GetMapping("/list")
    @Operation(summary = "查询指定类型的记录列表")
    public List<Map<String, Object>> list(
            @Parameter(description = "表单编码") @PathVariable String formCode) {
        return recordService.listRecords(formCode);
    }

    /**
     * 获取单条记录详情
     *
     * @param id 记录 ID
     * @return 完整嵌套结构的 Map
     */
    @GetMapping("/{id}")
    @Operation(summary = "根据 ID 获取完整记录详情")
    public Map<String, Object> get(
            @Parameter(description = "记录 ID") @PathVariable Long id) {
        return recordService.getRecord(id);
    }

    /**
     * 删除动态记录
     *
     * @param id 记录 ID
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除动态记录", description = "删除主记录的同时会级联删除相关的属性值和子级关系记录")
    public void delete(
            @Parameter(description = "记录 ID") @PathVariable Long id) {
        recordService.deleteRecord(id);
    }
}
