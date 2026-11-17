package com.civictrack.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;

@Entity
@Table(name = "complaints", indexes = {
        @Index(name = "idx_complaints_status", columnList = "status"),
        @Index(name = "idx_complaints_officer", columnList = "officer_id"),
        @Index(name = "idx_complaints_department", columnList = "department_id"),
        @Index(name = "idx_complaints_category", columnList = "category"),
        @Index(name = "idx_complaints_created", columnList = "created_at"),
        @Index(name = "idx_complaints_geo", columnList = "latitude,longitude")
})
@Getter @Setter @NoArgsConstructor
public class Complaint {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String trackingId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 30)
    private Category category;

    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 20)
    private Priority priority = Priority.MEDIUM;

    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 30)
    private ComplaintStatus status = ComplaintStatus.SUBMITTED;

    private Double latitude;
    private Double longitude;

    @Column(length = 500)
    private String address;

    /** First complaint photo (kept for backward compatibility); all photos live in complaint_photos. */
    private String imagePath;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "citizen_id", nullable = false)
    private User citizen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "officer_id")
    private User assignedOfficer;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime resolvedAt;
    /** When the assigned officer accepted the work (null until accepted). */
    private LocalDateTime acceptedAt;
    /** SLA deadline derived from the priority. */
    private LocalDateTime slaDueAt;
    /** Set once the SLA was breached and admins were alerted. */
    private LocalDateTime escalatedAt;

    @PrePersist void onCreate() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }
    @PreUpdate  void onUpdate() { updatedAt = LocalDateTime.now(); }
}
