package com.jobhub.service.auth;

import com.jobhub.dto.auth.LoginResponse;
import com.jobhub.dto.auth.RefreshTokenRequest;
import com.jobhub.entity.auth.User;
import com.jobhub.entity.auth.UserSession;
import com.jobhub.repository.access.RoleRepository;
import com.jobhub.repository.access.UserRoleRepository;
import com.jobhub.repository.auth.UserAuthProviderRepository;
import com.jobhub.repository.auth.UserRepository;
import com.jobhub.repository.auth.UserSessionRepository;
import com.jobhub.repository.auth.VerificationTokenRepository;
import com.jobhub.security.GoogleIdTokenVerifier;
import com.jobhub.security.JwtService;
import com.jobhub.security.OtpHashService;
import com.jobhub.security.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationSessionServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserAuthProviderRepository authProviderRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private UserSessionRepository userSessionRepository;
    @Mock private VerificationTokenRepository verificationTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private GoogleIdTokenVerifier googleIdTokenVerifier;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private OtpHashService otpHashService;
    @Mock private TextLkSmsService smsService;
    @Mock private AwsSesEmailService emailService;
    @Mock private HttpServletRequest httpRequest;

    @InjectMocks
    private AuthenticationService authenticationService;

    @Test
    void refreshRotatesRefreshTokenAndReturnsNewAccessToken() {
        ReflectionTestUtils.setField(authenticationService, "refreshTokenDays", 30L);

        User user = new User();
        user.setUserId(4L);
        user.setUsername("customer");
        user.setEmail("customer@example.com");
        user.setStatus("ACTIVE");

        UserSession session = new UserSession();
        session.setSessionId(20L);
        session.setUserId(4L);
        session.setRefreshTokenHash("old-hash");
        session.setExpiresAt(LocalDateTime.now().plusDays(2));

        when(refreshTokenService.hash("old-token")).thenReturn("old-hash");
        when(userSessionRepository.findByRefreshTokenHash("old-hash"))
                .thenReturn(Optional.of(session));
        when(userRepository.findById(4L)).thenReturn(Optional.of(user));
        when(refreshTokenService.generateToken()).thenReturn("new-token");
        when(refreshTokenService.hash("new-token")).thenReturn("new-hash");
        when(userRoleRepository.findActiveRoleNamesByUserId(4L))
                .thenReturn(List.of("CUSTOMER"));
        when(jwtService.createAccessToken(user, List.of("CUSTOMER")))
                .thenReturn("new-access-token");
        when(jwtService.getAccessTokenSeconds()).thenReturn(900L);
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(httpRequest.getHeader("User-Agent")).thenReturn("test-client");

        LoginResponse response = authenticationService.refresh(
                new RefreshTokenRequest("old-token"),
                httpRequest
        );

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isEqualTo("new-token");
        assertThat(session.getRefreshTokenHash()).isEqualTo("new-hash");
        assertThat(session.getLastUsedAt()).isNotNull();
        verify(userSessionRepository).save(session);
    }

    @Test
    void logoutAllRevokesEveryActiveSession() {
        UserSession first = new UserSession();
        UserSession second = new UserSession();
        when(userSessionRepository.findAllByUserIdAndRevokedAtIsNull(4L))
                .thenReturn(List.of(first, second));

        authenticationService.logoutAll(4L);

        assertThat(first.getRevokedAt()).isNotNull();
        assertThat(second.getRevokedAt()).isNotNull();
        verify(userSessionRepository).saveAll(List.of(first, second));
    }
}
