package cn.net.pap.example.spring.ai.tool;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

/**
 * 业务领域同义词/专有名词服务（极简版）。
 */
@Component
public class DomainGlossaryService {

    /**
     * 极简业务术语映射：口语/别名 -> 标准专有名词
     */
    private static final Map<String, String> GLOSSARY = Map.of(
            "折算", "除法"
    );

    /**
     * 获取关键词对应的扩展标准专有名词，若无映射则返回原词。
     *
     * @param keyword 原始检索词
     * @return 映射后的标准专有名词或原词
     */
    public String expand(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return keyword;
        }
        return GLOSSARY.getOrDefault(keyword.trim().toLowerCase(Locale.ROOT), keyword);
    }
}
