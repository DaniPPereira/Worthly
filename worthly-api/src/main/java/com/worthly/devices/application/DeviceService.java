package com.worthly.devices.application;

import com.worthly.audit.application.AuditService;
import com.worthly.devices.adapter.out.persistence.DeviceSessionEntity;
import com.worthly.devices.adapter.out.persistence.DeviceSessionRepository;
import com.worthly.devices.adapter.out.persistence.RefreshTokenRepository;
import com.worthly.shared.web.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceService {

    private final DeviceSessionRepository devices;
    private final RefreshTokenRepository refreshTokens;
    private final AuditService auditService;

    public DeviceService(
            DeviceSessionRepository devices,
            RefreshTokenRepository refreshTokens,
            AuditService auditService) {
        this.devices = devices;
        this.refreshTokens = refreshTokens;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<DeviceSessionEntity> list(UUID ownerId) {
        return devices.findByUserIdOrderByLastSeenAtDesc(ownerId);
    }

    @Transactional
    public void revoke(UUID ownerId, UUID deviceId) {
        DeviceSessionEntity session = devices.findById(deviceId)
                .orElseThrow(() -> ApiException.of(HttpStatus.NOT_FOUND, "not_found"));
        if (!session.getUserId().equals(ownerId)) {
            throw ApiException.of(HttpStatus.NOT_FOUND, "not_found");
        }
        Instant now = Instant.now();
        session.setRevokedAt(now);
        refreshTokens.revokeFamily(session.getRefreshFamilyId(), now);
        auditService.record(ownerId, "DEVICE_REVOKE", Map.of("deviceId", deviceId.toString()));
    }
}
