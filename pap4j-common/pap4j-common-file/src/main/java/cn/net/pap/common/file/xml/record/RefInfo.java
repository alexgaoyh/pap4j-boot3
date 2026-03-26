package cn.net.pap.common.file.xml.record;

/**
 * 提取出的引用信息实体
 *
 * @param type  标签类型 (tagName)
 * @param value 类型值 (refid)
 */
public record RefInfo(String type, String value) {
}