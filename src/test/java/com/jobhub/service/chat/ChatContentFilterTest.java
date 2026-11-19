package com.jobhub.service.chat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ChatContentFilterTest {

    @ParameterizedTest
    @ValueSource(strings = {"0771234567", "077 123 4567", "077-123-4567", "+94771234567", "+94 77 123 4567",
            "(077) 123 4567", "94771234567", "011 2 345 678"})
    void masksPhoneNumbers(String phone) {
        var result = ChatContentFilter.clean("call me on " + phone + " ok");
        assertThat(result.text()).isEqualTo("call me on [hidden] ok");
        assertThat(result.contactInfoHidden()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"madu@gmail.com", "madu.perera+x@mail.example.lk", "madu (at) gmail.com", "madu@ gmail.com"})
    void masksEmails(String email) {
        var result = ChatContentFilter.clean("mail " + email + " please");
        assertThat(result.text()).isEqualTo("mail [hidden] please");
        assertThat(result.contactInfoHidden()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"I will arrive at 10:30", "Booking on 2026-11-18 10:30", "on 18/11/2026 at 9",
            "Rs. 2500 for 2 hours", "Gate 12, room 304", "Booking #1234567"})
    void keepsOrdinaryNumbers(String text) {
        var result = ChatContentFilter.clean(text);
        assertThat(result.text()).isEqualTo(text);
        assertThat(result.contactInfoHidden()).isFalse();
    }

    @Test
    void keepsSinhalaConjunctsAndEmojiButStripsBidiOverrides() {
        String sinhala = "ක්‍ය ශ්‍රී ලංකාව ❤️‍🔥";
        assertThat(ChatContentFilter.clean(sinhala).text()).isEqualTo(sinhala);
        assertThat(ChatContentFilter.clean("ok‮txt.exe\u0007").text()).isEqualTo("oktxt.exe");
    }

    @Test
    void normalisesLineBreaksAndTrims() {
        assertThat(ChatContentFilter.clean("  hi\r\n\r\n\r\n\r\nthere  ").text()).isEqualTo("hi\n\nthere");
        assertThat(ChatContentFilter.clean("   ").text()).isEmpty();
        assertThat(ChatContentFilter.clean(null).text()).isEmpty();
    }
}
