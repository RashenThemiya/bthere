package com.jobhub.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;

@Service
public class OtpHashService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private final byte[] secret;

    public OtpHashService(@Value("${app.security.otp.hash-secret}") String secret) {
        if (!StringUtils.hasText(secret) || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("OTP_HASH_SECRET must contain at least 32 characters");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String hash(String purpose, String phoneNumber, String otp) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            byte[] value = (purpose + '|' + phoneNumber + '|' + otp)
                    .getBytes(StandardCharsets.UTF_8);
            return Base64.getEncoder().encodeToString(mac.doFinal(value));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to hash OTP", exception);
        }
    }

    public boolean matches(
            String expectedHash,
            String purpose,
            String phoneNumber,
            String otp
    ) {
        byte[] expected = expectedHash.getBytes(StandardCharsets.UTF_8);
        byte[] actual = hash(purpose, phoneNumber, otp).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }
}
