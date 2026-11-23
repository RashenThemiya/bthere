package com.jobhub.service.chat;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

/**
 * File checks for chat attachments. The type is decided from the file's first bytes, never from the client's
 * Content-Type or file name, and image dimensions are read from the header without decoding the image.
 */
public final class ChatFiles {
    public static final long MAX_BYTES = 10L * 1024 * 1024;

    public enum Kind { IMAGE, DOCUMENT }

    public record Type(String mimeType, String extension, Kind kind) {}

    public record Size(int width, int height) {}

    static final Type JPEG = new Type("image/jpeg", ".jpg", Kind.IMAGE);
    static final Type PNG = new Type("image/png", ".png", Kind.IMAGE);
    static final Type WEBP = new Type("image/webp", ".webp", Kind.IMAGE);
    static final Type PDF = new Type("application/pdf", ".pdf", Kind.DOCUMENT);

    private ChatFiles() {}

    public static Optional<Type> detect(byte[] b) {
        if (b == null) return Optional.empty();
        if (starts(b, 0xFF, 0xD8, 0xFF)) return Optional.of(JPEG);
        if (starts(b, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) return Optional.of(PNG);
        if (b.length >= 12 && ascii(b, 0, "RIFF") && ascii(b, 8, "WEBP")) return Optional.of(WEBP);
        if (ascii(b, 0, "%PDF-")) return Optional.of(PDF);
        return Optional.empty();
    }

    /** Width and height for images, or empty for documents and unreadable headers. */
    public static Optional<Size> dimensions(byte[] b, Type type) {
        if (type.kind() != Kind.IMAGE) return Optional.empty();
        if (type == WEBP) return webp(b);
        try (var in = ImageIO.createImageInputStream(new ByteArrayInputStream(b))) {
            var readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) return Optional.empty();
            var reader = readers.next();
            try {
                reader.setInput(in, true, true);
                return Optional.of(new Size(reader.getWidth(0), reader.getHeight(0)));
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException unreadable) {
            return Optional.empty();
        }
    }

    /**
     * A safe display name: no path, no control characters, contact details masked like chat text, at most 120
     * characters, always ending in the real type's extension.
     */
    public static String fileName(String original, Type type) {
        String name = original == null ? "" : original;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        int dot = name.lastIndexOf('.');
        String base = dot >= 0 ? name.substring(0, dot) : name;
        base = ChatContentFilter.clean(base.replaceAll("[\\p{Cntrl}\"<>|:*?]", " ")).text()
                .replaceAll("\\s+", " ").strip();
        if (base.isEmpty()) base = type.kind() == Kind.IMAGE ? "photo" : "document";
        int max = 120 - type.extension().length();
        if (base.length() > max) base = base.substring(0, max).strip();
        return base + type.extension();
    }

    private static Optional<Size> webp(byte[] b) {
        if (b.length < 30) return Optional.empty();
        if (ascii(b, 12, "VP8X")) {
            return Optional.of(new Size(1 + u24(b, 24), 1 + u24(b, 27)));
        }
        if (ascii(b, 12, "VP8L") && (b[20] & 0xFF) == 0x2F) {
            int b1 = b[21] & 0xFF, b2 = b[22] & 0xFF, b3 = b[23] & 0xFF, b4 = b[24] & 0xFF;
            return Optional.of(new Size(1 + (b1 | (b2 & 0x3F) << 8), 1 + ((b2 >> 6) | b3 << 2 | (b4 & 0x0F) << 10)));
        }
        if (ascii(b, 12, "VP8 ") && (b[23] & 0xFF) == 0x9D && (b[24] & 0xFF) == 0x01 && (b[25] & 0xFF) == 0x2A) {
            return Optional.of(new Size(u16(b, 26) & 0x3FFF, u16(b, 28) & 0x3FFF));
        }
        return Optional.empty();
    }

    private static int u16(byte[] b, int at) { return (b[at] & 0xFF) | (b[at + 1] & 0xFF) << 8; }

    private static int u24(byte[] b, int at) { return u16(b, at) | (b[at + 2] & 0xFF) << 16; }

    private static boolean starts(byte[] b, int... prefix) {
        if (b.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if ((b[i] & 0xFF) != prefix[i]) return false;
        return true;
    }

    private static boolean ascii(byte[] b, int at, String text) {
        byte[] t = text.getBytes(StandardCharsets.US_ASCII);
        return b.length >= at + t.length && Arrays.equals(b, at, at + t.length, t, 0, t.length);
    }
}
