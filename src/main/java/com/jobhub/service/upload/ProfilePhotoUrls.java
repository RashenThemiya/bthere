package com.jobhub.service.upload;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Profile photos are uploaded under the private {@code providers/<userId>/images/} prefix, so their stored S3 URL
 * returns 403 without credentials. This turns any stored form (direct S3 URL, bare key or an earlier proxy URL)
 * into the public {@code /api/v1/public/profile-photos/<userId>/<file>} URL at response time, so photos saved
 * before this change load as well. Values that are not our own upload (for example a Google photo URL) pass through.
 */
@Component
public class ProfilePhotoUrls {
    public static final String PATH = "/api/v1/public/profile-photos/";
    private static final Pattern KEY = Pattern.compile(
            "providers/([1-9][0-9]{0,18})/images/([A-Za-z0-9_-]{1,100}\\.(?:jpg|png|webp))");
    private static final Pattern PROXY_PATH = Pattern.compile("([1-9][0-9]{0,18})/([A-Za-z0-9_-]{1,100}\\.(?:jpg|png|webp))");

    private final String bucket;
    private final String region;
    private final String storageBaseUrl;
    private final String apiBaseUrl;

    public ProfilePhotoUrls(
            @Value("${app.storage.s3.bucket:}") String bucket,
            @Value("${app.storage.s3.region:}") String region,
            @Value("${app.storage.s3.public-base-url:}") String storageBaseUrl,
            @Value("${app.public-base-url:}") String apiBaseUrl) {
        this.bucket = bucket == null ? "" : bucket.trim();
        this.region = region == null ? "" : region.trim();
        this.storageBaseUrl = trimSlash(storageBaseUrl);
        this.apiBaseUrl = trimSlash(apiBaseUrl);
    }

    /** The URL clients should load; unchanged when the value is not one of our uploaded profile images. */
    public String publicUrl(String stored) {
        String key = key(stored);
        if (key == null) return stored;
        Matcher m = KEY.matcher(key);
        if (!m.matches()) return stored;
        return apiBase() + PATH + m.group(1) + "/" + m.group(2);
    }

    /** The S3 key of an uploaded profile image in any stored form, or null if it is not one. */
    public String key(String stored) {
        if (stored == null || stored.isBlank()) return null;
        String value = stored.trim();
        int cut = indexOfAny(value, '?', '#');
        if (cut >= 0) value = value.substring(0, cut);

        String path = null;
        for (String prefix : storagePrefixes()) {
            if (value.regionMatches(true, 0, prefix, 0, prefix.length())) {
                path = value.substring(prefix.length());
                break;
            }
        }
        if (path == null) {
            String proxy = proxyRemainder(value);
            if (proxy != null) {
                Matcher m = PROXY_PATH.matcher(proxy);
                path = m.matches() ? "providers/" + m.group(1) + "/images/" + m.group(2) : null;
            } else if (!value.contains("://")) {
                path = value.startsWith("/") ? value.substring(1) : value;
            }
        }
        return path != null && KEY.matcher(path).matches() ? path : null;
    }

    private String proxyRemainder(String value) {
        if (value.startsWith(PATH)) return value.substring(PATH.length());
        String base = apiBase();
        if (!base.isEmpty() && value.regionMatches(true, 0, base + PATH, 0, base.length() + PATH.length())) {
            return value.substring(base.length() + PATH.length());
        }
        return null;
    }

    private List<String> storagePrefixes() {
        var prefixes = new java.util.ArrayList<String>();
        if (!storageBaseUrl.isEmpty()) prefixes.add(storageBaseUrl + "/");
        if (!bucket.isEmpty()) {
            String b = bucket.toLowerCase(Locale.ROOT);
            if (!region.isEmpty()) {
                prefixes.add("https://" + b + ".s3." + region + ".amazonaws.com/");
                prefixes.add("https://s3." + region + ".amazonaws.com/" + b + "/");
            }
            prefixes.add("https://" + b + ".s3.amazonaws.com/");
        }
        return prefixes;
    }

    private String apiBase() {
        if (!apiBaseUrl.isEmpty()) return apiBaseUrl;
        if (RequestContextHolder.getRequestAttributes() == null) return "";
        return trimSlash(ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString());
    }

    private static int indexOfAny(String value, char a, char b) {
        int i = value.indexOf(a), j = value.indexOf(b);
        return i < 0 ? j : j < 0 ? i : Math.min(i, j);
    }

    private static String trimSlash(String value) {
        return value == null ? "" : value.trim().replaceAll("/+$", "");
    }
}
