package com.jobhub.service.upload;

import com.jobhub.dto.upload.FileUploadResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

@Service
@RequiredArgsConstructor
public class S3ImageUploadService {

    private static final long MAX_IMAGE_BYTES = 10L * 1024L * 1024L;
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "image/svg+xml", ".svg"
    );

    private final S3Client s3Client;

    @Value("${app.storage.s3.bucket}")
    private String bucket;

    @Value("${app.storage.s3.region}")
    private String region;

    @Value("${app.storage.s3.public-base-url:}")
    private String publicBaseUrl;

    public FileUploadResponse uploadImage(String keyPrefix, MultipartFile file) {
        validate(keyPrefix, file);
        String contentType = file.getContentType().toLowerCase();
        String key = keyPrefix + "/" + UUID.randomUUID() + EXTENSIONS.get(contentType);

        byte[] imageBytes;
        try {
            imageBytes = file.getBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read the uploaded image", exception);
        }
        if (!matchesContentType(imageBytes, contentType)) {
            throw new IllegalArgumentException(
                    "Image content does not match its JPG, PNG, WebP or SVG content type");
        }

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(requireBucket())
                .key(key)
                .contentType(contentType)
                .contentLength(file.getSize())
                .build();
        s3Client.putObject(request, RequestBody.fromBytes(imageBytes));

        return new FileUploadResponse(
                key,
                buildUrl(key),
                contentType,
                file.getSize()
        );
    }

    private void validate(String keyPrefix, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Image file is required");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "Image must not exceed 10 MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !EXTENSIONS.containsKey(contentType.toLowerCase())) {
            throw new IllegalArgumentException("Only JPG, PNG, WebP and SVG images are allowed");
        }
        if ("image/svg+xml".equalsIgnoreCase(contentType)
                && !"service-icons".equals(keyPrefix)) {
            throw new IllegalArgumentException("SVG is allowed only for admin-managed service assets");
        }
    }

    private boolean matchesContentType(byte[] bytes, String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> bytes.length >= 3
                    && (bytes[0] & 0xff) == 0xff
                    && (bytes[1] & 0xff) == 0xd8
                    && (bytes[2] & 0xff) == 0xff;
            case "image/png" -> bytes.length >= 8
                    && (bytes[0] & 0xff) == 0x89
                    && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47
                    && bytes[4] == 0x0d && bytes[5] == 0x0a
                    && bytes[6] == 0x1a && bytes[7] == 0x0a;
            case "image/webp" -> bytes.length >= 12
                    && bytes[0] == 'R' && bytes[1] == 'I'
                    && bytes[2] == 'F' && bytes[3] == 'F'
                    && bytes[8] == 'W' && bytes[9] == 'E'
                    && bytes[10] == 'B' && bytes[11] == 'P';
            case "image/svg+xml" -> isSafeSvg(bytes);
            default -> false;
        };
    }

    private boolean isSafeSvg(byte[] bytes) {
        try {
            String source = new String(bytes, StandardCharsets.UTF_8);
            String lower = source.toLowerCase(java.util.Locale.ROOT);
            if (lower.contains("<!doctype") || lower.contains("<!entity")) return false;

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            Element root = factory.newDocumentBuilder()
                    .parse(new java.io.ByteArrayInputStream(bytes)).getDocumentElement();
            String rootName = root.getLocalName() == null ? root.getNodeName() : root.getLocalName();
            return "svg".equalsIgnoreCase(rootName) && safeSvgElement(root);
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean safeSvgElement(Element element) {
        String name = element.getLocalName() == null
                ? element.getNodeName() : element.getLocalName();
        name = name.toLowerCase(java.util.Locale.ROOT);
        if (java.util.Set.of("script", "foreignobject", "iframe", "object", "embed", "style")
                .contains(name)) return false;
        NamedNodeMap attributes = element.getAttributes();
        for (int index = 0; index < attributes.getLength(); index++) {
            Node attribute = attributes.item(index);
            String attributeName = attribute.getNodeName().toLowerCase(java.util.Locale.ROOT);
            String value = attribute.getNodeValue().trim().toLowerCase(java.util.Locale.ROOT);
            if (attributeName.startsWith("on") || value.startsWith("javascript:")
                    || value.startsWith("data:text/html") || value.contains("url(")) return false;
            if ((attributeName.endsWith("href") || "src".equals(attributeName))
                    && !value.isBlank() && !value.startsWith("#")) return false;
        }
        NodeList children = element.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element childElement && !safeSvgElement(childElement)) return false;
        }
        return true;
    }

    private String requireBucket() {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("AWS_S3_BUCKET is not configured");
        }
        return bucket;
    }

    private String buildUrl(String key) {
        String encodedKey = URLEncoder.encode(key, StandardCharsets.UTF_8)
                .replace("%2F", "/");
        if (publicBaseUrl != null && !publicBaseUrl.isBlank()) {
            return publicBaseUrl.replaceAll("/+$", "") + "/" + encodedKey;
        }
        return "https://" + bucket + ".s3." + region
                + ".amazonaws.com/" + encodedKey;
    }
}
