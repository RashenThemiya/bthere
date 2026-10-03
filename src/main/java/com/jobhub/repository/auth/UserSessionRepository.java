package com.jobhub.repository.auth;

import com.jobhub.entity.auth.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {
}
