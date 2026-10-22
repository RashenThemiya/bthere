package com.jobhub.repository.provider;

import com.jobhub.entity.provider.ProviderServiceOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;

import java.util.Collection;
import java.util.List;

public interface ProviderServiceOptionRepository extends JpaRepository<ProviderServiceOption, Long> {
    List<ProviderServiceOption> findAllByAssignmentId(Long assignmentId);
    Optional<ProviderServiceOption> findByProviderIdAndOptionId(Long providerId, Long optionId);
    List<ProviderServiceOption> findAllByVerificationStatus(String status);
    Page<ProviderServiceOption> findAllByVerificationStatus(String status, Pageable pageable);
    List<ProviderServiceOption> findAllByOptionIdIn(Collection<Long> optionIds);
    void deleteAllByAssignmentId(Long assignmentId);

    // Also used by read-only discovery. Booking capacity is serialized with provider locks.
    Optional<ProviderServiceOption> findByAssignmentIdAndOptionId(Long assignmentId, Long optionId);
    long countByVerificationStatus(String status);
}
