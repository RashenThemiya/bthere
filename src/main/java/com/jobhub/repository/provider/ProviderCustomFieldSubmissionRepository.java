package com.jobhub.repository.provider;

import com.jobhub.entity.provider.ProviderCustomFieldSubmission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProviderCustomFieldSubmissionRepository
        extends JpaRepository<ProviderCustomFieldSubmission, Long> {
    List<ProviderCustomFieldSubmission> findAllByAssignmentId(Long assignmentId);
    Optional<ProviderCustomFieldSubmission> findByAssignmentIdAndFieldId(Long assignmentId, Long fieldId);
    Page<ProviderCustomFieldSubmission> findAllByVerificationStatus(String status, Pageable pageable);
}
