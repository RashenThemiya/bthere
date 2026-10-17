package com.jobhub.repository.customer;
import com.jobhub.entity.customer.CustomerEligibilityRule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface CustomerEligibilityRuleRepository extends JpaRepository<CustomerEligibilityRule, Long> {
    Optional<CustomerEligibilityRule> findByTargetKey(String targetKey);
    List<CustomerEligibilityRule> findAllByEnabledTrue();
}
