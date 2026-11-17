package com.civictrack.service;

import com.civictrack.dto.Dtos.*;
import com.civictrack.exception.ApiException;
import com.civictrack.model.*;
import com.civictrack.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository repo;
    private final UserRepository users;

    /** Joins the caller's transaction (or starts one). */
    @Transactional
    public void notify(User user, Complaint c, NotificationType type, String message) {
        if (user == null) return;
        Notification n = new Notification();
        n.setUser(users.getReferenceById(user.getId()));
        n.setComplaint(c);
        n.setType(type);
        n.setMessage(message.length() > 500 ? message.substring(0, 497) + "..." : message);
        repo.save(n);
    }

    @Transactional
    public void notifyAdmins(Complaint c, NotificationType type, String message) {
        for (User admin : users.findByRoleAndActiveTrue(Role.ADMIN)) notify(admin, c, type, message);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(User u, int page, int size) {
        Page<Notification> p = repo.findByUserIdOrderByCreatedAtDesc(u.getId(),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50)));
        return new PageResponse<>(p.getContent().stream().map(this::toResponse).toList(),
                p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
    }

    @Transactional(readOnly = true)
    public long unreadCount(User u) { return repo.countByUserIdAndSeenFalse(u.getId()); }

    @Transactional
    public void markRead(User u, Long id) {
        Notification n = repo.findByIdAndUserId(id, u.getId()).orElseThrow(() -> ApiException.notFound("Notification not found"));
        n.setSeen(true);
        repo.save(n);
    }

    @Transactional
    public int markAllRead(User u) { return repo.markAllSeen(u.getId()); }

    private NotificationResponse toResponse(Notification n) {
        Complaint c = n.getComplaint();
        return new NotificationResponse(n.getId(), n.getType(), n.getMessage(),
                c == null ? null : c.getId(), c == null ? null : c.getTrackingId(), n.isSeen(), n.getCreatedAt());
    }
}
