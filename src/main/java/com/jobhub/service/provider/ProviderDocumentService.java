package com.jobhub.service.provider;

import com.jobhub.dto.provider.CreateDocumentTypeRequest;
import com.jobhub.dto.provider.DocumentReviewRequest;
import com.jobhub.dto.provider.DocumentTypeResponse;
import com.jobhub.dto.provider.ProviderDocumentRequest;
import com.jobhub.dto.provider.ProviderDocumentResponse;
import com.jobhub.entity.provider.DocumentType;
import com.jobhub.entity.provider.ServiceProvider;
import com.jobhub.entity.provider.ServiceProviderDocument;
import com.jobhub.exception.ConflictException;
import com.jobhub.exception.ResourceNotFoundException;
import com.jobhub.repository.provider.DocumentTypeRepository;
import com.jobhub.repository.provider.ServiceProviderDocumentRepository;
import com.jobhub.repository.provider.ServiceProviderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
@RequiredArgsConstructor
public class ProviderDocumentService {

    private static final Set<String> REVIEW_STATUSES = Set.of(
            "PENDING",
            "APPROVED",
            "REJECTED"
    );

    private final ProviderProfileService providerProfileService;
    private final ServiceProviderRepository serviceProviderRepository;
    private final DocumentTypeRepository documentTypeRepository;
    private final ServiceProviderDocumentRepository documentRepository;
    private final ProviderServiceApprovalService approvalService;

