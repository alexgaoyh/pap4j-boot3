package cn.net.pap.common.file.xml.record;

/**
 * 提取出的引用信息实体
 *
 * @param type  标签类型 (tagName)
 * @param value 类型值 (refid)
 * @param nodeValue 节点内部的值
 */
public record RefInfo(String type, String value, String nodeValue) {
}