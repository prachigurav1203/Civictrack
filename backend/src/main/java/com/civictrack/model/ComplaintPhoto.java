package com.civictrack.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/** A photo attached to a complaint (resolution == null) or to an officer's resolution submission. */
@Entity
@Table(name = "complaint_photos", indexes = {
        @Index(name = "idx_photos_complaint", columnList = "complaint_id"),
        @Index(name = "idx_photos_resolution", columnList = "resolution_id")})
@Getter @Setter @NoArgsConstructor
public class ComplaintPhoto {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "complaint_id", nullable = false)
    private Complaint complaint;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolution_id")
    private ResolutionDetail resolution;

    @Column(nullable = false, length = 255)
    private String url;

    @Column(nullable = false)
    private LocalDateTime uploadedAt = LocalDateTime.now();

    public ComplaintPhoto(Complaint complaint, ResolutionDetail resolution, String url) {
        this.complaint = complaint; this.resolution = resolution; this.url = url;
    }
}
