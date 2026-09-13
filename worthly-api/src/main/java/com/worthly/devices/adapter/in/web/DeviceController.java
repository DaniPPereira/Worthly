package com.worthly.devices.adapter.in.web;

import com.worthly.devices.adapter.out.persistence.DeviceSessionEntity;
import com.worthly.devices.application.DeviceService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @GetMapping
    public List<DeviceResponse> list(@AuthenticationPrincipal Jwt jwt) {
        UUID ownerId = UUID.fromString(jwt.getSubject());
        return deviceService.list(ownerId).stream().map(DeviceResponse::from).toList();
    }

    @DeleteMapping("/{deviceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID deviceId) {
        deviceService.revoke(UUID.fromString(jwt.getSubject()), deviceId);
    }

    public record DeviceResponse(UUID id, String name, String platform, Instant createdAt, Instant lastSeenAt, boolean revoked) {
        static DeviceResponse from(DeviceSessionEntity entity) {
            return new DeviceResponse(
                    entity.getId(),
                    entity.getDeviceName(),
                    entity.getPlatform(),
                    entity.getCreatedAt(),
                    entity.getLastSeenAt(),
                    entity.getRevokedAt() != null);
        }
    }
}
