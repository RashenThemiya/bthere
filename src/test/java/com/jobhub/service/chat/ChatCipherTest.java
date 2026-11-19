package com.jobhub.service.chat;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatCipherTest {
    private static final String BOOKING_KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String CHAT_KEY = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

    @Test
    void roundTripsAndNeverStoresPlainText() {
        var cipher = new ChatCipher("", BOOKING_KEY);
        String stored = cipher.encrypt(7L, 21L, "හෙලෝ, I'm outside");
        assertThat(stored).startsWith("v1:").doesNotContain("outside");
        assertThat(cipher.encrypt(7L, 21L, "හෙලෝ, I'm outside")).isNotEqualTo(stored);
        assertThat(cipher.decrypt(7L, 21L, stored)).isEqualTo("හෙලෝ, I'm outside");
    }

    @Test
    void ciphertextIsBoundToItsJobAndSender() {
        var cipher = new ChatCipher("", BOOKING_KEY);
        String stored = cipher.encrypt(7L, 21L, "hello");
        assertThatThrownBy(() -> cipher.decrypt(8L, 21L, stored)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher.decrypt(7L, 22L, stored)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void explicitChatKeyIsUsedInsteadOfTheDerivedKey() {
        String derived = new ChatCipher("", BOOKING_KEY).encrypt(7L, 21L, "hello");
        var explicit = new ChatCipher(CHAT_KEY, BOOKING_KEY);
        assertThatThrownBy(() -> explicit.decrypt(7L, 21L, derived)).isInstanceOf(IllegalStateException.class);
        assertThat(explicit.decrypt(7L, 21L, explicit.encrypt(7L, 21L, "hello"))).isEqualTo("hello");
    }

    @Test
    void missingOrShortKeysFailWithoutEchoingThem() {
        assertThatThrownBy(() -> new ChatCipher("", "").encrypt(1L, 1L, "x"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("32-byte");
        assertThatThrownBy(() -> new ChatCipher("c2hvcnQ=", BOOKING_KEY).encrypt(1L, 1L, "x"))
                .isInstanceOf(IllegalStateException.class).hasMessageNotContaining("c2hvcnQ=");
    }
}
