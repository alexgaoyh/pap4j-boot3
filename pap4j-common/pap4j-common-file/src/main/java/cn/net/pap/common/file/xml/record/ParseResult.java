package cn.net.pap.common.file.xml.record;

import java.util.List;

/**
 * XML 解析的返回结果封装
 *
 * @param content 转换后的 HTML/XML 字符串
 * @param refList 统计结果：包含类型和类型值的自定义对象列表
 */
public record ParseResult(String content, List<RefInfo> refList) {
}
