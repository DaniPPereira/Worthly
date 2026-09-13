package com.worthly.audit.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.worthly.audit.adapter.out.persistence.AuditEventEntity;
import com.worthly.audit.adapter.out.persistence.AuditEventRepository;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditEventRepository repository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void record(UUID userId, String eventType, Map<String, Object> safeMetadata) {
        AuditEventEntity entity = new AuditEventEntity();
        entity.setUserId(userId);
        entity.setEventType(eventType);
        String correlation = MDC.get("correlationId");
        entity.setCorrelationId(correlation == null ? UUID.randomUUID().toString() : correlation);
        try {
            entity.setSafeMetadata(objectMapper.writeValueAsString(safeMetadata == null ? Map.of() : safeMetadata));
        } catch (JsonProcessingException ex) {
            entity.setSafeMetadata("{}");
        }
        repository.save(entity);
    }
}
