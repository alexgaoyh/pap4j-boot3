package cn.net.pap.common.spider.jsoup.dto;

import java.io.Serializable;

public class SpiderDTO implements Serializable {

    private String id;

    private String name;

    private String sign;

    private Integer pageNumber;

    public SpiderDTO() {
    }

    public SpiderDTO(String id, String name, String sign) {
        this.id = id;
        this.name = name;
        this.sign = sign;
    }

    public SpiderDTO(String id, String name, String sign, Integer pageNumber) {
        this.id = id;
        this.name = name;
        this.sign = sign;
        this.pageNumber = pageNumber;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSign() {
        return sign;
    }

    public void setSign(String sign) {
        this.sign = sign;
    }

    public Integer getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(Integer pageNumber) {
        this.pageNumber = pageNumber;
    }

}
