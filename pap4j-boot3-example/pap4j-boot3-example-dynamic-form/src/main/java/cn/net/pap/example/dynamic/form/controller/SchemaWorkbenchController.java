package cn.net.pap.example.dynamic.form.controller;

import cn.net.pap.common.qlexpress.Express4RunnerUtil;
import cn.net.pap.common.qlexpress.dto.FunctionalExtractionResultDTO;
import cn.net.pap.common.qlexpress.dto.FunctionalExtractionRuleDTO;
import cn.net.pap.example.dynamic.form.dto.ExtractRecordResult;
import cn.net.pap.example.dynamic.form.dto.ExtractRequest;
import cn.net.pap.example.dynamic.form.dto.ExtractResponse;
import cn.net.pap.example.dynamic.form.dto.ExtractRuleDTO;
import cn.net.pap.example.dynamic.form.dto.MockGenerateRequest;
import cn.net.pap.example.dynamic.form.dto.MockGenerateResponse;
import cn.net.pap.example.dynamic.form.util.SchemaMockDataGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * <p>Schema 数据生成与提取验证工作台控制器。</p>
 * <p>面向 schema-editor.html 的"数据生成与提取验证"页签：基于 JSON Schema 生成随机业务数据，
 * 并将用户书写的 JsonPath / QLExpress 提取规则批量跑在生成数据上，返回逐记录逐规则的值与状态。</p>
 */
@RestController
@RequestMapping("/api/schema-workbench")
@Tag(name = "Schema 数据生成与提取工作台", description = "随机生成符合 schema 的数据，并可视化验证 qlexpress 提取表达式")
public class SchemaWorkbenchController {

    private static final int MAX_COUNT = 200;
    private static final int DEFAULT_COUNT = 10;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 根据传入的 JSON Schema 生成 count 条随机业务数据。
     *
     * @param request 生成请求（schemaJson 必填，count 可选默认 10，seed 可选）
     * @return 生成的业务数据列表
     */
    @PostMapping("/generate")
    @Operation(summary = "按 JSON Schema 随机生成业务数据", description = "严格遵循 schema 约束并叠加字段名语义，count 上限 200")
    public MockGenerateResponse generate(@RequestBody MockGenerateRequest request) {
        if (request.schemaJson() == null || request.schemaJson().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "schemaJson 不能为空");
        }
        int count = request.count() == null ? DEFAULT_COUNT : request.count();
        count = Math.min(Math.max(count, 1), MAX_COUNT);
        List<Map<String, Object>> records =
                SchemaMockDataGenerator.generate(request.schemaJson(), count, request.seed());
        return new MockGenerateResponse(records);
    }

    /**
     * 预校验提取规则语法，非法时返回 400。
     *
     * @param rules 规则列表
     * @return 校验通过返回 {@code {ok: true}}
     */
    @PostMapping("/check-rules")
    @Operation(summary = "预校验提取规则语法", description = "JsonPath 走 JsonPath.compile，QLExpress 走 runner.check")
    public Map<String, Boolean> checkRules(@RequestBody List<ExtractRuleDTO> rules) {
        List<FunctionalExtractionRuleDTO> converted = new ArrayList<>(rules.size());
        for (ExtractRuleDTO rule : rules) {
            converted.add(new FunctionalExtractionRuleDTO(rule.targetField(), rule.expression()));
        }
        try {
            Express4RunnerUtil.checkRules(converted);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
        return Collections.singletonMap("ok", Boolean.TRUE);
    }

    /**
     * 将提取规则批量执行在业务数据上。
     *
     * @param request 提取请求（records 与 rules，rules 表达式以 {@code $} 开头走 JsonPath，其余走 QLExpress）
     * @return 逐记录逐规则的值与状态
     */
    @PostMapping("/extract")
    @Operation(summary = "批量执行提取规则", description = "逐条记录执行 JsonPath/QLExpress 提取，失败规则不产值并携带 errorMsg")
    public ExtractResponse extract(@RequestBody ExtractRequest request) {
        if (request.records() == null || request.rules() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "records 与 rules 不能为空");
        }
        List<FunctionalExtractionRuleDTO> rules = new ArrayList<>(request.rules().size());
        for (ExtractRuleDTO rule : request.rules()) {
            rules.add(new FunctionalExtractionRuleDTO(rule.targetField(), rule.expression()));
        }
        // 不做整体预校验：Express4RunnerUtil.extract 自带逐规则异常隔离，
        // 坏规则会进入 statuses(success=false + errorMsg)，由前端逐行标红展示。

        List<ExtractRecordResult> results = new ArrayList<>(request.records().size());
        for (int i = 0; i < request.records().size(); i++) {
            String jsonData = toJson(request.records().get(i));
            FunctionalExtractionResultDTO extractResult = Express4RunnerUtil.extract(jsonData, rules);
            results.add(new ExtractRecordResult(i, extractResult.extractedFields(), extractResult.statuses()));
        }
        return new ExtractResponse(results);
    }

    private static String toJson(Map<String, Object> record) {
        try {
            return MAPPER.writeValueAsString(record);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "记录序列化失败: " + e.getMessage(), e);
        }
    }
}
