package com.jobhub.service.auth;

import com.jobhub.dto.auth.CurrentUserResponse;
import com.jobhub.dto.auth.EmailResendRequest;
import com.jobhub.dto.auth.EmailVerificationRequest;
import com.jobhub.dto.auth.GoogleLoginRequest;
import com.jobhub.dto.auth.LoginRequest;
import com.jobhub.dto.auth.LoginResponse;
import com.jobhub.dto.auth.OtpPurpose;
import com.jobhub.dto.auth.OtpRequest;
import com.jobhub.dto.auth.OtpRequestResponse;
import com.jobhub.dto.auth.OtpVerifyRequest;
import com.jobhub.dto.auth.RegisterRequest;
import com.jobhub.dto.auth.RegisterResponse;
import com.jobhub.dto.auth.RefreshTokenRequest;
import com.jobhub.entity.access.Role;
import com.jobhub.entity.access.UserRole;
import com.jobhub.entity.auth.User;
import com.jobhub.entity.auth.UserAuthProvider;
import com.jobhub.entity.auth.UserSession;
import com.jobhub.entity.auth.VerificationToken;
import com.jobhub.exception.ConflictException;
import com.jobhub.exception.UnauthorizedException;
import com.jobhub.exception.TooManyRequestsException;
import com.jobhub.repository.access.RoleRepository;
import com.jobhub.repository.access.UserRoleRepository;
import com.jobhub.repository.auth.UserAuthProviderRepository;
import com.jobhub.repository.auth.UserRepository;
import com.jobhub.repository.auth.UserSessionRepository;
import com.jobhub.repository.auth.VerificationTokenRepository;
import com.jobhub.security.JwtService;
import com.jobhub.security.GoogleIdTokenVerifier;
import com.jobhub.security.GoogleIdentity;
import com.jobhub.security.RefreshTokenService;
import com.jobhub.security.OtpHashService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private static final Set<String> SOCIAL_LOGIN_ROLES = Set.of(
            "CUSTOMER",
            "SERVICE_PROVIDER"
    );

    private final UserRepository userRepository;
    private final UserAuthProviderRepository authProviderRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserSessionRepository userSessionRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final RefreshTokenService refreshTokenService;
    private final OtpHashService otpHashService;
    private final AwsSnsSmsService smsService;
    private final AwsSesEmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.security.refresh-token-days}")
    private long refreshTokenDays;

    @Value("${app.security.otp.expiry-seconds}")
    private long otpExpirySeconds;

    @Value("${app.security.otp.resend-seconds}")
    private long otpResendSeconds;

    @Value("${app.security.otp.max-attempts}")
    private int otpMaxAttempts;

    @Transactional
    public OtpRequestResponse requestOtp(OtpRequest request) {
        String phoneNumber = request.phoneNumber().trim();
        User existingUser = userRepository.findByPhoneNumber(phoneNumber).orElse(null);

        boolean eligible = request.purpose() == OtpPurpose.REGISTER
                ? existingUser == null
                : existingUser != null
                        && "ACTIVE".equals(existingUser.getStatus())
                        && isSocialLoginUser(existingUser);

        if (!eligible) {
            return acceptedOtpResponse();
        }

        String tokenType = otpTokenType(request.purpose());
        LocalDateTime cooldownStart = LocalDateTime.now().minusSeconds(otpResendSeconds);
        if (verificationTokenRepository.existsByTypeAndDestinationAndCreatedAtAfter(
                tokenType,
                phoneNumber,
                cooldownStart
        )) {
            throw new TooManyRequestsException("Please wait before requesting another OTP");
        }

        String otp = String.format(Locale.ROOT, "%06d", secureRandom.nextInt(1_000_000));
        VerificationToken token = new VerificationToken();
        token.setUserId(existingUser == null ? null : existingUser.getUserId());
        token.setType(tokenType);
        token.setDestination(phoneNumber);
        token.setTokenHash(otpHashService.hash(tokenType, phoneNumber, otp));
        token.setAttemptCount(0);
        token.setExpiresAt(LocalDateTime.now().plusSeconds(otpExpirySeconds));
        verificationTokenRepository.saveAndFlush(token);

        smsService.sendOtp(phoneNumber, otp, otpExpirySeconds);
        return acceptedOtpResponse();
    }

    @Transactional
    public OtpRequestResponse resendEmailVerification(EmailResendRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || user.isEmailVerified() || !"PENDING".equals(user.getStatus())) {
            return acceptedEmailOtpResponse();
        }

        sendEmailVerificationOtp(user);
        return acceptedEmailOtpResponse();
    }

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public LoginResponse verifyEmail(
            EmailVerificationRequest request,
            HttpServletRequest httpRequest
    ) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String tokenType = "EMAIL_VERIFY";
        VerificationToken token = verificationTokenRepository
                .findFirstByTypeAndDestinationAndUsedAtIsNullOrderByCreatedAtDesc(
                        tokenType,
                        email
                )
                .orElseThrow(() -> new UnauthorizedException("Email OTP is invalid or expired"));

        if (token.getExpiresAt().isBefore(LocalDateTime.now())
                || token.getAttemptCount() >= otpMaxAttempts) {
            token.setUsedAt(LocalDateTime.now());
            throw new UnauthorizedException("Email OTP is invalid or expired");
        }

        token.setAttemptCount(token.getAttemptCount() + 1);
        if (!otpHashService.matches(token.getTokenHash(), tokenType, email, request.otp())) {
            verificationTokenRepository.save(token);
            throw new UnauthorizedException("Email OTP is invalid or expired");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Email OTP is invalid or expired"));
        token.setUsedAt(LocalDateTime.now());
        verificationTokenRepository.save(token);

        user.setEmailVerified(true);
        user.setStatus("ACTIVE");
        userRepository.save(user);
        return issueTokens(user, httpRequest);
    }

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public LoginResponse verifyOtp(
            OtpVerifyRequest request,
            HttpServletRequest httpRequest
    ) {
        String phoneNumber = request.phoneNumber().trim();
        String tokenType = otpTokenType(request.purpose());
        VerificationToken token = verificationTokenRepository
                .findFirstByTypeAndDestinationAndUsedAtIsNullOrderByCreatedAtDesc(
                        tokenType,
                        phoneNumber
                )
                .orElseThrow(() -> new UnauthorizedException("OTP is invalid or expired"));

        if (token.getExpiresAt().isBefore(LocalDateTime.now())
                || token.getAttemptCount() >= otpMaxAttempts) {
            token.setUsedAt(LocalDateTime.now());
            throw new UnauthorizedException("OTP is invalid or expired");
        }

        token.setAttemptCount(token.getAttemptCount() + 1);
        if (!otpHashService.matches(
                token.getTokenHash(),
                tokenType,
                phoneNumber,
                request.otp()
        )) {
            verificationTokenRepository.save(token);
            throw new UnauthorizedException("OTP is invalid or expired");
        }

        token.setUsedAt(LocalDateTime.now());
        verificationTokenRepository.save(token);

        User user = request.purpose() == OtpPurpose.REGISTER
                ? createPhoneUser(phoneNumber, request)
                : findPhoneLoginUser(phoneNumber);

        ensurePhoneProvider(user, phoneNumber);
        return issueTokens(user, httpRequest);
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
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
        user.setStatus("PENDING");
        user = userRepository.saveAndFlush(user);

        String roleName = request.type().name();
        Role role = roleRepository.findByName(roleName)
                .orElseGet(() -> createRole(roleName));

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

        sendEmailVerificationOtp(user);

        return new RegisterResponse(
                user.getUserId(),
                user.getUsername(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getStatus(),
                user.isEmailVerified(),
                user.isPhoneVerified(),
                List.of(roleName)
        );
    }

    @Transactional
    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        User user = findUser(request.username().trim());

        if (!"ACTIVE".equals(user.getStatus())
                || user.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid username or password");
        }

        return issueTokens(user, httpRequest);
    }

    @Transactional
    public LoginResponse refresh(
            RefreshTokenRequest request,
            HttpServletRequest httpRequest
    ) {
        String tokenHash = refreshTokenService.hash(request.refreshToken());
        UserSession session = userSessionRepository.findByRefreshTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("Refresh token is invalid or expired"));

        if (session.getRevokedAt() != null
                || session.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new UnauthorizedException("Refresh token is invalid or expired");
        }

        User user = userRepository.findById(session.getUserId())
                .orElseThrow(() -> new UnauthorizedException("Refresh token is invalid or expired"));
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new UnauthorizedException("User account is not active");
        }

        String newRefreshToken = refreshTokenService.generateToken();
        session.setRefreshTokenHash(refreshTokenService.hash(newRefreshToken));
        session.setIpAddress(httpRequest.getRemoteAddr());
        session.setUserAgent(limit(httpRequest.getHeader("User-Agent"), 1024));
        session.setLastUsedAt(LocalDateTime.now());
        session.setExpiresAt(LocalDateTime.now().plusDays(refreshTokenDays));
        userSessionRepository.save(session);

        return createTokenResponse(user, newRefreshToken);
    }

    @Transactional
    public void logout(RefreshTokenRequest request) {
        String tokenHash = refreshTokenService.hash(request.refreshToken());
        userSessionRepository.findByRefreshTokenHash(tokenHash).ifPresent(session -> {
            if (session.getRevokedAt() == null) {
                session.setRevokedAt(LocalDateTime.now());
                userSessionRepository.save(session);
            }
        });
    }

    @Transactional
    public void logoutAll(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        List<UserSession> sessions = userSessionRepository
                .findAllByUserIdAndRevokedAtIsNull(userId);
        sessions.forEach(session -> session.setRevokedAt(now));
        userSessionRepository.saveAll(sessions);
    }

    @Transactional
    public LoginResponse googleLogin(
            GoogleLoginRequest request,
            HttpServletRequest httpRequest
    ) {
        GoogleIdentity identity = googleIdTokenVerifier.verify(request.idToken());

        UserAuthProvider existingProvider = authProviderRepository
                .findByProviderAndProviderUserId("GOOGLE", identity.subject())
                .orElse(null);

        User user;
        if (existingProvider != null) {
            user = userRepository.findById(existingProvider.getUserId())
                    .orElseThrow(() -> new UnauthorizedException("Google account is not linked correctly"));
        } else {
            String email = identity.email().trim().toLowerCase(Locale.ROOT);
            User emailUser = userRepository.findByEmail(email).orElse(null);
            if (emailUser != null && !isSocialLoginUser(emailUser)) {
                throw new UnauthorizedException(
                        "Google login is not available for administrative accounts"
                );
            }
            user = emailUser != null
                    ? emailUser
                    : createGoogleUser(email, identity.subject(), request);
            linkGoogleProvider(user, identity);
        }

        if (!isSocialLoginUser(user)) {
            throw new UnauthorizedException(
                    "Google login is not available for administrative accounts"
            );
        }

        if (!"ACTIVE".equals(user.getStatus())) {
            throw new UnauthorizedException("User account is not active");
        }

        if (!user.isEmailVerified()) {
            user.setEmailVerified(true);
            userRepository.save(user);
        }

        return issueTokens(user, httpRequest);
    }

    private LoginResponse issueTokens(User user, HttpServletRequest httpRequest) {
        String refreshToken = refreshTokenService.generateToken();

        UserSession session = new UserSession();
        session.setUserId(user.getUserId());
        session.setRefreshTokenHash(refreshTokenService.hash(refreshToken));
        session.setIpAddress(httpRequest.getRemoteAddr());
        session.setUserAgent(limit(httpRequest.getHeader("User-Agent"), 1024));
        session.setExpiresAt(LocalDateTime.now().plusDays(refreshTokenDays));
        session.setLastUsedAt(LocalDateTime.now());
        userSessionRepository.save(session);

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        return createTokenResponse(user, refreshToken);
    }

    private LoginResponse createTokenResponse(User user, String refreshToken) {
        List<String> roles = userRoleRepository.findActiveRoleNamesByUserId(user.getUserId());
        String accessToken = jwtService.createAccessToken(user, roles);

        CurrentUserResponse currentUser = new CurrentUserResponse(
                user.getUserId(),
                user.getUsername(),
                user.getEmail(),
                user.getStatus(),
                List.copyOf(roles)
        );

        return new LoginResponse(
                accessToken,
                refreshToken,
                "Bearer",
                jwtService.getAccessTokenSeconds(),
                currentUser
        );
    }

    private User createGoogleUser(
            String email,
            String googleSubject,
            GoogleLoginRequest request
    ) {
        User user = new User();
        user.setUsername(generateGoogleUsername(email, googleSubject));
        user.setEmail(email);
        user.setPasswordHash(null);
        user.setEmailVerified(true);
        user.setPhoneVerified(false);
        user.setStatus("ACTIVE");
        user = userRepository.saveAndFlush(user);

        String roleName = request.type().name();
        Role role = roleRepository.findByName(roleName)
                .orElseGet(() -> createRole(roleName));

        UserRole userRole = new UserRole();
        userRole.setUserId(user.getUserId());
        userRole.setRoleId(role.getRoleId());
        userRoleRepository.save(userRole);
        return user;
    }

    private void linkGoogleProvider(User user, GoogleIdentity identity) {
        UserAuthProvider provider = new UserAuthProvider();
        provider.setUserId(user.getUserId());
        provider.setProvider("GOOGLE");
        provider.setProviderUserId(identity.subject());
        provider.setProviderEmail(identity.email().toLowerCase(Locale.ROOT));
        authProviderRepository.save(provider);
    }

    private String generateGoogleUsername(String email, String subject) {
        String emailPrefix = email.substring(0, email.indexOf('@'))
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]", "");
        String base = emailPrefix.length() >= 3 ? emailPrefix : "google";
        base = base.substring(0, Math.min(base.length(), 35));
        String suffix = subject.substring(Math.max(0, subject.length() - 10));
        String username = base + "-" + suffix;

        int counter = 1;
        while (userRepository.existsByUsername(username)) {
            username = base + "-" + suffix + "-" + counter++;
        }
        return username;
    }

    private User createPhoneUser(String phoneNumber, OtpVerifyRequest request) {
        if (request.type() == null) {
            throw new IllegalArgumentException(
                    "Registration type is required when registering by phone"
            );
        }
        if (userRepository.existsByPhoneNumber(phoneNumber)) {
            throw new ConflictException("Phone number is already registered");
        }

        User user = new User();
        user.setUsername(generatePhoneUsername(phoneNumber));
        user.setPhoneNumber(phoneNumber);
        user.setPasswordHash(null);
        user.setEmailVerified(false);
        user.setPhoneVerified(true);
        user.setStatus("ACTIVE");
        user = userRepository.saveAndFlush(user);

        String roleName = request.type().name();
        Role role = roleRepository.findByName(roleName)
                .orElseGet(() -> createRole(roleName));

        UserRole userRole = new UserRole();
        userRole.setUserId(user.getUserId());
        userRole.setRoleId(role.getRoleId());
        userRoleRepository.save(userRole);
        return user;
    }

    private User findPhoneLoginUser(String phoneNumber) {
        User user = userRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new UnauthorizedException("OTP is invalid or expired"));
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new UnauthorizedException("User account is not active");
        }
        if (!isSocialLoginUser(user)) {
            throw new UnauthorizedException(
                    "Phone OTP login is not available for administrative accounts"
            );
        }
        if (!user.isPhoneVerified()) {
            user.setPhoneVerified(true);
            userRepository.save(user);
        }
        return user;
    }

    private boolean isSocialLoginUser(User user) {
        List<String> roles = userRoleRepository.findActiveRoleNamesByUserId(user.getUserId());
        return !roles.isEmpty() && roles.stream().allMatch(SOCIAL_LOGIN_ROLES::contains);
    }

    private void ensurePhoneProvider(User user, String phoneNumber) {
        if (authProviderRepository.existsByProviderAndProviderUserId("PHONE", phoneNumber)) {
            return;
        }

        UserAuthProvider provider = new UserAuthProvider();
        provider.setUserId(user.getUserId());
        provider.setProvider("PHONE");
        provider.setProviderUserId(phoneNumber);
        authProviderRepository.save(provider);
    }

    private String generatePhoneUsername(String phoneNumber) {
        String digits = phoneNumber.replaceAll("[^0-9]", "");
        String suffix = digits.substring(Math.max(0, digits.length() - 10));
        String base = "user-" + suffix;
        String username = base;
        int counter = 1;
        while (userRepository.existsByUsername(username)) {
            username = base + '-' + counter++;
        }
        return username;
    }

    private void sendEmailVerificationOtp(User user) {
        String tokenType = "EMAIL_VERIFY";
        LocalDateTime cooldownStart = LocalDateTime.now().minusSeconds(otpResendSeconds);
        if (verificationTokenRepository.existsByTypeAndDestinationAndCreatedAtAfter(
                tokenType,
                user.getEmail(),
                cooldownStart
        )) {
            throw new TooManyRequestsException(
                    "Please wait before requesting another email verification code"
            );
        }

        String otp = generateOtp();
        VerificationToken token = new VerificationToken();
        token.setUserId(user.getUserId());
        token.setType(tokenType);
        token.setDestination(user.getEmail());
        token.setTokenHash(otpHashService.hash(tokenType, user.getEmail(), otp));
        token.setAttemptCount(0);
        token.setExpiresAt(LocalDateTime.now().plusSeconds(otpExpirySeconds));
        verificationTokenRepository.saveAndFlush(token);
        emailService.sendVerificationOtp(user.getEmail(), otp, otpExpirySeconds);
    }

    private String generateOtp() {
        return String.format(Locale.ROOT, "%06d", secureRandom.nextInt(1_000_000));
    }

    private String otpTokenType(OtpPurpose purpose) {
        return purpose == OtpPurpose.REGISTER
                ? "PHONE_REGISTER_OTP"
                : "PHONE_LOGIN_OTP";
    }

    private OtpRequestResponse acceptedOtpResponse() {
        return new OtpRequestResponse(
                "If the phone number is eligible, an OTP has been sent",
                otpExpirySeconds
        );
    }

    private OtpRequestResponse acceptedEmailOtpResponse() {
        return new OtpRequestResponse(
                "If the email is awaiting verification, a code has been sent",
                otpExpirySeconds
        );
    }

    private User findUser(String usernameOrEmail) {
        String normalized = usernameOrEmail.toLowerCase(Locale.ROOT);
        return userRepository.findByUsername(normalized)
                .or(() -> userRepository.findByEmail(normalized))
                .orElseThrow(() -> new UnauthorizedException("Invalid username or password"));
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

    private Role createRole(String roleName) {
        Role role = new Role();
        role.setName(roleName);
        role.setDescription(roleName.replace('_', ' ') + " account");
        role.setStatus("ACTIVE");
        return roleRepository.saveAndFlush(role);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String limit(String value, int maximumLength) {
        if (value == null || value.length() <= maximumLength) {
            return value;
        }
        return value.substring(0, maximumLength);
    }
}
