package com.jobhub.repository.customer;
import com.jobhub.entity.customer.CustomerRequirement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface CustomerRequirementRepository extends JpaRepository<CustomerRequirement, Long> {
    List<CustomerRequirement> findAllByStatusOrderByDisplayOrderAscRequirementIdAsc(String status);
    Optional<CustomerRequirement> findByCode(String code);
}
