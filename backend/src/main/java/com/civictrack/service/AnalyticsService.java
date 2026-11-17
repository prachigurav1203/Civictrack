package com.civictrack.service;

import com.civictrack.model.*;
import com.civictrack.repository.*;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** All figures are computed from the database on every request (nothing is hard-coded or cached). */
@Service
@RequiredArgsConstructor
public class AnalyticsService {
    private final ComplaintRepository complaints;
    private final UserRepository users;
    private final DepartmentRepository departments;
    private final FeedbackRepository feedbacks;

    @Transactional(readOnly = true)
    public List<Complaint> filtered(LocalDate from, LocalDate to, Long departmentId, Category category) {
        Specification<Complaint> spec = (r, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (from != null) p.add(cb.greaterThanOrEqualTo(r.<LocalDateTime>get("createdAt"), from.atStartOfDay()));
            if (to != null) p.add(cb.lessThan(r.<LocalDateTime>get("createdAt"), to.plusDays(1).atStartOfDay()));
            if (departmentId != null) p.add(cb.equal(r.get("department").get("id"), departmentId));
            if (category != null) p.add(cb.equal(r.get("category"), category));
            return cb.and(p.toArray(new Predicate[0]));
        };
        return complaints.findAll(spec);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> summary(LocalDate from, LocalDate to, Long departmentId, Category category) {
        List<Complaint> list = filtered(from, to, departmentId, category);
        LocalDateTime now = LocalDateTime.now();

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (ComplaintStatus s : ComplaintStatus.values()) byStatus.put(s.name(), 0L);
        Map<String, Long> byCategory = new LinkedHashMap<>();
        for (Category c : Category.values()) byCategory.put(c.name(), 0L);
        Map<String, long[]> dept = new TreeMap<>();                     // name -> {total, resolved, open, overdue}
        Map<String, double[]> deptHours = new HashMap<>();              // name -> {sumHours, n}
        Map<Long, Object[]> officerAgg = new LinkedHashMap<>();         // officerId -> {name, dept, assigned, inProgress, resolved, overdue, sumHours, n}
        double sumHours = 0; long resolvedN = 0; long overdue = 0;

        for (Complaint c : list) {
            byStatus.merge(c.getStatus().name(), 1L, Long::sum);
            byCategory.merge(c.getCategory().name(), 1L, Long::sum);
            boolean closed = ComplaintService.CLOSED.contains(c.getStatus());
            boolean late = !closed && c.getSlaDueAt() != null && now.isAfter(c.getSlaDueAt());
            if (late) overdue++;
            String dn = c.getDepartment() == null ? "Unassigned" : c.getDepartment().getName();
            long[] d = dept.computeIfAbsent(dn, k -> new long[4]);
            d[0]++;
            if (c.getStatus() == ComplaintStatus.RESOLVED) d[1]++; else if (!closed) d[2]++;
            if (late) d[3]++;
            double hrs = -1;
            if (c.getStatus() == ComplaintStatus.RESOLVED && c.getResolvedAt() != null && c.getCreatedAt() != null) {
                hrs = Duration.between(c.getCreatedAt(), c.getResolvedAt()).toMinutes() / 60.0;
                sumHours += hrs; resolvedN++;
                double[] dh = deptHours.computeIfAbsent(dn, k -> new double[2]);
                dh[0] += hrs; dh[1]++;
            }
            User o = c.getAssignedOfficer();
            if (o != null) {
                Object[] a = officerAgg.computeIfAbsent(o.getId(), k -> new Object[]{o.getName(),
                        o.getDepartment() == null ? "-" : o.getDepartment().getName(), 0L, 0L, 0L, 0L, 0.0, 0L});
                a[2] = (Long) a[2] + 1;
                if (c.getStatus() == ComplaintStatus.IN_PROGRESS) a[3] = (Long) a[3] + 1;
                if (c.getStatus() == ComplaintStatus.RESOLVED) { a[4] = (Long) a[4] + 1; a[6] = (Double) a[6] + Math.max(hrs, 0); a[7] = (Long) a[7] + 1; }
                if (late) a[5] = (Long) a[5] + 1;
            }
        }

        long total = list.size();
        long resolved = byStatus.get("RESOLVED");

        List<Map<String, Object>> deptStats = new ArrayList<>();
        Map<String, Long> byDepartment = new LinkedHashMap<>();
        dept.forEach((name, v) -> {
            double[] dh = deptHours.get(name);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("department", name); m.put("total", v[0]); m.put("resolved", v[1]); m.put("open", v[2]); m.put("overdue", v[3]);
            m.put("avgResolutionHours", dh == null ? 0 : Math.round(dh[0] / dh[1] * 10) / 10.0);
            deptStats.add(m);
            byDepartment.put(name, v[0]);
        });

        List<Map<String, Object>> officerPerf = new ArrayList<>();
        officerAgg.values().forEach(a -> {
            long n = (Long) a[7];
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("officer", a[0]); m.put("department", a[1]); m.put("assigned", a[2]); m.put("inProgress", a[3]);
            m.put("resolved", a[4]); m.put("overdue", a[5]);
            m.put("avgResolutionHours", n == 0 ? 0 : Math.round((Double) a[6] / n * 10) / 10.0);
            officerPerf.add(m);
        });
        officerPerf.sort((x, y) -> Long.compare((Long) y.get("resolved"), (Long) x.get("resolved")));

        // monthly trend: requested range (max 24 months) or the last 6 months
        YearMonth end = to != null ? YearMonth.from(to) : YearMonth.now();
        YearMonth start = from != null ? YearMonth.from(from) : end.minusMonths(5);
        if (start.isAfter(end)) start = end;
        if (start.isBefore(end.minusMonths(23))) start = end.minusMonths(23);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
        List<Map<String, Object>> monthly = new ArrayList<>();
        for (YearMonth ym = start; !ym.isAfter(end); ym = ym.plusMonths(1)) {
            final YearMonth m0 = ym;
            long sub = list.stream().filter(c -> c.getCreatedAt() != null && YearMonth.from(c.getCreatedAt()).equals(m0)).count();
            long done = list.stream().filter(c -> c.getResolvedAt() != null && YearMonth.from(c.getResolvedAt()).equals(m0)).count();
            monthly.add(Map.of("month", ym.format(fmt), "submitted", sub, "resolved", done));
        }

        List<Map<String, Object>> points = list.stream().filter(c -> c.getLatitude() != null && c.getLongitude() != null)
                .sorted(Comparator.comparing(Complaint::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))).limit(500)
                .map(c -> Map.<String, Object>of("id", c.getId(), "trackingId", c.getTrackingId(), "title", c.getTitle(),
                        "status", c.getStatus().name(), "latitude", c.getLatitude(), "longitude", c.getLongitude())).toList();

        Double rating = feedbacks.averageRating();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", total);
        out.put("pending", byStatus.get("SUBMITTED"));
        out.put("underReview", byStatus.get("UNDER_REVIEW"));
        out.put("assigned", byStatus.get("ASSIGNED"));
        out.put("inProgress", byStatus.get("IN_PROGRESS"));
        out.put("resolutionSubmitted", byStatus.get("RESOLUTION_SUBMITTED"));
        out.put("resolved", resolved);
        out.put("rejected", byStatus.get("REJECTED"));
        out.put("reopened", byStatus.get("REOPENED"));
        out.put("overdue", overdue);
        out.put("resolutionRate", total == 0 ? 0 : Math.round(resolved * 1000.0 / total) / 10.0);
        out.put("avgResolutionHours", resolvedN == 0 ? 0 : Math.round(sumHours / resolvedN * 10) / 10.0);
        out.put("avgRating", rating == null ? 0 : Math.round(rating * 10) / 10.0);
        out.put("citizens", users.countByRole(Role.CITIZEN));
        out.put("officers", users.countByRole(Role.OFFICER));
        out.put("departments", departments.count());
        out.put("byStatus", byStatus);
        out.put("byCategory", byCategory);
        out.put("byDepartment", byDepartment);
        out.put("departmentStats", deptStats);
        out.put("officerPerformance", officerPerf);
        out.put("monthly", monthly);
        out.put("points", points);
        return out;
    }
}
