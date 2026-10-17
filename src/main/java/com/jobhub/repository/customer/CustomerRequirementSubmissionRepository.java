package com.jobhub.repository.customer;
import com.jobhub.entity.customer.CustomerRequirementSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import java.util.*;
public interface CustomerRequirementSubmissionRepository extends JpaRepository<CustomerRequirementSubmission, Long> {
    List<CustomerRequirementSubmission> findAllByCustomerId(Long customerId);
    List<CustomerRequirementSubmission> findAllByRequirementId(Long requirementId);
    Optional<CustomerRequirementSubmission> findByCustomerIdAndRequirementId(Long customerId, Long requirementId);
    Page<CustomerRequirementSubmission> findAllByStatus(String status, Pageable pageable);
}
