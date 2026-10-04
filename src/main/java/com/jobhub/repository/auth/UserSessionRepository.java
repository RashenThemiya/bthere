package com.jobhub.repository.auth;

import com.jobhub.entity.auth.UserSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UserSession> findByRefreshTokenHash(String refreshTokenHash);

    List<UserSession> findAllByUserIdAndRevokedAtIsNull(Long userId);
}
