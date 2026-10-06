package com.jobhub.controller.upload;

import com.jobhub.dto.upload.FileUploadResponse;
import com.jobhub.dto.upload.PresignedUrlResponse;
import com.jobhub.security.AuthenticatedUser;
import com.jobhub.service.upload.S3ImageUploadService;
import com.jobhub.service.upload.S3PrivateDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/uploads")
@RequiredArgsConstructor
public class FileUploadController {

    private final S3ImageUploadService imageUploadService;
    private final S3PrivateDocumentService documentService;

    @PostMapping(
            value = "/images",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<FileUploadResponse> uploadImage(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestPart("file") MultipartFile file
    ) {
        return ResponseEntity.ok(
                imageUploadService.uploadProviderImage(user.id(), file)
        );
    }

    @PostMapping(
            value = "/documents",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<FileUploadResponse> uploadDocument(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestPart("file") MultipartFile file
    ) {
        return ResponseEntity.ok(documentService.upload(user.id(), file));
    }

    @GetMapping("/view-url")
    public ResponseEntity<PresignedUrlResponse> createViewUrl(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam String key
    ) {
        return ResponseEntity.ok(documentService.createViewUrl(user, key));
    }
}
