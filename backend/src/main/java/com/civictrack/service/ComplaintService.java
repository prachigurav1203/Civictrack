package com.civictrack.service;

import com.civictrack.dto.ComplaintForm;
import com.civictrack.dto.Dtos.*;
import com.civictrack.exception.ApiException;
import com.civictrack.model.*;
import com.civictrack.repository.*;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.*;

/**
 * Complaint workflow:
 * SUBMITTED -> (UNDER_REVIEW) -> ASSIGNED -> [officer accepts] -> IN_PROGRESS -> RESOLUTION_SUBMITTED
 *   -> admin approves -> RESOLVED   |   admin rejects the proof -> back to IN_PROGRESS
 * Admins may also REJECT a complaint or REOPEN a closed one. Officers can never set RESOLVED directly.
 */
@Service
@RequiredArgsConstructor
public class ComplaintService {
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RND = new SecureRandom();
    public static final Set<ComplaintStatus> CLOSED = EnumSet.of(ComplaintStatus.RESOLVED, ComplaintStatus.REJECTED);
    private static final Set<ComplaintStatus> REASSIGNABLE = EnumSet.of(ComplaintStatus.SUBMITTED, ComplaintStatus.UNDER_REVIEW,
            ComplaintStatus.ASSIGNED, ComplaintStatus.IN_PROGRESS, ComplaintStatus.REOPENED);
    private static final Set<String> STOP = Set.of("the", "and", "for", "with", "this", "that", "has", "have", "are", "was",
            "not", "from", "near", "road", "very", "been", "there", "which", "into", "our", "you", "its");

    private final ComplaintRepository complaints;
    private final StatusHistoryRepository histories;
    private final DepartmentRepository departments;
    private final UserRepository users;
    private final FileStorageService storage;
    private final PhotoRepository photos;
    private final ResolutionRepository resolutions;
    private final FeedbackRepository feedbacks;
    private final CategoryDepartmentRepository mappings;
    private final OfficerAssignmentRepository assignments;
    private final NotificationService notifier;

    public static int slaHours(Priority p) {
        return switch (p) { case CRITICAL -> 24; case HIGH -> 72; case MEDIUM -> 168; case LOW -> 336; };
    }

    // ------------------------------------------------------------------ create
    @Transactional
    public ComplaintResponse create(User citizen, ComplaintForm f) {
        List<MultipartFile> files = new ArrayList<>();
        if (f.getImages() != null) files.addAll(f.getImages());
        if (f.getImage() != null) files.add(f.getImage());
        List<String> urls = storage.storeAll(files, 5);

        Complaint c = new Complaint();
        c.setTrackingId(newTrackingId());
        c.setTitle(f.getTitle().trim());
        c.setDescription(f.getDescription().trim());
        c.setCategory(f.getCategory());
        c.setPriority(Priority.MEDIUM);
        c.setLatitude(f.getLatitude());
        c.setLongitude(f.getLongitude());
        c.setAddress(StringUtils.hasText(f.getAddress()) ? f.getAddress().trim() : null);
        c.setImagePath(urls.isEmpty() ? null : urls.get(0));
        c.setCitizen(users.getReferenceById(citizen.getId()));
        c.setStatus(ComplaintStatus.SUBMITTED);
        c.setSlaDueAt(LocalDateTime.now().plusHours(slaHours(Priority.MEDIUM)));
        Department dept = suggestedDepartment(f.getCategory());
        c.setDepartment(dept);
        complaints.save(c);
        for (String url : urls) photos.save(new ComplaintPhoto(c, null, url));

        addHistory(c, ComplaintStatus.SUBMITTED, "Complaint registered", citizen);
        notifier.notify(citizen, c, NotificationType.COMPLAINT_SUBMITTED,
                "Your complaint " + c.getTrackingId() + " was submitted successfully.");
        if (dept != null) {
            addHistory(c, ComplaintStatus.SUBMITTED, "Department automatically assigned: " + dept.getName(), null);
            notifier.notify(citizen, c, NotificationType.DEPARTMENT_ASSIGNED,
                    "Complaint " + c.getTrackingId() + " was routed to " + dept.getName() + ".");
        }
        notifier.notifyAdmins(c, NotificationType.NEW_COMPLAINT,
                "New complaint " + c.getTrackingId() + " (" + c.getCategory() + ") awaits review.");
        return toResponse(c, true, true, Role.CITIZEN);
    }