    @Transactional(readOnly = true)
    public List<DocumentTypeResponse> listDocumentTypes() {
        return documentTypeRepository.findAllByStatusOrderByNameAsc("ACTIVE")
                .stream()
                .map(this::toTypeResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DocumentTypeResponse> listAllDocumentTypes() {
        return documentTypeRepository.findAllByOrderByNameAsc()
                .stream()
                .map(this::toTypeResponse)
                .toList();
    }

    @Transactional
    public DocumentTypeResponse createDocumentType(CreateDocumentTypeRequest request) {
        String name = request.name().trim();
        if (documentTypeRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("Document type already exists");
        }

        DocumentType type = new DocumentType();
        type.setName(name);
        type.setDescription(optional(request.description()));
        type.setHasExpiry(request.hasExpiry());
        type.setStatus("ACTIVE");
        return toTypeResponse(documentTypeRepository.save(type));
    }

    @Transactional
    public DocumentTypeResponse updateDocumentType(
            Long documentTypeId,
            CreateDocumentTypeRequest request
    ) {
        DocumentType type = requireDocumentType(documentTypeId);
        String name = request.name().trim();
        if (documentTypeRepository.existsByNameIgnoreCaseAndDocumentTypeIdNot(
                name, documentTypeId)) {
            throw new ConflictException("Document type already exists");
        }
        type.setName(name);
        type.setDescription(optional(request.description()));
        type.setHasExpiry(request.hasExpiry());
        return toTypeResponse(documentTypeRepository.save(type));
    }

    @Transactional
    public DocumentTypeResponse updateDocumentTypeStatus(
            Long documentTypeId,
            String requestedStatus
    ) {
        String status = requestedStatus.trim().toUpperCase();
        if (!Set.of("ACTIVE", "INACTIVE").contains(status)) {
            throw new IllegalArgumentException("Status must be ACTIVE or INACTIVE");
        }
        DocumentType type = requireDocumentType(documentTypeId);
        type.setStatus(status);
        return toTypeResponse(documentTypeRepository.save(type));
    }

    @Transactional(readOnly = true)
    public List<ProviderDocumentResponse> getProviderDocuments(Long userId) {
        ServiceProvider provider = providerProfileService.findByUserId(userId);
        return toResponses(documentRepository
                .findAllByServiceProviderIdOrderByCreatedAtDesc(
                        provider.getServiceProviderId()
                ));
    }

    @Transactional
    public ProviderDocumentResponse addDocument(
            Long userId,
            ProviderDocumentRequest request
    ) {
        String expectedPrefix = "providers/" + userId + "/documents/";
        if (!request.documentKey().startsWith(expectedPrefix)) {
            throw new IllegalArgumentException(
                    "Document key does not belong to this provider"
            );
        }
        ServiceProvider provider = providerProfileService.findByUserId(userId);
        DocumentType type = documentTypeRepository.findById(request.documentTypeId())
                .filter(value -> "ACTIVE".equals(value.getStatus()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Document type was not found or is inactive"
                ));

        if (type.isHasExpiry() && request.expiryDate() == null) {
            throw new IllegalArgumentException("Expiry date is required for this document type");
        }
        if (request.issuedDate() != null
                && request.expiryDate() != null
                && request.expiryDate().isBefore(request.issuedDate())) {
            throw new IllegalArgumentException("Expiry date cannot be before issued date");
        }

        ServiceProviderDocument document = documentRepository
                .findByServiceProviderIdAndDocumentTypeId(
                        provider.getServiceProviderId(),
                        type.getDocumentTypeId()
                )
                .orElseGet(ServiceProviderDocument::new);
        document.setServiceProviderId(provider.getServiceProviderId());
        document.setDocumentTypeId(type.getDocumentTypeId());
        document.setDocumentName(request.documentName().trim());
        document.setDocumentNumber(optional(request.documentNumber()));
        document.setDocumentUrl(request.documentKey().trim());
        document.setIssuedDate(request.issuedDate());
        document.setExpiryDate(request.expiryDate());
        document.setVerificationStatus("PENDING");
        document.setVerifiedBy(null);
        document.setVerifiedAt(null);
        document.setRejectionReason(null);
        provider.setVerificationStatus("PENDING");
        serviceProviderRepository.save(provider);
        ServiceProviderDocument saved = documentRepository.save(document);
        approvalService.refreshProviderApprovals(provider.getServiceProviderId());
        return toResponse(saved, type.getName());
    }

    @Transactional
    public void deleteDocument(Long userId, Long documentId) {
        ServiceProvider provider = providerProfileService.findByUserId(userId);
        ServiceProviderDocument document = documentRepository
                .findByDocumentIdAndServiceProviderId(
                        documentId,
                        provider.getServiceProviderId()
                )
                .orElseThrow(() -> new ResourceNotFoundException("Document was not found"));
        if ("APPROVED".equals(document.getVerificationStatus())) {
            throw new ConflictException("Approved documents cannot be deleted");
        }
        documentRepository.delete(document);
        approvalService.refreshProviderApprovals(provider.getServiceProviderId());
    }

    @Transactional(readOnly = true)
    public Page<ProviderDocumentResponse> listForReview(String status, Pageable pageable) {
        String normalized = status.trim().toUpperCase();
        if (!REVIEW_STATUSES.contains(normalized)) {
            throw new IllegalArgumentException(
                    "Document status must be PENDING, APPROVED or REJECTED"
            );
        }
        return documentRepository.findAllByVerificationStatus(normalized, pageable)
                .map(document -> {
                    DocumentType type = documentTypeRepository
                            .findById(document.getDocumentTypeId()).orElse(null);
                    return toResponse(document, type == null ? null : type.getName());
                });
    }

    @Transactional
    public ProviderDocumentResponse review(
            Long documentId,
            Long reviewerId,
            DocumentReviewRequest request
    ) {
        ServiceProviderDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document was not found"));
        if ("REJECTED".equals(request.status())
                && (request.rejectionReason() == null
                || request.rejectionReason().isBlank())) {
            throw new IllegalArgumentException("Rejection reason is required");
        }

        document.setVerificationStatus(request.status());
        document.setVerifiedBy(reviewerId);
        document.setVerifiedAt(LocalDateTime.now());
        document.setRejectionReason(
                "REJECTED".equals(request.status())
                        ? request.rejectionReason().trim()
                        : null
        );
        DocumentType type = documentTypeRepository.findById(document.getDocumentTypeId())
                .orElse(null);
        ServiceProviderDocument saved = documentRepository.save(document);
        approvalService.refreshProviderApprovals(document.getServiceProviderId());
        return toResponse(
                saved,
                type == null ? null : type.getName()
        );
    }

    private List<ProviderDocumentResponse> toResponses(
            List<ServiceProviderDocument> documents
    ) {
        Map<Long, String> typeNames = new HashMap<>();
        documentTypeRepository.findAllById(documents.stream()
                        .map(ServiceProviderDocument::getDocumentTypeId)
                        .distinct()
                        .toList())
                .forEach(type -> typeNames.put(type.getDocumentTypeId(), type.getName()));
        return documents.stream()
                .map(document -> toResponse(
                        document,
                        typeNames.get(document.getDocumentTypeId())
                ))
                .toList();
    }

    private ProviderDocumentResponse toResponse(
            ServiceProviderDocument document,
            String typeName
    ) {
        return new ProviderDocumentResponse(
                document.getDocumentId(),
                document.getServiceProviderId(),
                document.getDocumentTypeId(),
                typeName,
                document.getDocumentName(),
                document.getDocumentNumber(),
                normalizeStoredDocumentKey(document.getDocumentUrl()),
                document.getIssuedDate(),
                document.getExpiryDate(),
                document.getVerificationStatus(),
                document.getVerifiedBy(),
                document.getVerifiedAt(),
                document.getRejectionReason(),
                document.getCreatedAt()
        );
    }

    private DocumentTypeResponse toTypeResponse(DocumentType type) {
        return new DocumentTypeResponse(
                type.getDocumentTypeId(),
                type.getName(),
                type.getDescription(),
                type.isHasExpiry(),
                type.getStatus()
        );
    }

    private DocumentType requireDocumentType(Long documentTypeId) {
        return documentTypeRepository.findById(documentTypeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Document type was not found"));
    }

    private String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeStoredDocumentKey(String value) {
        if (value == null || !value.startsWith("s3://")) {
            return value;
        }
        int keyStart = value.indexOf('/', "s3://".length());
        return keyStart < 0 ? value : value.substring(keyStart + 1);
    }
}
