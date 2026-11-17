package com.civictrack.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;

/** One resolution submission by an officer, verified (approved/rejected) by an admin. */
@Entity
@Table(name = "resolution_details", indexes = @Index(name = "idx_resolution_complaint", columnList = "complaint_id"))
@Getter @Setter @NoArgsConstructor
public class ResolutionDetail {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "complaint_id", nullable = false)
    private Complaint complaint;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submitted_by", nullable = false)
    private User submittedBy;

    @Column(nullable = false, length = 2000)
    private String remarks;

    @Column(nullable = false)
    private LocalDateTime submittedAt = LocalDateTime.now();

    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 20)
    private ResolutionStatus status = ResolutionStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by")
    private User verifiedBy;

    private LocalDateTime verifiedAt;

    @Column(length = 1000)
    private String adminRemarks;
}
