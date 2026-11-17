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

    Optional<VerificationToken> findFirstByTypeAndDestinationOrderByCreatedAtDesc(
            String type,
            String destination
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<VerificationToken> findFirstByTypeAndDestinationAndUsedAtIsNullOrderByCreatedAtDesc(
            String type,
            String destination
    );

    /** Contact change: per-user resend cooldown. */
    Optional<VerificationToken> findFirstByTypeAndUserIdOrderByCreatedAtDesc(String type, Long userId);

    /** Contact change: the code is bound to the signed-in user and the new address. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<VerificationToken> findFirstByTypeAndUserIdAndDestinationAndUsedAtIsNullOrderByCreatedAtDesc(
            String type,
            Long userId,
            String destination
    );
}
