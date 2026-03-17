package cn.net.pap.common.datastructure.fst;

import java.io.Serializable;

/**
 * <p><strong>ValueLocationDTO</strong> 表示分词处理的结果。</p>
 *
 * <p>它保存了匹配的文本及其在原始字符串中的起始和结束位置。</p>
 * 
 * <ul>
 *     <li><strong>text:</strong> 匹配的字符串。</li>
 *     <li><strong>start:</strong> 匹配的起始索引（包含）。</li>
 *     <li><strong>end:</strong> 匹配的结束索引（不包含）。</li>
 * </ul>
 */
public class ValueLocationDTO implements Serializable {
    /**
     * <p>匹配的文本内容。</p>
     */
    private String text;

    /**
     * <p>匹配文本的起始位置。</p>
     */
    private Integer start;

    /**
     * <p>匹配文本的结束位置。</p>
     */
    private Integer end;

    /**
     * <p>默认构造函数。</p>
     */
    public ValueLocationDTO() {
    }

    /**
     * <p>构造一个具有指定文本和位置的全新 <strong>ValueLocationDTO</strong>。</p>
     *
     * @param text  匹配的字符串。
     * @param start 起始索引。
     * @param end   结束索引。
     */
    public ValueLocationDTO(String text, Integer start, Integer end) {
        this.text = text;
        this.start = start;
        this.end = end;
    }

    /**
     * <p>获取匹配的文本。</p>
     *
     * @return 文本字符串。
     */
    public String getText() {
        return text;
    }

    /**
     * <p>设置匹配的文本。</p>
     *
     * @param text 要设置的文本字符串。
     */
    public void setText(String text) {
        this.text = text;
    }

    /**
     * <p>获取匹配项的起始索引。</p>
     *
     * @return 起始索引。
     */
    public Integer getStart() {
        return start;
    }

    /**
     * <p>设置匹配项的起始索引。</p>
     *
     * @param start 要设置的起始索引。
     */
    public void setStart(Integer start) {
        this.start = start;
    }

    /**
     * <p>获取匹配项的结束索引。</p>
     *
     * @return 结束索引。
     */
    public Integer getEnd() {
        return end;
    }

    /**
     * <p>设置匹配项的结束索引。</p>
     *
     * @param end 要设置的结束索引。
     */
    public void setEnd(Integer end) {
        this.end = end;
    }

    /**
     * <p>返回该对象的字符串表示形式。</p>
     *
     * @return 包含文本、起始和结束位置的字符串。
     */
    @Override
    public String toString() {
        return "ValueLocationDTO{" +
                "text='" + text + '\'' +
                ", start=" + start +
                ", end=" + end +
                '}';
    }
}
