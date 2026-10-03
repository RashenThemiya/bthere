package com.jobhub.config;

import com.jobhub.entity.access.Role;
import com.jobhub.entity.access.UserRole;
import com.jobhub.entity.auth.User;
import com.jobhub.entity.auth.UserAuthProvider;
import com.jobhub.repository.access.RoleRepository;
import com.jobhub.repository.access.UserRoleRepository;
import com.jobhub.repository.auth.UserAuthProviderRepository;
import com.jobhub.repository.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.bootstrap.super-admin.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class SuperAdminBootstrap implements ApplicationRunner {

    private static final String SUPER_ADMIN_ROLE = "SUPER_ADMIN";
    private static final String CUSTOMER_ROLE = "CUSTOMER";
    private static final String SERVICE_PROVIDER_ROLE = "SERVICE_PROVIDER";
    private static final String LOCAL_PROVIDER = "LOCAL";

    private final UserRepository userRepository;
    private final UserAuthProviderRepository authProviderRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;

    @Value("${app.bootstrap.super-admin.username}")
    private String username;

    @Value("${app.bootstrap.super-admin.email}")
    private String email;

    @Value("${app.bootstrap.super-admin.password}")
    private String password;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(password)) {
            throw new IllegalStateException(
                    "SUPER_ADMIN_PASSWORD must be configured for the initial super-admin account"
            );
        }

        Role role = roleRepository.findByName(SUPER_ADMIN_ROLE)
                .orElseGet(this::createSuperAdminRole);

        ensureRole(CUSTOMER_ROLE, "Customer account");
        ensureRole(SERVICE_PROVIDER_ROLE, "Service provider account");

        User user = userRepository.findByUsername(username)
                .orElseGet(this::createSuperAdminUser);

        if (!userRoleRepository.existsByUserIdAndRoleId(user.getUserId(), role.getRoleId())) {
            UserRole userRole = new UserRole();
            userRole.setUserId(user.getUserId());
            userRole.setRoleId(role.getRoleId());
            userRoleRepository.save(userRole);
        }

        if (!authProviderRepository.existsByProviderAndProviderUserId(LOCAL_PROVIDER, username)) {
            UserAuthProvider provider = new UserAuthProvider();
            provider.setUserId(user.getUserId());
            provider.setProvider(LOCAL_PROVIDER);
            provider.setProviderUserId(username);
            provider.setProviderEmail(email);
            authProviderRepository.save(provider);
        }
    }

    private Role createSuperAdminRole() {
        Role role = new Role();
        role.setName(SUPER_ADMIN_ROLE);
        role.setDescription("Full platform access");
        role.setStatus("ACTIVE");
        return roleRepository.save(role);
    }

    private void ensureRole(String name, String description) {
        if (roleRepository.findByName(name).isPresent()) {
            return;
        }

        Role role = new Role();
        role.setName(name);
        role.setDescription(description);
        role.setStatus("ACTIVE");
        roleRepository.save(role);
    }

    private User createSuperAdminUser() {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(new BCryptPasswordEncoder(12).encode(password));
        user.setEmailVerified(true);
        user.setPhoneVerified(false);
        user.setStatus("ACTIVE");
        return userRepository.save(user);
    }
}
