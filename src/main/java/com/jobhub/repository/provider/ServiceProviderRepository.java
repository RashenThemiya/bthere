package com.jobhub.repository.provider;

import com.jobhub.entity.provider.ServiceProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ServiceProviderRepository extends JpaRepository<ServiceProvider, Long> {

    Optional<ServiceProvider> findByUserId(Long userId);
}
