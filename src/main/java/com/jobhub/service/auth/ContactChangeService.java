package com.jobhub.service.auth;

import com.jobhub.dto.customer.ContactChangeApi.CodeSent;
import com.jobhub.dto.customer.ContactChangeApi.EmailVerifyRequest;
import com.jobhub.dto.customer.ContactChangeApi.PhoneVerifyRequest;
import com.jobhub.dto.customer.ProfileOverviewApi.Contacts;
import com.jobhub.entity.auth.User;
import com.jobhub.entity.auth.VerificationToken;
import com.jobhub.exception.ConflictException;
import com.jobhub.exception.ResourceNotFoundException;
import com.jobhub.exception.TooManyRequestsException;
import com.jobhub.repository.auth.UserAuthProviderRepository;
import com.jobhub.repository.auth.UserRepository;
import com.jobhub.repository.auth.VerificationTokenRepository;
import com.jobhub.security.OtpHashService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

/**
 * Change or verify the signed-in account's email / phone. A code is sent to the NEW address and is bound to
 * this user and that address. The account changes only after the code is verified, so an unverified address
 * is never saved. Accounts with a password must also confirm it on the verify step; a wrong password uses up an
 * attempt, so neither the code nor the password can be guessed beyond the OTP attempt limit.
 */
@Service
@RequiredArgsConstructor
public class ContactChangeService {
    static final String EMAIL_CHANGE = "EMAIL_CHANGE";
    static final String PHONE_CHANGE = "PHONE_CHANGE";
    static final String INVALID_CODE = "Verification code is invalid or expired";

    private final UserRepository users;
    private final UserAuthProviderRepository authProviders;
    private final VerificationTokenRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final OtpHashService otpHash;
    private final TextLkSmsService sms;
    private final AwsSesEmailService email;
    private final SecureRandom random = new SecureRandom();

    @Value("${app.security.otp.expiry-seconds}") long otpExpirySeconds;
    @Value("${app.security.otp.resend-seconds}") long otpResendSeconds;
    @Value("${app.security.otp.max-attempts}") int otpMaxAttempts;

    @Transactional
    public CodeSent requestEmailCode(Long userId, String rawEmail) {
        User user = user(userId);
        String address = normalizeEmail(rawEmail);
        boolean current = address.equals(user.getEmail());
        if (current && user.isEmailVerified()) throw new IllegalArgumentException("This email is already verified");
        if (!current && users.existsByEmail(address)) throw new ConflictException("Email is already registered");
        String otp = issue(EMAIL_CHANGE, userId, address);
        email.sendVerificationOtp(address, otp, otpExpirySeconds);
        return new CodeSent(address, otpExpirySeconds, otpResendSeconds);
    }

    @Transactional
    public CodeSent requestPhoneCode(Long userId, String rawPhone) {
        User user = user(userId);
        String phone = rawPhone.trim();
        boolean current = phone.equals(user.getPhoneNumber());
        if (current && user.isPhoneVerified()) throw new IllegalArgumentException("This phone number is already verified");
        if (!current && users.existsByPhoneNumber(phone)) throw new ConflictException("Phone number is already registered");
        String otp = issue(PHONE_CHANGE, userId, phone);
        sms.sendOtp(phone, otp, otpExpirySeconds);
        return new CodeSent(phone, otpExpirySeconds, otpResendSeconds);
    }

