package cn.net.pap.drools.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.util.Date;

@Entity
public class DroolsRule {

    /**
     * 规则id
     */
    @Id
    private Long ruleId;

    /**
     * kbase的名字
     */
    private String kieBaseName;

    /**
     * 设置该kbase需要从那个目录下加载文件，这个是一个虚拟的目录，相对于 `src/main/resources`
     * 比如：kiePackageName=rules/rule01 那么当前规则文件写入路径为： kieFileSystem.write("src/main/resources/rules/rule01/1.drl")
     */
    private String kiePackageName;

    /**
     * 规则内容
     */
    private String ruleContent;

    /**
     * 规则创建时间
     */
    private Date createdTime;
    /**
     * 规则更新时间
     */
    private Date updateTime;

    public Long getRuleId() {
        return ruleId;
    }

    public void setRuleId(Long ruleId) {
        this.ruleId = ruleId;
    }

    public String getKieBaseName() {
        return kieBaseName;
    }

    public void setKieBaseName(String kieBaseName) {
        this.kieBaseName = kieBaseName;
    }

    public String getKiePackageName() {
        return kiePackageName;
    }

    public void setKiePackageName(String kiePackageName) {
        this.kiePackageName = kiePackageName;
    }

    public String getRuleContent() {
        return ruleContent;
    }

    public void setRuleContent(String ruleContent) {
        this.ruleContent = ruleContent;
    }

    public Date getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(Date createdTime) {
        this.createdTime = createdTime;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }
}
