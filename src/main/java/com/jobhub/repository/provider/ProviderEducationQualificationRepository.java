package com.jobhub.repository.provider;

import com.jobhub.entity.provider.ProviderEducationQualification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProviderEducationQualificationRepository extends JpaRepository<ProviderEducationQualification, Long> {
    List<ProviderEducationQualification> findAllByAssignmentIdOrderByCreatedAtDesc(Long assignmentId);
    List<ProviderEducationQualification> findAllByVerificationStatusOrderByCreatedAtAsc(String status);
    Page<ProviderEducationQualification> findAllByVerificationStatus(String status, Pageable pageable);
}