    /** Attempt counts must survive a wrong code, so a 400 does not roll the transaction back. */
    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public Contacts verifyEmail(Long userId, EmailVerifyRequest in) {
        User user = user(userId);
        String address = normalizeEmail(in.email());
        consume(EMAIL_CHANGE, user, address, in.otp(), in.currentPassword());
        Optional<User> owner = users.findByEmail(address);
        if (owner.isPresent() && !owner.get().getUserId().equals(userId)) throw new ConflictException("Email is already registered");

        user.setEmail(address);
        user.setEmailVerified(true);
        users.saveAndFlush(user);
        authProviders.findAllByUserId(userId).stream().filter(p -> "LOCAL".equals(p.getProvider()))
                .forEach(p -> { p.setProviderEmail(address); authProviders.save(p); });
        return contacts(user);
    }

    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public Contacts verifyPhone(Long userId, PhoneVerifyRequest in) {
        User user = user(userId);
        String phone = in.phoneNumber().trim();
        consume(PHONE_CHANGE, user, phone, in.otp(), in.currentPassword());
        Optional<User> owner = users.findByPhoneNumber(phone);
        if (owner.isPresent() && !owner.get().getUserId().equals(userId)) throw new ConflictException("Phone number is already registered");

        user.setPhoneNumber(phone);
        user.setPhoneVerified(true);
        users.saveAndFlush(user);
        // Phone-OTP sign-in looks the account up by phone number; keep the PHONE link pointing at the new number.
        authProviders.findAllByUserId(userId).stream().filter(p -> "PHONE".equals(p.getProvider()))
                .forEach(p -> { p.setProviderUserId(phone); authProviders.save(p); });
        return contacts(user);
    }

    private User user(Long userId) {
        return users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private String issue(String type, Long userId, String destination) {
        cooldown(tokens.findFirstByTypeAndDestinationOrderByCreatedAtDesc(type, destination));
        cooldown(tokens.findFirstByTypeAndUserIdOrderByCreatedAtDesc(type, userId));
        String otp = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        VerificationToken token = new VerificationToken();
        token.setUserId(userId);
        token.setType(type);
        token.setDestination(destination);
        token.setTokenHash(otpHash.hash(purpose(type, userId), destination, otp));
        token.setAttemptCount(0);
        token.setExpiresAt(LocalDateTime.now().plusSeconds(otpExpirySeconds));
        tokens.saveAndFlush(token);
        return otp;
    }

    private void consume(String type, User user, String destination, String otp, String currentPassword) {
        boolean passwordRequired = user.getPasswordHash() != null;
        if (passwordRequired && (currentPassword == null || currentPassword.isEmpty())) {
            throw new IllegalArgumentException("Current password is required");
        }
        VerificationToken token = tokens
                .findFirstByTypeAndUserIdAndDestinationAndUsedAtIsNullOrderByCreatedAtDesc(type, user.getUserId(), destination)
                .orElseThrow(() -> new IllegalArgumentException(INVALID_CODE));
        if (token.getExpiresAt().isBefore(LocalDateTime.now()) || token.getAttemptCount() >= otpMaxAttempts) {
            token.setUsedAt(LocalDateTime.now());
            tokens.save(token);
            throw new IllegalArgumentException(INVALID_CODE);
        }
        token.setAttemptCount(token.getAttemptCount() + 1);
        if (!otpHash.matches(token.getTokenHash(), purpose(type, user.getUserId()), destination, otp)) {
            tokens.save(token);
            throw new IllegalArgumentException(INVALID_CODE);
        }
        if (passwordRequired && !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            tokens.save(token);
            throw new IllegalArgumentException("Current password is incorrect");
        }
        token.setUsedAt(LocalDateTime.now());
        tokens.save(token);
    }

    /** The user id is part of the hashed purpose, so a code can never be verified by another account. */
    private static String purpose(String type, Long userId) { return type + ":" + userId; }

    private void cooldown(Optional<VerificationToken> last) {
        last.filter(t -> t.getCreatedAt() != null).ifPresent(t -> {
            LocalDateTime retryAt = t.getCreatedAt().plusSeconds(otpResendSeconds);
            LocalDateTime now = LocalDateTime.now();
            if (!retryAt.isAfter(now)) return;
            long seconds = Math.max(1, (Duration.between(now, retryAt).toNanos() + 999_999_999L) / 1_000_000_000L);
            throw new TooManyRequestsException("Please wait before requesting another verification code", seconds);
        });
    }

    private static String normalizeEmail(String value) { return value.trim().toLowerCase(Locale.ROOT); }

    private static Contacts contacts(User u) {
        return new Contacts(u.getEmail(), u.isEmailVerified(), u.getPhoneNumber(), u.isPhoneVerified());
    }
}
