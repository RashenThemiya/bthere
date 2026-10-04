package com.jobhub.dto.upload;

public record FileUploadResponse(
        String key,
        String url,
        String contentType,
        long size
) {
}
