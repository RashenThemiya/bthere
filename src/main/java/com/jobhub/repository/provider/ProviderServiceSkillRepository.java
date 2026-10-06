package com.jobhub.repository.provider;

import com.jobhub.entity.provider.ProviderServiceSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProviderServiceSkillRepository extends JpaRepository<ProviderServiceSkill, Long> {
    List<ProviderServiceSkill> findAllByAssignmentId(Long assignmentId);
    List<ProviderServiceSkill> findAllByVerificationStatusOrderByCreatedAtAsc(String status);
    Page<ProviderServiceSkill> findAllByVerificationStatus(String status, Pageable pageable);
    void deleteAllByAssignmentId(Long assignmentId);
    long countByVerificationStatus(String status);
}
