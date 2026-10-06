package com.jobhub.repository.provider;

import com.jobhub.entity.provider.ProviderProfessionalCertificate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProviderProfessionalCertificateRepository extends JpaRepository<ProviderProfessionalCertificate, Long> {
    List<ProviderProfessionalCertificate> findAllByAssignmentIdOrderByCreatedAtDesc(Long assignmentId);
    List<ProviderProfessionalCertificate> findAllByVerificationStatusOrderByCreatedAtAsc(String status);
    Page<ProviderProfessionalCertificate> findAllByVerificationStatus(String status, Pageable pageable);
}
