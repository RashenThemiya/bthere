package com.jobhub.service.auth;

import com.jobhub.dto.customer.ContactChangeApi.EmailVerifyRequest;
import com.jobhub.dto.customer.ContactChangeApi.PhoneVerifyRequest;
import com.jobhub.entity.auth.User;
import com.jobhub.entity.auth.UserAuthProvider;
import com.jobhub.entity.auth.VerificationToken;
import com.jobhub.exception.ConflictException;
import com.jobhub.exception.TooManyRequestsException;
import com.jobhub.repository.auth.UserAuthProviderRepository;
import com.jobhub.repository.auth.UserRepository;
import com.jobhub.repository.auth.VerificationTokenRepository;
import com.jobhub.security.OtpHashService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContactChangeServiceTest {
    @Mock UserRepository users;
    @Mock UserAuthProviderRepository authProviders;
    @Mock VerificationTokenRepository tokens;
    @Mock PasswordEncoder passwordEncoder;
    @Spy OtpHashService otpHash = new OtpHashService("0123456789abcdef0123456789abcdef-test");
    @Mock TextLkSmsService sms;
    @Mock AwsSesEmailService email;
    @InjectMocks ContactChangeService service;

    User user;

    @BeforeEach void setup() {
        service.otpExpirySeconds = 300; service.otpResendSeconds = 60; service.otpMaxAttempts = 5;
        user = new User(); user.setUserId(40L); user.setEmail("old@example.com"); user.setEmailVerified(true);
        user.setPhoneNumber("+94770000001"); user.setPhoneVerified(false); user.setPasswordHash("hash"); user.setStatus("ACTIVE");
        when(users.findById(40L)).thenReturn(Optional.of(user));
        when(users.findByEmail(anyString())).thenReturn(Optional.empty());
        when(users.findByPhoneNumber(anyString())).thenReturn(Optional.empty());
        when(tokens.findFirstByTypeAndDestinationOrderByCreatedAtDesc(anyString(), anyString())).thenReturn(Optional.empty());
        when(tokens.findFirstByTypeAndUserIdOrderByCreatedAtDesc(anyString(), anyLong())).thenReturn(Optional.empty());
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
    }

    /** Requests a code and returns it, as captured from the sender. */
    private String requestEmail(String address) {
        service.requestEmailCode(40L, address);
        var otp = ArgumentCaptor.forClass(String.class);
        verify(email).sendVerificationOtp(eq(address.trim().toLowerCase()), otp.capture(), eq(300L));
        var saved = ArgumentCaptor.forClass(VerificationToken.class);
        verify(tokens).saveAndFlush(saved.capture());
        VerificationToken t = saved.getValue();
        when(tokens.findFirstByTypeAndUserIdAndDestinationAndUsedAtIsNullOrderByCreatedAtDesc("EMAIL_CHANGE", 40L, t.getDestination()))
                .thenReturn(Optional.of(t));
        return otp.getValue();
    }

    @Test void emailChangeSendsCodeToNewAddressAndSavesOnlyAfterVerify() {
        String otp = requestEmail(" New@Example.com ");
        assertThat(user.getEmail()).isEqualTo("old@example.com");
        UserAuthProvider local = new UserAuthProvider(); local.setProvider("LOCAL"); local.setProviderEmail("old@example.com");
        when(authProviders.findAllByUserId(40L)).thenReturn(List.of(local));

        var contacts = service.verifyEmail(40L, new EmailVerifyRequest("new@example.com", otp, "secret"));
        assertThat(contacts.email()).isEqualTo("new@example.com");
        assertThat(contacts.emailVerified()).isTrue();
        assertThat(local.getProviderEmail()).isEqualTo("new@example.com");
    }

    @Test void wrongCodeCountsAnAttemptAndChangesNothing() {
        requestEmail("new@example.com");
        assertThatThrownBy(() -> service.verifyEmail(40L, new EmailVerifyRequest("new@example.com", "000000", "secret")))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(ContactChangeService.INVALID_CODE);
        var saved = ArgumentCaptor.forClass(VerificationToken.class);
        verify(tokens).save(saved.capture());
        assertThat(saved.getValue().getAttemptCount()).isEqualTo(1);
        assertThat(user.getEmail()).isEqualTo("old@example.com");
    }

    @Test void wrongPasswordUsesAnAttempt() {
        String otp = requestEmail("new@example.com");
        assertThatThrownBy(() -> service.verifyEmail(40L, new EmailVerifyRequest("new@example.com", otp, "nope")))
                .hasMessage("Current password is incorrect");
        assertThat(user.getEmail()).isEqualTo("old@example.com");
    }

    @Test void passwordRequiredOnlyForPasswordAccounts() {
        String otp = requestEmail("new@example.com");
        assertThatThrownBy(() -> service.verifyEmail(40L, new EmailVerifyRequest("new@example.com", otp, null)))
                .hasMessage("Current password is required");
        user.setPasswordHash(null);
        assertThat(service.verifyEmail(40L, new EmailVerifyRequest("new@example.com", otp, null)).email())
                .isEqualTo("new@example.com");
    }

    @Test void expiredOrExhaustedCodeIsRejected() {
        requestEmail("new@example.com");
        var t = tokens.findFirstByTypeAndUserIdAndDestinationAndUsedAtIsNullOrderByCreatedAtDesc("EMAIL_CHANGE", 40L, "new@example.com").get();
        t.setAttemptCount(5);
        assertThatThrownBy(() -> service.verifyEmail(40L, new EmailVerifyRequest("new@example.com", "123456", "secret")))
                .hasMessage(ContactChangeService.INVALID_CODE);
        assertThat(t.getUsedAt()).isNotNull();
    }

    @Test void codeFromAnotherUserNeverMatches() {
        // Same destination and code, but hashed for user 41: verifying as user 40 must fail.
        VerificationToken foreign = new VerificationToken(); foreign.setUserId(41L); foreign.setType("EMAIL_CHANGE");
        foreign.setDestination("new@example.com"); foreign.setAttemptCount(0); foreign.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        foreign.setTokenHash(otpHash.hash("EMAIL_CHANGE:41", "new@example.com", "123456"));
        when(tokens.findFirstByTypeAndUserIdAndDestinationAndUsedAtIsNullOrderByCreatedAtDesc("EMAIL_CHANGE", 40L, "new@example.com"))
                .thenReturn(Optional.of(foreign));
        assertThatThrownBy(() -> service.verifyEmail(40L, new EmailVerifyRequest("new@example.com", "123456", "secret")))
                .hasMessage(ContactChangeService.INVALID_CODE);
    }

    @Test void takenEmailIsConflictBeforeAndAfterCode() {
        when(users.existsByEmail("taken@example.com")).thenReturn(true);
        assertThatThrownBy(() -> service.requestEmailCode(40L, "taken@example.com")).isInstanceOf(ConflictException.class);
        verifyNoInteractions(email);

        String otp = requestEmail("race@example.com");
        User other = new User(); other.setUserId(99L);
        when(users.findByEmail("race@example.com")).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.verifyEmail(40L, new EmailVerifyRequest("race@example.com", otp, "secret")))
                .isInstanceOf(ConflictException.class);
        assertThat(user.getEmail()).isEqualTo("old@example.com");
    }

    @Test void alreadyVerifiedEmailIsRejected() {
        assertThatThrownBy(() -> service.requestEmailCode(40L, "OLD@example.com")).hasMessage("This email is already verified");
    }

    @Test void resendInsideCooldownIs429() {
        VerificationToken recent = new VerificationToken(); recent.setCreatedAt(LocalDateTime.now().minusSeconds(10));
        when(tokens.findFirstByTypeAndUserIdOrderByCreatedAtDesc("PHONE_CHANGE", 40L)).thenReturn(Optional.of(recent));
        assertThatThrownBy(() -> service.requestPhoneCode(40L, "+94770000002"))
                .isInstanceOf(TooManyRequestsException.class);
        verifyNoInteractions(sms);
    }

    @Test void currentUnverifiedPhoneCanBeVerifiedAndPhoneLinkFollows() {
        service.requestPhoneCode(40L, "+94770000001");
        var otp = ArgumentCaptor.forClass(String.class);
        verify(sms).sendOtp(eq("+94770000001"), otp.capture(), eq(300L));
        var saved = ArgumentCaptor.forClass(VerificationToken.class);
        verify(tokens).saveAndFlush(saved.capture());
        when(tokens.findFirstByTypeAndUserIdAndDestinationAndUsedAtIsNullOrderByCreatedAtDesc("PHONE_CHANGE", 40L, "+94770000001"))
                .thenReturn(Optional.of(saved.getValue()));
        UserAuthProvider phone = new UserAuthProvider(); phone.setProvider("PHONE"); phone.setProviderUserId("+94770000001");
        when(authProviders.findAllByUserId(40L)).thenReturn(List.of(phone));

        var contacts = service.verifyPhone(40L, new PhoneVerifyRequest("+94770000001", otp.getValue(), "secret"));
        assertThat(contacts.phoneVerified()).isTrue();
        assertThat(saved.getValue().getUsedAt()).isNotNull();
    }
}
