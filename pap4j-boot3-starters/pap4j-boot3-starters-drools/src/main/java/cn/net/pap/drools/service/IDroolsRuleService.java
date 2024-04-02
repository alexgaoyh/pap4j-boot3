package cn.net.pap.drools.service;

import cn.net.pap.drools.entity.DroolsRule;

import java.util.List;

public interface IDroolsRuleService {

    public DroolsRule findById(Long droolsRuleId);

    public List<DroolsRule> findAll();

    public DroolsRule save(DroolsRule DroolsRule);

    public DroolsRule edit(DroolsRule DroolsRule);

    public boolean deleteById(Long droolsRuleId);

    public String success(Long droolsRuleId);

}
