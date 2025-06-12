package cn.net.pap.example.proguard.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "number_segments")
public class NumberSegment {

    @Id
    private String name;

    /**
     * 号段前缀
     */
    private String segmentPrefix;

    /**
     * 当前号段值
     */
    private Integer currentValue;

    public NumberSegment() {
    }

    public NumberSegment(String name, String segmentPrefix, Integer currentValue) {
        this.name = name;
        this.segmentPrefix = segmentPrefix;
        this.currentValue = currentValue;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSegmentPrefix() {
        return segmentPrefix;
    }

    public void setSegmentPrefix(String segmentPrefix) {
        this.segmentPrefix = segmentPrefix;
    }

    public Integer getCurrentValue() {
        return currentValue;
    }

    public void setCurrentValue(Integer currentValue) {
        this.currentValue = currentValue;
    }

}