package com.jobhub.service.admin;

import com.jobhub.dto.admin.AdminResponse;
import com.jobhub.dto.admin.AdminRole;
import com.jobhub.dto.admin.CreateAdminRequest;
import com.jobhub.entity.access.Role;
import com.jobhub.entity.access.UserRole;
import com.jobhub.entity.auth.User;
import com.jobhub.entity.auth.UserAuthProvider;
import com.jobhub.exception.ConflictException;
import com.jobhub.repository.access.RoleRepository;
import com.jobhub.repository.access.UserRoleRepository;
import com.jobhub.repository.auth.UserAuthProviderRepository;
import com.jobhub.repository.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserAuthProviderRepository authProviderRepository;
    private final PasswordEncoder passwordEncoder;

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
        user.setEmailVerified(false);
        user.setPhoneVerified(false);
        user.setStatus("ACTIVE");
        user = userRepository.saveAndFlush(user);

        Role role = getOrCreateRole(request.role());

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
                request.role(),
                user.getCreatedAt()
        );
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
