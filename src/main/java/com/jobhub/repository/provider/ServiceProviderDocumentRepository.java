package com.jobhub.repository.provider;

import com.jobhub.entity.provider.ServiceProviderDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ServiceProviderDocumentRepository
        extends JpaRepository<ServiceProviderDocument, Long> {

    List<ServiceProviderDocument> findAllByServiceProviderIdOrderByCreatedAtDesc(
            Long serviceProviderId
    );

    Optional<ServiceProviderDocument> findByDocumentIdAndServiceProviderId(
            Long documentId,
            Long serviceProviderId
    );

    Optional<ServiceProviderDocument> findByServiceProviderIdAndDocumentTypeId(
            Long serviceProviderId,
            Long documentTypeId
    );

    List<ServiceProviderDocument> findAllByServiceProviderIdAndVerificationStatus(
            Long serviceProviderId,
            String verificationStatus
    );

    List<ServiceProviderDocument> findAllByVerificationStatusOrderByCreatedAtAsc(
            String verificationStatus
    );

    Page<ServiceProviderDocument> findAllByVerificationStatus(
            String verificationStatus, Pageable pageable
    );
    long countByVerificationStatus(String verificationStatus);
}
