package cn.net.pap.drools.repository;

import cn.net.pap.drools.entity.DroolsRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DroolsRuleRepository extends JpaRepository<DroolsRule, Long> {


}
