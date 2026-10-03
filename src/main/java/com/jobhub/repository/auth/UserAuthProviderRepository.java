package com.jobhub.repository.auth;

import com.jobhub.entity.auth.UserAuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserAuthProviderRepository extends JpaRepository<UserAuthProvider, Long> {

    boolean existsByProviderAndProviderUserId(String provider, String providerUserId);

    Optional<UserAuthProvider> findByProviderAndProviderUserId(
            String provider,
            String providerUserId
    );
}
