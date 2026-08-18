package cn.net.pap.example.proguard.dto;

import java.math.BigDecimal;

/**
 * 分表存储的固定传输类型（非 @Entity）。
 *
 * <p>与业务表 5 列一一对应：id, data, ext_str_1, ext_str_2, ext_num_1。
 * 只作 Service 进出参数，不依赖任何 Hibernate 托管机制。</p>
 *
 * <p>字段纪律：{@code data} 是权威 JSON，ext 字段是检索副本，展示一律读 {@code data}。</p>
 */
public class SplitRecordDTO {

    /**
     * DB 自增主键，{@code save()} 写入后回填
     */
    private Long id;

    /**
     * 权威业务数据 JSON 字符串
     */
    private String data;

    /**
     * ext_str_1 检索副本
     */
    private String extStr1;

    /**
     * ext_str_2 检索副本
     */
    private String extStr2;

    /**
     * 数值检索副本（ext_num_1，DECIMAL(20,6)）
     */
    private BigDecimal extNum1;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getExtStr1() {
        return extStr1;
    }

    public void setExtStr1(String extStr1) {
        this.extStr1 = extStr1;
    }

    public String getExtStr2() {
        return extStr2;
    }

    public void setExtStr2(String extStr2) {
        this.extStr2 = extStr2;
    }

    public BigDecimal getExtNum1() {
        return extNum1;
    }

    public void setExtNum1(BigDecimal extNum1) {
        this.extNum1 = extNum1;
    }
}
