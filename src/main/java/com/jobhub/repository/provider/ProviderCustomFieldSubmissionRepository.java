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
    List<ProviderCustomFieldSubmission> findAllByFieldId(Long fieldId);
    Optional<ProviderCustomFieldSubmission> findByAssignmentIdAndFieldId(Long assignmentId, Long fieldId);
    @org.springframework.data.jpa.repository.Query("""
            select s from ProviderCustomFieldSubmission s
            where s.providerId = :providerId and s.reusableCode = :reusableCode
              and s.verificationStatus = :verificationStatus
              and not exists (select f.fieldId from ServiceCustomField f
                  where f.fieldId = s.fieldId and f.valueSource = 'PROVIDER_PROFILE')
            order by s.updatedAt desc
            """)
    List<ProviderCustomFieldSubmission> findAllByProviderIdAndReusableCodeAndVerificationStatusOrderByUpdatedAtDesc(
            @org.springframework.data.repository.query.Param("providerId") Long providerId,
            @org.springframework.data.repository.query.Param("reusableCode") String reusableCode,
            @org.springframework.data.repository.query.Param("verificationStatus") String verificationStatus);
    @org.springframework.data.jpa.repository.Query("""
            select s from ProviderCustomFieldSubmission s where s.verificationStatus = :status
              and not exists (select f.fieldId from ServiceCustomField f
                  where f.fieldId = s.fieldId and f.valueSource = 'PROVIDER_PROFILE')
            """)
    Page<ProviderCustomFieldSubmission> findAllByVerificationStatus(
            @org.springframework.data.repository.query.Param("status") String status, Pageable pageable);
    @org.springframework.data.jpa.repository.Query("""
            select count(s) from ProviderCustomFieldSubmission s where s.verificationStatus = :status
              and not exists (select f.fieldId from ServiceCustomField f
                  where f.fieldId = s.fieldId and f.valueSource = 'PROVIDER_PROFILE')
            """)
    long countByVerificationStatus(@org.springframework.data.repository.query.Param("status") String status);
}
