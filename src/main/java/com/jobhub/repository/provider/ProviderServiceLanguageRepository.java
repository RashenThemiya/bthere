package com.jobhub.repository.provider;

import com.jobhub.entity.provider.ProviderServiceLanguage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProviderServiceLanguageRepository extends JpaRepository<ProviderServiceLanguage, Long> {
    List<ProviderServiceLanguage> findAllByAssignmentIdOrderByLanguageCodeAsc(Long assignmentId);
    void deleteAllByAssignmentId(Long assignmentId);
}
