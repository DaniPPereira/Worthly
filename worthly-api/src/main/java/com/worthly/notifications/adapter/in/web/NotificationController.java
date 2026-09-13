package com.worthly.notifications.adapter.in.web;

import com.worthly.notifications.adapter.out.persistence.NotificationEntity;
import com.worthly.notifications.application.NotificationService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<NotificationResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return notificationService.list(UUID.fromString(jwt.getSubject())).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @PostMapping("/{notificationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID notificationId) {
        notificationService.markRead(UUID.fromString(jwt.getSubject()), notificationId);
    }

    public record NotificationResponse(UUID id, String type, Instant createdAt, Instant readAt) {
        static NotificationResponse from(NotificationEntity entity) {
            return new NotificationResponse(entity.getId(), entity.getType(), entity.getCreatedAt(), entity.getReadAt());
        }
    }
}
