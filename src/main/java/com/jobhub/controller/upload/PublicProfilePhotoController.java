package com.jobhub.controller.upload;

import com.jobhub.service.upload.PublicProfilePhotoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/** Public (no Authorization header) image endpoint used by the profilePhoto URLs in API responses. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/public/profile-photos")
public class PublicProfilePhotoController {
    private final PublicProfilePhotoService service;

    @GetMapping("/{userId}/{file}")
    public ResponseEntity<byte[]> photo(@PathVariable Long userId, @PathVariable String file) {
        var photo = service.load(userId, file);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .header("Content-Disposition", "inline")
                .body(photo.bytes());
    }
}
