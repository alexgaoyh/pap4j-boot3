package cn.net.pap.drools.service.impl;

import cn.net.pap.drools.entity.DroolsRule;
import cn.net.pap.drools.repository.DroolsRuleRepository;
import cn.net.pap.drools.service.IDroolsRuleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class DroolsRuleServiceImpl implements IDroolsRuleService {

    @Autowired
    private DroolsRuleRepository droolsRuleRepository;

    @Override
    public DroolsRule findById(Long droolsRuleId) {
        Optional<DroolsRule> byIdOptional = droolsRuleRepository.findById(droolsRuleId);
        if (byIdOptional.isPresent()) {
            return byIdOptional.get();
        } else {
            return null;
        }
    }

    @Override
    public List<DroolsRule> findAll() {
        return droolsRuleRepository.findAll();
    }

    @Override
    public DroolsRule save(DroolsRule droolsRule) {
        return droolsRuleRepository.save(droolsRule);
    }

    @Override
    public DroolsRule edit(DroolsRule droolsRule) {
        return droolsRuleRepository.save(droolsRule);
    }

    @Override
    public boolean deleteById(Long droolsRuleId) {
        boolean result = true;
        try {
            droolsRuleRepository.deleteById(droolsRuleId);
        } catch (Exception ex) {
            result = false;
        }
        return result;
    }

    @Override
    public String success(Long droolsRuleId) {
        DroolsRule byId = findById(droolsRuleId);
        return "success(" + droolsRuleId + ") : " + byId;
    }

}
