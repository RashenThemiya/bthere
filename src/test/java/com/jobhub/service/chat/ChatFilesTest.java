package com.jobhub.service.chat;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ChatFilesTest {
    private static byte[] image(String format, int w, int h) throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB), format, out);
        return out.toByteArray();
    }

    /** A 40-byte WebP header with the given chunk name; pairs of (offset, value) fill the rest. */
    private static byte[] webp(String chunk, int... body) {
        byte[] b = new byte[40];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, b, 0, 4);
        System.arraycopy("WEBP".getBytes(StandardCharsets.US_ASCII), 0, b, 8, 4);
        System.arraycopy(chunk.getBytes(StandardCharsets.US_ASCII), 0, b, 12, 4);
        for (int i = 0; i < body.length; i += 2) b[body[i]] = (byte) body[i + 1];
        return b;
    }

    @Test
    void typeComesFromTheFileBytes() throws Exception {
        assertThat(ChatFiles.detect(image("png", 3, 3))).contains(ChatFiles.PNG);
        assertThat(ChatFiles.detect(image("jpg", 3, 3))).contains(ChatFiles.JPEG);
        assertThat(ChatFiles.detect("%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII))).contains(ChatFiles.PDF);
        assertThat(ChatFiles.detect(webp("VP8X"))).contains(ChatFiles.WEBP);
        assertThat(ChatFiles.detect("GIF89a".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(ChatFiles.detect("<html><script>".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(ChatFiles.detect(new byte[]{'M', 'Z', (byte) 0x90, 0})).isEmpty();
        assertThat(ChatFiles.detect(new byte[0])).isEmpty();
    }

    @Test
    void imageDimensionsAreReadFromTheHeader() throws Exception {
        assertThat(ChatFiles.dimensions(image("png", 37, 21), ChatFiles.PNG)).contains(new ChatFiles.Size(37, 21));
        assertThat(ChatFiles.dimensions(image("jpg", 64, 48), ChatFiles.JPEG)).contains(new ChatFiles.Size(64, 48));
        // VP8X stores width-1 and height-1 as 24-bit little endian: 1199 = 0x04AF, 799 = 0x031F
        assertThat(ChatFiles.dimensions(webp("VP8X", 24, 0xAF, 25, 0x04, 27, 0x1F, 28, 0x03), ChatFiles.WEBP))
                .contains(new ChatFiles.Size(1200, 800));
        // VP8 (lossy) key frame: start code 9d 01 2a, then 14-bit width 640 = 0x0280 and height 480 = 0x01E0
        assertThat(ChatFiles.dimensions(webp("VP8 ", 23, 0x9D, 24, 0x01, 25, 0x2A, 26, 0x80, 27, 0x02, 28, 0xE0, 29, 0x01),
                ChatFiles.WEBP)).contains(new ChatFiles.Size(640, 480));
        // VP8L (lossless): signature 0x2f, then 14-bit width-1 and height-1 packed: 100 x 50
        int w = 99, h = 49;
        int bits = w | h << 14;
        assertThat(ChatFiles.dimensions(webp("VP8L", 20, 0x2F, 21, bits & 0xFF, 22, bits >> 8 & 0xFF, 23, bits >> 16 & 0xFF,
                24, bits >> 24 & 0xFF), ChatFiles.WEBP)).contains(new ChatFiles.Size(100, 50));
        assertThat(ChatFiles.dimensions("%PDF-1.7".getBytes(StandardCharsets.US_ASCII), ChatFiles.PDF)).isEmpty();
        assertThat(ChatFiles.dimensions(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0}, ChatFiles.JPEG)).isEmpty();
    }

    @Test
    void fileNamesAreCleanedMaskedAndGetTheRealExtension() {
        assertThat(ChatFiles.fileName("C:\\Users\\me\\Invoice Nov.pdf", ChatFiles.PDF)).isEqualTo("Invoice Nov.pdf");
        assertThat(ChatFiles.fileName("../../etc/passwd", ChatFiles.PDF)).isEqualTo("passwd.pdf");
        assertThat(ChatFiles.fileName("call 0771234567.exe", ChatFiles.PNG)).isEqualTo("call [hidden].png");
        assertThat(ChatFiles.fileName("a\u0000b<c>.jpg", ChatFiles.JPEG)).isEqualTo("a b c.jpg");
        assertThat(ChatFiles.fileName(null, ChatFiles.JPEG)).isEqualTo("photo.jpg");
        assertThat(ChatFiles.fileName(".pdf", ChatFiles.PDF)).isEqualTo("document.pdf");
        assertThat(ChatFiles.fileName("x".repeat(300) + ".pdf", ChatFiles.PDF)).hasSize(120).endsWith(".pdf");
    }
}
