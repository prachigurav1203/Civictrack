package com.civictrack.controller;

import com.civictrack.dto.Dtos.*;
import com.civictrack.model.User;
import com.civictrack.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** Every user only ever sees and changes their own notifications. */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService service;

    @GetMapping
    public PageResponse<NotificationResponse> list(@AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "15") int size) {
        return service.list(user, page, size);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unread(@AuthenticationPrincipal User user) { return Map.of("count", service.unreadCount(user)); }

    @PostMapping("/{id}/read")
    public Map<String, Boolean> read(@AuthenticationPrincipal User user, @PathVariable Long id) {
        service.markRead(user, id);
        return Map.of("ok", true);
    }

    @PostMapping("/read-all")
    public Map<String, Integer> readAll(@AuthenticationPrincipal User user) { return Map.of("updated", service.markAllRead(user)); }
}
