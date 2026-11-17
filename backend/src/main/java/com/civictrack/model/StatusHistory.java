package com.civictrack.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;

@Entity
@Table(name = "status_history", indexes = @Index(name = "idx_history_complaint", columnList = "complaint_id"))
@Getter @Setter @NoArgsConstructor
public class StatusHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "complaint_id", nullable = false)
    private Complaint complaint;

    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 30)
    private ComplaintStatus status;

    @Column(length = 1000)
    private String remarks;

    /** null = changed automatically by the system (e.g. SLA escalation). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by")
    private User changedBy;

    @Column(nullable = false)
    private LocalDateTime changedAt = LocalDateTime.now();

    public StatusHistory(Complaint complaint, ComplaintStatus status, String remarks, User changedBy) {
        this.complaint = complaint; this.status = status; this.remarks = remarks; this.changedBy = changedBy;
    }
}
