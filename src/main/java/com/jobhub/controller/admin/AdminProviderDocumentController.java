package com.jobhub.controller.admin;

import com.jobhub.dto.provider.CreateDocumentTypeRequest;
import com.jobhub.dto.provider.DocumentReviewRequest;
import com.jobhub.dto.provider.DocumentTypeResponse;
import com.jobhub.dto.provider.ProviderDocumentResponse;
import com.jobhub.security.AuthenticatedUser;
import com.jobhub.service.provider.ProviderDocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'VERIFICATION_ADMIN')")
public class AdminProviderDocumentController {

    private final ProviderDocumentService providerDocumentService;

    @PostMapping("/document-types")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<DocumentTypeResponse> createDocumentType(
            @Valid @RequestBody CreateDocumentTypeRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(providerDocumentService.createDocumentType(request));
    }

    @GetMapping("/provider-documents")
    public ResponseEntity<Page<ProviderDocumentResponse>> listDocuments(
            @RequestParam(defaultValue = "PENDING") String status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(providerDocumentService.listForReview(status, pageable));
    }

    @PatchMapping("/provider-documents/{documentId}/review")
    public ResponseEntity<ProviderDocumentResponse> reviewDocument(
            @AuthenticationPrincipal AuthenticatedUser reviewer,
            @PathVariable Long documentId,
            @Valid @RequestBody DocumentReviewRequest request
    ) {
        return ResponseEntity.ok(providerDocumentService.review(
                documentId,
                reviewer.id(),
                request
        ));
    }
}
