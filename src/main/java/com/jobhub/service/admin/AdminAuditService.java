package com.jobhub.service.admin;

import com.jobhub.dto.admin.AuditLogResponse;
import com.jobhub.entity.audit.AuditLog;
import com.jobhub.repository.audit.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class AdminAuditService {
    private final AuditLogRepository repository;

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> search(String action, String entityType, Long actorId, Pageable pageable) {
        Specification<AuditLog> spec = Specification.where(null);
        if (action != null && !action.isBlank()) spec = spec.and((r,q,c) ->
                c.like(c.lower(r.get("action")), "%" + action.toLowerCase() + "%"));
        if (entityType != null && !entityType.isBlank()) spec = spec.and((r,q,c) ->
                c.equal(c.upper(r.get("entityType")), entityType.toUpperCase()));
        if (actorId != null) spec = spec.and((r,q,c) -> c.equal(r.get("userId"), actorId));
        return repository.findAll(spec, pageable).map(this::response);
    }

    private AuditLogResponse response(AuditLog item) {
        return new AuditLogResponse(item.getAuditId(), item.getUserId(), item.getAction(),
                item.getEntityType(), item.getEntityId(), item.getOldValue(), item.getNewValue(),
                item.getIpAddress(), item.getCreatedAt());
    }
}
