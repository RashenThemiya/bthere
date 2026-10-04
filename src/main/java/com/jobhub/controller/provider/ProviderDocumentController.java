package com.jobhub.controller.provider;

import com.jobhub.dto.provider.DocumentTypeResponse;
import com.jobhub.dto.provider.ProviderDocumentRequest;
import com.jobhub.dto.provider.ProviderDocumentResponse;
import com.jobhub.security.AuthenticatedUser;
import com.jobhub.service.provider.ProviderDocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ProviderDocumentController {

    private final ProviderDocumentService providerDocumentService;

    @GetMapping("/api/v1/document-types")
    public ResponseEntity<List<DocumentTypeResponse>> listDocumentTypes() {
        return ResponseEntity.ok(providerDocumentService.listDocumentTypes());
    }

    @GetMapping("/api/v1/providers/me/documents")
    @PreAuthorize("hasRole('SERVICE_PROVIDER')")
    public ResponseEntity<List<ProviderDocumentResponse>> getDocuments(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ResponseEntity.ok(
                providerDocumentService.getProviderDocuments(user.id())
        );
    }

    @PostMapping("/api/v1/providers/me/documents")
    @PreAuthorize("hasRole('SERVICE_PROVIDER')")
    public ResponseEntity<ProviderDocumentResponse> addDocument(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ProviderDocumentRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(providerDocumentService.addDocument(user.id(), request));
    }

    @DeleteMapping("/api/v1/providers/me/documents/{documentId}")
    @PreAuthorize("hasRole('SERVICE_PROVIDER')")
    public ResponseEntity<Void> deleteDocument(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long documentId
    ) {
        providerDocumentService.deleteDocument(user.id(), documentId);
        return ResponseEntity.noContent().build();
    }
}
