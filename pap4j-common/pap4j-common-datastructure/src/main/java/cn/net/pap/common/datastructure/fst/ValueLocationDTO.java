package cn.net.pap.common.datastructure.fst;

import java.io.Serializable;

/**
 * 分词结果对象
 */
public class ValueLocationDTO implements Serializable {
    private String text;

    private Integer start;

    private Integer end;

    public ValueLocationDTO() {
    }

    public ValueLocationDTO(String text, Integer start, Integer end) {
        this.text = text;
        this.start = start;
        this.end = end;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Integer getStart() {
        return start;
    }

    public void setStart(Integer start) {
        this.start = start;
    }

    public Integer getEnd() {
        return end;
    }

    public void setEnd(Integer end) {
        this.end = end;
    }

    @Override
    public String toString() {
        return "ValueLocationDTO{" +
                "text='" + text + '\'' +
                ", start=" + start +
                ", end=" + end +
                '}';
    }
}
