package com.jobhub.service.chat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-256-GCM encryption for chat message text at rest. Each ciphertext is bound to its job and sender (GCM
 * associated data), so a stored message cannot be moved into another conversation or attributed to someone else.
 * Uses CHAT_ENCRYPTION_KEY when set; otherwise a separate key derived from BOOKING_VERIFICATION_KEY.
 */
@Component
public class ChatCipher {
    private static final String VERSION = "v1:";
    private static final byte[] DERIVATION_LABEL = "jobhub chat message key v1".getBytes(StandardCharsets.UTF_8);
    private final String chatKey;
    private final String bookingKey;
    private final SecureRandom random = new SecureRandom();
    private volatile SecretKeySpec key;

    public ChatCipher(@Value("${chat.encryption-key:}") String chatKey,
                      @Value("${booking.verification-key:}") String bookingKey) {
        this.chatKey = chatKey == null ? "" : chatKey.trim();
        this.bookingKey = bookingKey == null ? "" : bookingKey.trim();
    }

    public String encrypt(Long jobId, Long senderUserId, String text) {
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            cipher.updateAAD(aad(jobId, senderUserId));
            byte[] value = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + value.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(value, 0, out, iv.length, value.length);
            return VERSION + Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot protect chat message", e);
        }
    }

    public String decrypt(Long jobId, Long senderUserId, String stored) {
        if (stored == null || !stored.startsWith(VERSION)) throw new IllegalStateException("Unknown chat message format");
        try {
            byte[] data = Base64.getDecoder().decode(stored.substring(VERSION.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Arrays.copyOf(data, 12)));
            cipher.updateAAD(aad(jobId, senderUserId));
            return new String(cipher.doFinal(Arrays.copyOfRange(data, 12, data.length)), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Cannot read chat message", e);
        }
    }

    private static byte[] aad(Long jobId, Long senderUserId) {
        return ("chat:" + jobId + ":" + senderUserId).getBytes(StandardCharsets.UTF_8);
    }

    private SecretKeySpec key() {
        SecretKeySpec current = key;
        if (current != null) return current;
        synchronized (this) {
            if (key == null) key = new SecretKeySpec(resolveKey(), "AES");
            return key;
        }
    }

    private byte[] resolveKey() {
        if (!chatKey.isEmpty()) return decode32(chatKey, "CHAT_ENCRYPTION_KEY");
        byte[] base = decode32(bookingKey, "CHAT_ENCRYPTION_KEY or BOOKING_VERIFICATION_KEY");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(base, "HmacSHA256"));
            return mac.doFinal(DERIVATION_LABEL);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot derive chat key", e);
        }
    }

    private static byte[] decode32(String value, String name) {
        try {
            byte[] bytes = Base64.getDecoder().decode(value);
            if (bytes.length == 32) return bytes;
        } catch (IllegalArgumentException ignored) {
            // reported below without echoing the value
        }
        throw new IllegalStateException(name + " must be a 32-byte base64 key");
    }
}
