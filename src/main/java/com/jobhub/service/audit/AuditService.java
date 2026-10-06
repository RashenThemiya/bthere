package com.jobhub.service.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobhub.entity.audit.AuditLog;
import com.jobhub.repository.audit.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditService {
    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;

    public void record(Long actorId, String action, String entityType,
                       Long entityId, Object oldValue, Object newValue) {
        AuditLog log = new AuditLog();
        log.setUserId(actorId);
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setOldValue(json(oldValue));
        log.setNewValue(json(newValue));
        repository.save(log);
    }

    private String json(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "\"unavailable\"";
        }
    }
}
