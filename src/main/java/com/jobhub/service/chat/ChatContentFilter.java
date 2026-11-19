package com.jobhub.service.chat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cleans chat text before it is stored: removes control and bidi-override characters, limits blank lines and masks
 * phone numbers and email addresses so bookings are not moved off the platform. Zero-width joiners are kept because
 * Sinhala conjuncts and emoji need them. Text is always plain text; clients must not render it as HTML.
 */
public final class ChatContentFilter {
    public static final String MASK = "[hidden]";
    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9._%+-]+\\s*(?:@|\\(at\\)|\\[at\\])\\s*[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}");
    /** 9 to 15 ASCII digits, optionally starting with + and separated by spaces, dots, dashes or brackets. */
    private static final Pattern PHONE = Pattern.compile(
            "(?<![\\p{L}\\p{N}(])\\(?\\+?\\d(?:[ ().-]{0,3}\\d){8,14}(?![\\p{L}\\p{N}])");
    private static final Pattern DATE = Pattern.compile(
            "\\d{4}[-./]\\d{1,2}[-./]\\d{1,2}|\\d{1,2}[-./]\\d{1,2}[-./]\\d{2,4}");
    /** C0/C1 controls except tab and newline, plus bidirectional override/isolate characters. */
    private static final Pattern UNSAFE = Pattern.compile(
            "[\\x00-\\x08\\x0B-\\x1F\\x7F-\\x9F\\u202A-\\u202E\\u2066-\\u2069\\uFEFF]");
    private static final Pattern BLANK_LINES = Pattern.compile("\\n{3,}");

    public record Result(String text, boolean contactInfoHidden) {}

    private ChatContentFilter() {}

    public static Result clean(String raw) {
        if (raw == null) return new Result("", false);
        String text = raw.replace("\r\n", "\n").replace('\r', '\n');
        text = UNSAFE.matcher(text).replaceAll("");
        text = BLANK_LINES.matcher(text).replaceAll("\n\n").strip();
        String masked = EMAIL.matcher(text).replaceAll(MASK);
        masked = PHONE.matcher(masked).replaceAll(m -> DATE.matcher(m.group()).find()
                ? Matcher.quoteReplacement(m.group()) : MASK);
        return new Result(masked, !masked.equals(text));
    }
}