    // -------------------------------------------------------------------- read
    @Transactional(readOnly = true)
    public PageResponse<ComplaintResponse> list(User u, String search, ComplaintStatus status, Category category,
                                                Priority priority, Long departmentId, Long officerId,
                                                LocalDate from, LocalDate to, boolean overdue, Pageable pageable) {
        Specification<Complaint> spec = Specification.where(scope(u))
                .and(filters(search, status, category, priority, departmentId, officerId, from, to, overdue));
        Page<Complaint> p = complaints.findAll(spec, pageable);
        return new PageResponse<>(p.getContent().stream().map(c -> toResponse(c, false, true, u.getRole())).toList(),
                p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
    }

    @Transactional(readOnly = true)
    public ComplaintResponse get(User u, Long id) {
        Complaint c = find(id);
        checkAccess(u, c);
        return toResponse(c, true, true, u.getRole());
    }

    /** Public tracking: no citizen / staff personal details, only approved resolutions. */
    @Transactional(readOnly = true)
    public ComplaintResponse track(String trackingId) {
        Complaint c = complaints.findByTrackingId(trackingId.trim().toUpperCase())
                .orElseThrow(() -> ApiException.notFound("No complaint found with tracking ID " + trackingId));
        return toResponse(c, true, false, null);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> stats(User u) {
        Map<String, Long> m = new LinkedHashMap<>();
        long total = 0;
        for (ComplaintStatus s : ComplaintStatus.values()) {
            Specification<Complaint> spec = Specification.where(scope(u)).and((r, q, cb) -> cb.equal(r.get("status"), s));
            long n = complaints.count(spec);
            m.put(s.name(), n);
            total += n;
        }
        m.put("TOTAL", total);
        m.put("OVERDUE", complaints.count(Specification.where(scope(u)).and(overdueSpec())));
        return m;
    }

    // ------------------------------------------------------------- admin actions
    @Transactional
    public ComplaintResponse review(Long id, String remarks, User admin) {
        Complaint c = find(id);
        require(c, "Only new or reopened complaints can be moved to review", ComplaintStatus.SUBMITTED, ComplaintStatus.REOPENED);
        c.setStatus(ComplaintStatus.UNDER_REVIEW);
        complaints.save(c);
        addHistory(c, ComplaintStatus.UNDER_REVIEW, clean(remarks, "Complaint is under review"), admin);
        notifier.notify(c.getCitizen(), c, NotificationType.GENERAL, "Complaint " + c.getTrackingId() + " is now under review.");
        return toResponse(c, true, true, Role.ADMIN);
    }

    @Transactional
    public ComplaintResponse assign(Long id, Long departmentId, Long officerId, String remarks, User admin) {
        Complaint c = find(id);
        if (!REASSIGNABLE.contains(c.getStatus()))
            throw ApiException.badRequest("A complaint in status " + c.getStatus() + " cannot be (re)assigned. "
                    + (c.getStatus() == ComplaintStatus.RESOLUTION_SUBMITTED ? "Verify the submitted resolution first." : "Reopen it first."));
        Department d = departments.findById(departmentId).orElseThrow(() -> ApiException.notFound("Department not found"));
        User officer = users.findById(officerId)
                .filter(o -> o.getRole() == Role.OFFICER && o.isActive() && o.getDepartment() != null
                        && o.getDepartment().getId().equals(d.getId()))
                .orElseThrow(() -> ApiException.badRequest("Select an active officer who belongs to the selected department"));

        boolean deptChanged = c.getDepartment() == null || !c.getDepartment().getId().equals(d.getId());
        User previous = c.getAssignedOfficer();
        boolean officerChanged = previous == null || !previous.getId().equals(officer.getId());

        c.setDepartment(d);
        c.setAssignedOfficer(officer);
        c.setAcceptedAt(null);
        c.setStatus(ComplaintStatus.ASSIGNED);
        complaints.save(c);
        assignments.save(new OfficerAssignment(c, officer, d, users.getReferenceById(admin.getId())));

        String note = (previous != null && officerChanged ? "Reassigned from " + previous.getName() + " to " : "Assigned to ")
                + "officer " + officer.getName() + " (" + d.getName() + ")" + (StringUtils.hasText(remarks) ? ". " + remarks.trim() : "");
        addHistory(c, ComplaintStatus.ASSIGNED, note, admin);

        if (deptChanged) notifier.notify(c.getCitizen(), c, NotificationType.DEPARTMENT_ASSIGNED,
                "Complaint " + c.getTrackingId() + " was assigned to " + d.getName() + ".");
        notifier.notify(c.getCitizen(), c, NotificationType.OFFICER_ASSIGNED,
                "Officer " + officer.getName() + " was assigned to complaint " + c.getTrackingId() + ".");
        notifier.notify(officer, c, NotificationType.OFFICER_ASSIGNED, "New complaint assigned to you: " + c.getTrackingId() + " - " + c.getTitle());
        if (previous != null && officerChanged)
            notifier.notify(previous, c, NotificationType.GENERAL, "Complaint " + c.getTrackingId() + " was reassigned to another officer.");
        return toResponse(c, true, true, Role.ADMIN);
    }

    @Transactional
    public ComplaintResponse rejectComplaint(Long id, String remarks, User admin) {
        Complaint c = find(id);
        require(c, "Only complaints that have not been worked on can be rejected", ComplaintStatus.SUBMITTED, ComplaintStatus.UNDER_REVIEW, ComplaintStatus.REOPENED);
        if (!StringUtils.hasText(remarks)) throw ApiException.badRequest("Please give a reason for rejecting the complaint");
        c.setStatus(ComplaintStatus.REJECTED);
        c.setAssignedOfficer(null);
        c.setAcceptedAt(null);
        complaints.save(c);
        addHistory(c, ComplaintStatus.REJECTED, remarks.trim(), admin);
        notifier.notify(c.getCitizen(), c, NotificationType.COMPLAINT_REJECTED, "Complaint " + c.getTrackingId() + " was rejected: " + remarks.trim());
        return toResponse(c, true, true, Role.ADMIN);
    }

    @Transactional
    public ComplaintResponse reopen(Long id, String remarks, User admin) {
        Complaint c = find(id);
        require(c, "Only resolved or rejected complaints can be reopened", ComplaintStatus.RESOLVED, ComplaintStatus.REJECTED);
        if (!StringUtils.hasText(remarks)) throw ApiException.badRequest("Please give a reason for reopening the complaint");
        c.setStatus(ComplaintStatus.REOPENED);
        c.setResolvedAt(null);
        c.setAssignedOfficer(null);
        c.setAcceptedAt(null);
        c.setEscalatedAt(null);
        c.setSlaDueAt(LocalDateTime.now().plusHours(slaHours(c.getPriority())));
        complaints.save(c);
        addHistory(c, ComplaintStatus.REOPENED, remarks.trim(), admin);
        notifier.notify(c.getCitizen(), c, NotificationType.COMPLAINT_REOPENED, "Complaint " + c.getTrackingId() + " was reopened: " + remarks.trim());
        return toResponse(c, true, true, Role.ADMIN);
    }

    @Transactional
    public ComplaintResponse setPriority(Long id, Priority priority, String remarks, User admin) {
        Complaint c = find(id);
        if (CLOSED.contains(c.getStatus())) throw ApiException.badRequest("The priority of a closed complaint cannot be changed");
        Priority old = c.getPriority();
        c.setPriority(priority);
        c.setSlaDueAt(c.getCreatedAt().plusHours(slaHours(priority)));
        if (c.getEscalatedAt() != null && c.getSlaDueAt().isAfter(LocalDateTime.now())) c.setEscalatedAt(null);
        complaints.save(c);
        addHistory(c, c.getStatus(), "Priority changed from " + old + " to " + priority + (StringUtils.hasText(remarks) ? ". " + remarks.trim() : ""), admin);
        return toResponse(c, true, true, Role.ADMIN);
    }

    @Transactional
    public ComplaintResponse verify(Long id, boolean approve, String remarks, User admin) {
        Complaint c = find(id);
        require(c, "There is no submitted resolution waiting for verification", ComplaintStatus.RESOLUTION_SUBMITTED);
        ResolutionDetail r = resolutions.findFirstByComplaintIdAndStatusOrderBySubmittedAtDesc(id, ResolutionStatus.PENDING)
                .orElseThrow(() -> ApiException.badRequest("No pending resolution found"));
        if (!approve && !StringUtils.hasText(remarks)) throw ApiException.badRequest("Please explain why the resolution is rejected");
        r.setVerifiedBy(users.getReferenceById(admin.getId()));
        r.setVerifiedAt(LocalDateTime.now());
        r.setAdminRemarks(StringUtils.hasText(remarks) ? remarks.trim() : null);
        if (approve) {
            r.setStatus(ResolutionStatus.APPROVED);
            c.setStatus(ComplaintStatus.RESOLVED);
            c.setResolvedAt(LocalDateTime.now());
            resolutions.save(r);
            complaints.save(c);
            addHistory(c, ComplaintStatus.RESOLVED, "Resolution verified and approved" + (StringUtils.hasText(remarks) ? ": " + remarks.trim() : ""), admin);
            notifier.notify(c.getCitizen(), c, NotificationType.RESOLUTION_APPROVED, "The resolution of complaint " + c.getTrackingId() + " was verified and approved.");
            notifier.notify(c.getCitizen(), c, NotificationType.COMPLAINT_RESOLVED, "Complaint " + c.getTrackingId() + " is resolved. Please rate the service.");
            notifier.notify(c.getAssignedOfficer(), c, NotificationType.RESOLUTION_APPROVED, "Your resolution for " + c.getTrackingId() + " was approved.");
        } else {
            r.setStatus(ResolutionStatus.REJECTED);
            c.setStatus(ComplaintStatus.IN_PROGRESS);
            resolutions.save(r);
            complaints.save(c);
            addHistory(c, ComplaintStatus.IN_PROGRESS, "Resolution rejected by admin, rework required: " + remarks.trim(), admin);
            notifier.notify(c.getCitizen(), c, NotificationType.RESOLUTION_REJECTED, "The submitted resolution for " + c.getTrackingId() + " was not accepted; work continues.");
            notifier.notify(c.getAssignedOfficer(), c, NotificationType.RESOLUTION_REJECTED, "Resolution for " + c.getTrackingId() + " was rejected: " + remarks.trim());
        }
        return toResponse(c, true, true, Role.ADMIN);
    }

    @Transactional(readOnly = true)
    public List<DuplicateResponse> duplicates(Long id) {
        Complaint c = find(id);
        if (c.getLatitude() == null || c.getLongitude() == null) return List.of();
        double dLat = 0.0015;                                           // ~165 m
        double dLng = dLat / Math.max(0.2, Math.cos(Math.toRadians(c.getLatitude())));
        Set<String> mine = tokens(c.getTitle() + " " + c.getDescription());
        List<DuplicateResponse> out = new ArrayList<>();
        for (Complaint n : complaints.findNearby(id, c.getLatitude() - dLat, c.getLatitude() + dLat, c.getLongitude() - dLng, c.getLongitude() + dLng)) {
            double dist = haversine(c.getLatitude(), c.getLongitude(), n.getLatitude(), n.getLongitude());
            if (dist > 150) continue;
            double sim = jaccard(mine, tokens(n.getTitle() + " " + n.getDescription()));
            if (n.getCategory() != c.getCategory() && sim < 0.2) continue;
            out.add(new DuplicateResponse(n.getId(), n.getTrackingId(), n.getTitle(), n.getStatus(), n.getCategory(),
                    Math.round(dist), (int) Math.round(sim * 100), n.getCreatedAt()));
        }
        out.sort(Comparator.comparingLong(DuplicateResponse::distanceMeters));
        return out.size() > 10 ? out.subList(0, 10) : out;
    }

    // ----------------------------------------------------------- officer actions
    @Transactional
    public ComplaintResponse accept(Long id, User officer) {
        Complaint c = ownedByOfficer(id, officer);
        require(c, "Only newly assigned complaints can be accepted", ComplaintStatus.ASSIGNED);
        if (c.getAcceptedAt() != null) throw ApiException.conflict("You have already accepted this complaint");
        c.setAcceptedAt(LocalDateTime.now());
        complaints.save(c);
        addHistory(c, ComplaintStatus.ASSIGNED, "Officer " + officer.getName() + " accepted the complaint", officer);
        notifier.notify(c.getCitizen(), c, NotificationType.WORK_ACCEPTED, "Officer " + officer.getName() + " accepted complaint " + c.getTrackingId() + ".");
        return toResponse(c, true, true, Role.OFFICER);
    }

    @Transactional
    public ComplaintResponse start(Long id, String remarks, User officer) {
        Complaint c = ownedByOfficer(id, officer);
        require(c, "Work can only be started on an assigned complaint", ComplaintStatus.ASSIGNED);
        if (c.getAcceptedAt() == null) throw ApiException.badRequest("Accept the complaint before starting work");
        c.setStatus(ComplaintStatus.IN_PROGRESS);
        complaints.save(c);
        addHistory(c, ComplaintStatus.IN_PROGRESS, clean(remarks, "Work started"), officer);
        notifier.notify(c.getCitizen(), c, NotificationType.WORK_STARTED, "Work has started on complaint " + c.getTrackingId() + ".");
        return toResponse(c, true, true, Role.OFFICER);
    }

    @Transactional
    public ComplaintResponse progress(Long id, String remarks, User officer) {
        Complaint c = ownedByOfficer(id, officer);
        require(c, "Progress updates can only be added while the complaint is in progress", ComplaintStatus.IN_PROGRESS);
        if (!StringUtils.hasText(remarks)) throw ApiException.badRequest("Please describe the progress made");
        c.setUpdatedAt(LocalDateTime.now());
        addHistory(c, ComplaintStatus.IN_PROGRESS, remarks.trim(), officer);
        complaints.save(c);
        notifier.notify(c.getCitizen(), c, NotificationType.PROGRESS_UPDATE, "Progress update on " + c.getTrackingId() + ": " + remarks.trim());
        return toResponse(c, true, true, Role.OFFICER);
    }

    @Transactional
    public ComplaintResponse submitResolution(Long id, String remarks, List<MultipartFile> files, User officer) {
        Complaint c = ownedByOfficer(id, officer);
        require(c, "A resolution can only be submitted for a complaint that is in progress", ComplaintStatus.IN_PROGRESS);
        if (!StringUtils.hasText(remarks) || remarks.trim().length() < 10)
            throw ApiException.badRequest("Resolution remarks are required (at least 10 characters)");
        if (remarks.trim().length() > 2000) throw ApiException.badRequest("Resolution remarks are too long (max 2000 characters)");
        List<String> urls = storage.storeAll(files, 5);
        if (urls.isEmpty()) throw ApiException.badRequest("Upload at least one work-completion photo as proof");

        ResolutionDetail r = new ResolutionDetail();
        r.setComplaint(c);
        r.setSubmittedBy(users.getReferenceById(officer.getId()));
        r.setRemarks(remarks.trim());
        resolutions.save(r);
        for (String url : urls) photos.save(new ComplaintPhoto(c, r, url));

        c.setStatus(ComplaintStatus.RESOLUTION_SUBMITTED);
        complaints.save(c);
        addHistory(c, ComplaintStatus.RESOLUTION_SUBMITTED, "Resolution submitted for admin verification: " + remarks.trim(), officer);
        notifier.notify(c.getCitizen(), c, NotificationType.RESOLUTION_SUBMITTED, "The officer submitted the resolution of " + c.getTrackingId() + " for verification.");
        notifier.notifyAdmins(c, NotificationType.RESOLUTION_SUBMITTED, "Resolution submitted for " + c.getTrackingId() + " - verification required.");
        return toResponse(c, true, true, Role.OFFICER);
    }

    // ------------------------------------------------------------ citizen action
    @Transactional
    public ComplaintResponse feedback(Long id, int rating, String comment, User citizen) {
        Complaint c = find(id);
        if (!c.getCitizen().getId().equals(citizen.getId())) throw ApiException.forbidden("You do not have access to this complaint");
        if (c.getStatus() != ComplaintStatus.RESOLVED) throw ApiException.badRequest("Feedback can only be given after the complaint is resolved");
        if (feedbacks.existsByComplaintId(id)) throw ApiException.conflict("Feedback was already submitted for this complaint");
        Feedback f = new Feedback();
        f.setComplaint(c);
        f.setCitizen(users.getReferenceById(citizen.getId()));
        f.setRating(rating);
        f.setComment(StringUtils.hasText(comment) ? comment.trim() : null);
        feedbacks.save(f);
        notifier.notifyAdmins(c, NotificationType.GENERAL, "Citizen feedback received for " + c.getTrackingId() + ": " + rating + "/5");
        return toResponse(c, true, true, Role.CITIZEN);
    }

    // -------------------------------------------------------------- SLA escalation
    /** Called periodically: flags complaints past their SLA deadline once and alerts admins (and the officer). */
    @Transactional
    public int escalateOverdue() {
        List<Complaint> late = complaints.findByStatusNotInAndSlaDueAtBeforeAndEscalatedAtIsNull(CLOSED, LocalDateTime.now());
        for (Complaint c : late) {
            c.setEscalatedAt(LocalDateTime.now());
            complaints.save(c);
            addHistory(c, c.getStatus(), "SLA deadline exceeded - complaint escalated for admin attention", null);
            notifier.notifyAdmins(c, NotificationType.ESCALATION, "SLA breached: " + c.getTrackingId() + " (" + c.getPriority() + ") is overdue.");
            notifier.notify(c.getAssignedOfficer(), c, NotificationType.ESCALATION, "Complaint " + c.getTrackingId() + " has exceeded its SLA deadline.");
        }
        return late.size();
    }

    // ----------------------------------------------------------------- helpers
    public Department suggestedDepartment(Category category) {
        return mappings.findByCategory(category).map(CategoryDepartment::getDepartment).orElse(null);
    }

    private Complaint find(Long id) {
        return complaints.findById(id).orElseThrow(() -> ApiException.notFound("Complaint not found"));
    }

    private Complaint ownedByOfficer(Long id, User officer) {
        Complaint c = find(id);
        if (officer.getRole() != Role.OFFICER || c.getAssignedOfficer() == null || !c.getAssignedOfficer().getId().equals(officer.getId()))
            throw ApiException.forbidden("This complaint is not assigned to you");
        return c;
    }

    private void require(Complaint c, String message, ComplaintStatus... allowed) {
        for (ComplaintStatus s : allowed) if (c.getStatus() == s) return;
        throw ApiException.badRequest(message + " (current status: " + c.getStatus() + ")");
    }

    private static String clean(String s, String fallback) { return StringUtils.hasText(s) ? s.trim() : fallback; }

    private void addHistory(Complaint c, ComplaintStatus s, String remarks, User by) {
        histories.save(new StatusHistory(c, s, remarks, by == null ? null : users.getReferenceById(by.getId())));
    }

    private void checkAccess(User u, Complaint c) {
        boolean ok = switch (u.getRole()) {
            case ADMIN -> true;
            case CITIZEN -> c.getCitizen().getId().equals(u.getId());
            case OFFICER -> c.getAssignedOfficer() != null && c.getAssignedOfficer().getId().equals(u.getId());
        };
        if (!ok) throw ApiException.forbidden("You do not have access to this complaint");
    }

    private Specification<Complaint> scope(User u) {
        return (r, q, cb) -> switch (u.getRole()) {
            case CITIZEN -> cb.equal(r.get("citizen").get("id"), u.getId());
            case OFFICER -> cb.equal(r.get("assignedOfficer").get("id"), u.getId());
            case ADMIN -> cb.conjunction();
        };
    }

    private Specification<Complaint> overdueSpec() {
        return (r, q, cb) -> cb.and(cb.not(r.get("status").in(CLOSED)), cb.lessThan(r.<LocalDateTime>get("slaDueAt"), LocalDateTime.now()));
    }

    private Specification<Complaint> filters(String search, ComplaintStatus status, Category category, Priority priority,
                                             Long departmentId, Long officerId, LocalDate from, LocalDate to, boolean overdue) {
        return (r, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (StringUtils.hasText(search)) {
                String like = "%" + search.trim().toLowerCase() + "%";
                p.add(cb.or(cb.like(cb.lower(r.get("title")), like),
                        cb.like(cb.lower(r.get("trackingId")), like),
                        cb.like(cb.lower(r.get("address")), like),
                        cb.like(cb.lower(r.get("description")), like)));
            }
            if (status != null) p.add(cb.equal(r.get("status"), status));
            if (category != null) p.add(cb.equal(r.get("category"), category));
            if (priority != null) p.add(cb.equal(r.get("priority"), priority));
            if (departmentId != null) p.add(cb.equal(r.get("department").get("id"), departmentId));
            if (officerId != null) p.add(cb.equal(r.get("assignedOfficer").get("id"), officerId));
            if (from != null) p.add(cb.greaterThanOrEqualTo(r.<LocalDateTime>get("createdAt"), from.atStartOfDay()));
            if (to != null) p.add(cb.lessThan(r.<LocalDateTime>get("createdAt"), to.plusDays(1).atStartOfDay()));
            if (overdue) p.add(overdueSpec().toPredicate(r, q, cb));
            return cb.and(p.toArray(new Predicate[0]));
        };
    }

    private String newTrackingId() {
        String id;
        do {
            StringBuilder sb = new StringBuilder("CT" + Year.now().getValue() + "-");
            for (int i = 0; i < 6; i++) sb.append(CHARS.charAt(RND.nextInt(CHARS.length())));
            id = sb.toString();
        } while (complaints.existsByTrackingId(id));
        return id;
    }

    private static Set<String> tokens(String text) {
        Set<String> out = new HashSet<>();
        for (String t : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) if (t.length() > 2 && !STOP.contains(t)) out.add(t);
        return out;
    }

    private static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        Set<String> inter = new HashSet<>(a); inter.retainAll(b);
        Set<String> union = new HashSet<>(a); union.addAll(b);
        return (double) inter.size() / union.size();
    }

    private static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000, p1 = Math.toRadians(lat1), p2 = Math.toRadians(lat2);
        double dp = p2 - p1, dl = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * R * Math.asin(Math.sqrt(a));
    }

