package com.jobhub.repository.auth;

import com.jobhub.entity.auth.VerificationToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.LocalDateTime;
import java.util.Optional;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {

    boolean existsByTypeAndDestinationAndCreatedAtAfter(
            String type,
            String destination,
            LocalDateTime createdAfter
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<VerificationToken> findFirstByTypeAndDestinationAndUsedAtIsNullOrderByCreatedAtDesc(
            String type,
            String destination
    );
}
