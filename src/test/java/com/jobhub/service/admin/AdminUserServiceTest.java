package com.jobhub.service.admin;

import com.jobhub.dto.admin.AdminResponse;
import com.jobhub.dto.admin.AdminRole;
import com.jobhub.dto.admin.CreateAdminRequest;
import com.jobhub.entity.access.Role;
import com.jobhub.entity.auth.User;
import com.jobhub.repository.access.RoleRepository;
import com.jobhub.repository.access.UserRoleRepository;
import com.jobhub.repository.auth.UserAuthProviderRepository;
import com.jobhub.repository.auth.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private UserAuthProviderRepository authProviderRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AdminUserService adminUserService;

    @Test
    void superAdminCanCreateActiveCustomerWithoutOtp() {
        CreateAdminRequest request = new CreateAdminRequest(
                "New.Customer",
                "Customer@Example.com",
                "+94771234567",
                "strong-password",
                AdminRole.CUSTOMER
        );

        when(passwordEncoder.encode("strong-password")).thenReturn("encoded-password");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setUserId(101L);
            return user;
        });

        Role customerRole = new Role();
        customerRole.setRoleId(10L);
        customerRole.setName("CUSTOMER");
        customerRole.setStatus("ACTIVE");
        when(roleRepository.findByName("CUSTOMER")).thenReturn(Optional.of(customerRole));

        AdminResponse response = adminUserService.create(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        assertThat(savedUser.getUsername()).isEqualTo("new.customer");
        assertThat(savedUser.getEmail()).isEqualTo("customer@example.com");
        assertThat(savedUser.isEmailVerified()).isTrue();
        assertThat(savedUser.isPhoneVerified()).isFalse();
        assertThat(savedUser.getStatus()).isEqualTo("ACTIVE");
        assertThat(response.role()).isEqualTo(AdminRole.CUSTOMER);
    }
}
