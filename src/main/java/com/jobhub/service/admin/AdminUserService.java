package com.jobhub.service.admin;

import com.jobhub.dto.admin.AdminResponse;
import com.jobhub.dto.admin.AdminRole;
import com.jobhub.dto.admin.CreateAdminRequest;
import com.jobhub.dto.admin.UserAdminResponse;
import com.jobhub.entity.access.Role;
import com.jobhub.entity.access.UserRole;
import com.jobhub.entity.auth.User;
import com.jobhub.entity.auth.UserAuthProvider;
import com.jobhub.exception.ConflictException;
import com.jobhub.repository.access.RoleRepository;
import com.jobhub.repository.access.UserRoleRepository;
import com.jobhub.repository.auth.UserAuthProviderRepository;
import com.jobhub.repository.auth.UserRepository;
import com.jobhub.repository.auth.UserSessionRepository;
import com.jobhub.service.audit.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;
import java.util.Set;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserAuthProviderRepository authProviderRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserSessionRepository sessionRepository;
    private final AuditService auditService;

    @Transactional
    public AdminResponse create(CreateAdminRequest request) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String phoneNumber = normalize(request.phoneNumber());

        validateUniqueUser(username, email, phoneNumber);

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPhoneNumber(phoneNumber);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setEmailVerified(true);
        user.setPhoneVerified(false);
        user.setStatus("ACTIVE");
        user = userRepository.saveAndFlush(user);

        Role role = getOrCreateRole(request.accountType());

        UserRole userRole = new UserRole();
        userRole.setUserId(user.getUserId());
        userRole.setRoleId(role.getRoleId());
        userRoleRepository.save(userRole);

        UserAuthProvider provider = new UserAuthProvider();
        provider.setUserId(user.getUserId());
        provider.setProvider("LOCAL");
        provider.setProviderUserId(username);
        provider.setProviderEmail(email);
        authProviderRepository.save(provider);

        return new AdminResponse(
                user.getUserId(),
                user.getUsername(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getStatus(),
                request.accountType(),
                user.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public Page<UserAdminResponse> search(String search, String status, String role, Pageable pageable) {
        Specification<User> specification = Specification.where(null);
        if (search != null && !search.isBlank()) {
            String value = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("username")), value),
                    cb.like(cb.lower(root.get("email")), value),
                    cb.like(cb.lower(root.get("phoneNumber")), value)));
        }
        if (status != null && !status.isBlank()) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("status"), status.trim().toUpperCase(Locale.ROOT)));
        }
        if (role != null && !role.isBlank()) {
            var userIds = userRoleRepository.findUserIdsByRoleName(role.trim());
            specification = specification.and((root, query, cb) -> root.get("userId").in(userIds));
        }
        Page<User> users = userRepository.findAll(specification, pageable);
        return users.map(this::toUserResponse);
    }

    @Transactional
    public UserAdminResponse updateStatus(Long actorId, Long userId, String requestedStatus) {
        String status = requestedStatus.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ACTIVE", "INACTIVE", "SUSPENDED").contains(status)) {
            throw new IllegalArgumentException("Status must be ACTIVE, INACTIVE or SUSPENDED");
        }
        if (actorId.equals(userId)) {
            throw new IllegalArgumentException("Super Admin cannot change their own status");
        }
        User user = requireUser(userId);
        String oldStatus = user.getStatus();
        user.setStatus(status);
        userRepository.save(user);
        if (!"ACTIVE".equals(status)) revokeSessions(userId);
        auditService.record(actorId, "USER_STATUS_UPDATED", "USER", userId,
                java.util.Map.of("status", oldStatus), java.util.Map.of("status", status));
        return toUserResponse(user);
    }

    @Transactional
    public void resetPassword(Long actorId, Long userId, String password) {
        User user = requireUser(userId);
        user.setPasswordHash(passwordEncoder.encode(password));
        userRepository.save(user);
        revokeSessions(userId);
        auditService.record(actorId, "USER_PASSWORD_RESET", "USER", userId, null,
                java.util.Map.of("sessionsRevoked", true));
    }

    private void revokeSessions(Long userId) {
        var sessions = sessionRepository.findAllByUserIdAndRevokedAtIsNull(userId);
        sessions.forEach(item -> item.setRevokedAt(LocalDateTime.now()));
        sessionRepository.saveAll(sessions);
    }

    private User requireUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new com.jobhub.exception.ResourceNotFoundException("User was not found"));
    }

    private UserAdminResponse toUserResponse(User user) {
        return new UserAdminResponse(user.getUserId(), user.getUsername(), user.getEmail(),
                user.getPhoneNumber(), user.isEmailVerified(), user.isPhoneVerified(),
                user.getStatus(), userRoleRepository.findActiveRoleNamesByUserId(user.getUserId()),
                user.getLastLoginAt(), user.getCreatedAt());
    }

    private Role getOrCreateRole(AdminRole adminRole) {
        String roleName = adminRole.name();
        return roleRepository.findByName(roleName).orElseGet(() -> {
            Role role = new Role();
            role.setName(roleName);
            role.setDescription(roleName.replace('_', ' ') + " account");
            role.setStatus("ACTIVE");
            return roleRepository.saveAndFlush(role);
        });
    }

    private void validateUniqueUser(String username, String email, String phoneNumber) {
        if (userRepository.existsByUsername(username)) {
            throw new ConflictException("Username is already registered");
        }
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email is already registered");
        }
        if (phoneNumber != null && userRepository.existsByPhoneNumber(phoneNumber)) {
            throw new ConflictException("Phone number is already registered");
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
