package com.civictrack.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/** Audit trail of every officer assignment / reassignment. */
@Entity
@Table(name = "officer_assignments", indexes = @Index(name = "idx_assignments_complaint", columnList = "complaint_id"))
@Getter @Setter @NoArgsConstructor
public class OfficerAssignment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "complaint_id", nullable = false)
    private Complaint complaint;

    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "officer_id", nullable = false)
    private User officer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "assigned_by", nullable = false)
    private User assignedBy;

    @Column(nullable = false)
    private LocalDateTime assignedAt = LocalDateTime.now();

    public OfficerAssignment(Complaint c, User officer, Department d, User by) {
        this.complaint = c; this.officer = officer; this.department = d; this.assignedBy = by;
    }
}
