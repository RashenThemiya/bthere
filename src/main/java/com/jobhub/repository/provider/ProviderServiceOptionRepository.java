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
    List<ProviderServiceOption> findAllByVerificationStatus(String status);
    Page<ProviderServiceOption> findAllByVerificationStatus(String status, Pageable pageable);
    List<ProviderServiceOption> findAllByOptionIdIn(Collection<Long> optionIds);
    void deleteAllByAssignmentId(Long assignmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ProviderServiceOption> findByAssignmentIdAndOptionId(Long assignmentId, Long optionId);
}