    /**
     * @param internal   true for logged-in views (includes citizen / staff names); false for public tracking.
     * @param viewerRole null for public tracking. Citizens and the public only see APPROVED resolutions.
     */
    private ComplaintResponse toResponse(Complaint c, boolean detail, boolean internal, Role viewerRole) {
        LocalDateTime sla = c.getSlaDueAt() != null ? c.getSlaDueAt() : (c.getCreatedAt() == null ? null : c.getCreatedAt().plusHours(slaHours(c.getPriority())));
        boolean overdue = sla != null && !CLOSED.contains(c.getStatus()) && LocalDateTime.now().isAfter(sla);
        boolean staff = viewerRole == Role.ADMIN || viewerRole == Role.OFFICER;

        List<HistoryResponse> history = List.of();
        List<ResolutionResponse> res = List.of();
        List<String> photoUrls = List.of();
        FeedbackResponse fb = null;
        if (detail) {
            history = histories.findByComplaintIdOrderByChangedAtAscIdAsc(c.getId()).stream()
                    .map(h -> new HistoryResponse(h.getStatus(), h.getRemarks(),
                            internal && h.getChangedBy() != null ? h.getChangedBy().getName() : (h.getChangedBy() == null && internal ? "System" : null),
                            internal && h.getChangedBy() != null ? h.getChangedBy().getRole().name() : null, h.getChangedAt()))
                    .toList();

            List<ComplaintPhoto> all = photos.findByComplaintIdOrderByIdAsc(c.getId());
            photoUrls = all.stream().filter(p -> p.getResolution() == null).map(ComplaintPhoto::getUrl).toList();
            if (photoUrls.isEmpty() && c.getImagePath() != null) photoUrls = List.of(c.getImagePath());

            res = resolutions.findByComplaintIdOrderBySubmittedAtAscIdAsc(c.getId()).stream()
                    .filter(r -> staff || r.getStatus() == ResolutionStatus.APPROVED)
                    .map(r -> new ResolutionResponse(r.getId(), r.getRemarks(),
                            internal ? r.getSubmittedBy().getName() : null, r.getSubmittedAt(), r.getStatus(),
                            internal && r.getVerifiedBy() != null ? r.getVerifiedBy().getName() : null, r.getVerifiedAt(), r.getAdminRemarks(),
                            all.stream().filter(p -> p.getResolution() != null && p.getResolution().getId().equals(r.getId())).map(ComplaintPhoto::getUrl).toList()))
                    .toList();

            fb = feedbacks.findByComplaintId(c.getId()).map(f -> new FeedbackResponse(f.getRating(), f.getComment(), f.getCreatedAt())).orElse(null);
        } else if (c.getImagePath() != null) {
            photoUrls = List.of(c.getImagePath());
        }

        Department d = c.getDepartment();
        User o = c.getAssignedOfficer();
        Department sug = viewerRole == Role.ADMIN ? suggestedDepartment(c.getCategory()) : null;
        return new ComplaintResponse(c.getId(), c.getTrackingId(), c.getTitle(), c.getDescription(),
                c.getCategory(), c.getPriority(), c.getStatus(), c.getLatitude(), c.getLongitude(), c.getAddress(),
                photoUrls.isEmpty() ? c.getImagePath() : photoUrls.get(0), photoUrls,
                internal ? c.getCitizen().getName() : null,
                d == null ? null : d.getId(), d == null ? null : d.getName(),
                sug == null ? null : sug.getId(), sug == null ? null : sug.getName(),
                internal && o != null ? o.getId() : null, internal && o != null ? o.getName() : null,
                c.getCreatedAt(), c.getUpdatedAt(), c.getResolvedAt(), c.getAcceptedAt(), sla, overdue, c.getEscalatedAt(),
                history, res, fb);
    }
}
