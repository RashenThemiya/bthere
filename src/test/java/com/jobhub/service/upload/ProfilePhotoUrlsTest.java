package com.jobhub.service.upload;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProfilePhotoUrlsTest {
    private static final String FILE = "3f1c2a9e-1b2c-4d5e-8f90-123456789abc.jpg";
    private static final String KEY = "providers/21/images/" + FILE;
    private static final String PUBLIC = "https://api.test/api/v1/public/profile-photos/21/" + FILE;

    private final ProfilePhotoUrls urls = new ProfilePhotoUrls(
            "jobhube", "eu-north-1", "https://jobhube.s3.eu-north-1.amazonaws.com/", "https://api.test/");

    @Test
    void previouslySavedDirectS3UrlsBecomePublicProxyUrls() {
        assertThat(urls.publicUrl("https://jobhube.s3.eu-north-1.amazonaws.com/" + KEY)).isEqualTo(PUBLIC);
        assertThat(urls.publicUrl("https://jobhube.s3.amazonaws.com/" + KEY)).isEqualTo(PUBLIC);
        assertThat(urls.publicUrl("https://s3.eu-north-1.amazonaws.com/jobhube/" + KEY)).isEqualTo(PUBLIC);
        assertThat(urls.publicUrl("https://jobhube.s3.eu-north-1.amazonaws.com/" + KEY + "?X-Amz-Expires=600")).isEqualTo(PUBLIC);
    }

    @Test
    void bareKeysAndProxyUrlsAreNormalised() {
        assertThat(urls.publicUrl(KEY)).isEqualTo(PUBLIC);
        assertThat(urls.publicUrl(PUBLIC)).isEqualTo(PUBLIC);
        assertThat(urls.publicUrl("/api/v1/public/profile-photos/21/" + FILE)).isEqualTo(PUBLIC);
        assertThat(urls.key(PUBLIC)).isEqualTo(KEY);
    }

    @Test
    void otherValuesPassThroughUnchanged() {
        assertThat(urls.publicUrl(null)).isNull();
        assertThat(urls.publicUrl("")).isEmpty();
        assertThat(urls.publicUrl("https://lh3.googleusercontent.com/a/photo.jpg")).isEqualTo("https://lh3.googleusercontent.com/a/photo.jpg");
        String icon = "https://jobhube.s3.eu-north-1.amazonaws.com/service-icons/" + FILE;
        assertThat(urls.publicUrl(icon)).isEqualTo(icon);
    }

    @Test
    void privateDocumentsAndOtherBucketsAreNeverRewritten() {
        String document = "https://jobhube.s3.eu-north-1.amazonaws.com/providers/21/documents/" + FILE;
        assertThat(urls.publicUrl(document)).isEqualTo(document);
        assertThat(urls.key("customers/21/documents/" + FILE)).isNull();
        assertThat(urls.key("https://other.s3.eu-north-1.amazonaws.com/" + KEY)).isNull();
        assertThat(urls.key("https://evil.example/api/v1/public/profile-photos/21/" + FILE)).isNull();
        assertThat(urls.key("providers/21/images/../documents/" + FILE)).isNull();
    }
}
