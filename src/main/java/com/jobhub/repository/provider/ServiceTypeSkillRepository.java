package com.jobhub.repository.provider;

import com.jobhub.entity.provider.ServiceTypeSkill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServiceTypeSkillRepository extends JpaRepository<ServiceTypeSkill, Long> {

    List<ServiceTypeSkill> findAllByServiceProviderTypeIdOrderByNameAsc(
            Long serviceProviderTypeId
    );

    void deleteAllByServiceProviderTypeId(Long serviceProviderTypeId);
}
