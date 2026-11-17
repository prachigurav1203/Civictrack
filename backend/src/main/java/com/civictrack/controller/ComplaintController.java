package com.civictrack.controller;

import com.civictrack.dto.ComplaintForm;
import com.civictrack.dto.Dtos.*;
import com.civictrack.model.*;
import com.civictrack.service.ComplaintService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Role rules are declared in SecurityConfig; ownership rules are enforced again inside ComplaintService. */
@RestController
@RequestMapping("/api/complaints")
@RequiredArgsConstructor
public class ComplaintController {
    private static final Set<String> SORTABLE = Set.of("createdAt", "updatedAt", "priority", "status", "title", "slaDueAt");
    private final ComplaintService service;

    // ---- citizen
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ComplaintResponse create(@AuthenticationPrincipal User user, @Valid @ModelAttribute ComplaintForm form) {
        return service.create(user, form);
    }

    @PostMapping("/{id}/feedback")
    public ComplaintResponse feedback(@AuthenticationPrincipal User user, @PathVariable Long id, @Valid @RequestBody FeedbackRequest r) {
        return service.feedback(id, r.rating(), r.comment(), user);
    }

    // ---- shared (scoped by role in the service)
    @GetMapping
    public PageResponse<ComplaintResponse> list(@AuthenticationPrincipal User user,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ComplaintStatus status,
            @RequestParam(required = false) Category category,
            @RequestParam(required = false) Priority priority,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long officerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "false") boolean overdue,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        String[] s = sort.split(",");
        String field = SORTABLE.contains(s[0]) ? s[0] : "createdAt";
        Sort.Direction dir = s.length > 1 && s[1].equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50),
                Sort.by(dir, field).and(Sort.by(Sort.Direction.DESC, "id")));
        return service.list(user, search, status, category, priority, departmentId, officerId, from, to, overdue, pageable);
    }

    @GetMapping("/stats")
    public Map<String, Long> stats(@AuthenticationPrincipal User user) { return service.stats(user); }

    @GetMapping("/track/{trackingId}")
    public ComplaintResponse track(@PathVariable String trackingId) { return service.track(trackingId); }

    @GetMapping("/{id}")
    public ComplaintResponse get(@AuthenticationPrincipal User user, @PathVariable Long id) { return service.get(user, id); }

    // ---- admin
    @PostMapping("/{id}/review")
    public ComplaintResponse review(@AuthenticationPrincipal User user, @PathVariable Long id, @Valid @RequestBody(required = false) RemarksRequest r) {
        return service.review(id, r == null ? null : r.remarks(), user);
    }

    @PostMapping("/{id}/assign")
    public ComplaintResponse assign(@AuthenticationPrincipal User user, @PathVariable Long id, @Valid @RequestBody AssignRequest r) {
        return service.assign(id, r.departmentId(), r.officerId(), r.remarks(), user);
    }

    @PostMapping("/{id}/reject")
    public ComplaintResponse reject(@AuthenticationPrincipal User user, @PathVariable Long id, @Valid @RequestBody RemarksRequest r) {
        return service.rejectComplaint(id, r.remarks(), user);
    }

    @PostMapping("/{id}/reopen")
    public ComplaintResponse reopen(@AuthenticationPrincipal User user, @PathVariable Long id, @Valid @RequestBody RemarksRequest r) {
        return service.reopen(id, r.remarks(), user);
    }

    @PostMapping("/{id}/priority")
    public ComplaintResponse priority(@AuthenticationPrincipal User user, @PathVariable Long id, @Valid @RequestBody PriorityRequest r) {
        return service.setPriority(id, r.priority(), r.remarks(), user);
    }

    @PostMapping("/{id}/verify")
    public ComplaintResponse verify(@AuthenticationPrincipal User user, @PathVariable Long id, @Valid @RequestBody VerifyRequest r) {
        return service.verify(id, r.approve(), r.remarks(), user);
    }

    @GetMapping("/{id}/duplicates")
    public List<DuplicateResponse> duplicates(@PathVariable Long id) { return service.duplicates(id); }

    // ---- officer
    @PostMapping("/{id}/accept")
    public ComplaintResponse accept(@AuthenticationPrincipal User user, @PathVariable Long id) { return service.accept(id, user); }

    @PostMapping("/{id}/start")
    public ComplaintResponse start(@AuthenticationPrincipal User user, @PathVariable Long id, @Valid @RequestBody(required = false) RemarksRequest r) {
        return service.start(id, r == null ? null : r.remarks(), user);
    }

    @PostMapping("/{id}/progress")
    public ComplaintResponse progress(@AuthenticationPrincipal User user, @PathVariable Long id, @Valid @RequestBody RemarksRequest r) {
        return service.progress(id, r.remarks(), user);
    }

    @PostMapping(value = "/{id}/resolution", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ComplaintResponse resolution(@AuthenticationPrincipal User user, @PathVariable Long id,
            @RequestParam("remarks") String remarks, @RequestParam(value = "photos", required = false) List<MultipartFile> photos) {
        return service.submitResolution(id, remarks, photos, user);
    }
}
