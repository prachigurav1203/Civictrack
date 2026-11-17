package com.civictrack.repository;

import com.civictrack.model.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    Optional<Feedback> findByComplaintId(Long complaintId);
    boolean existsByComplaintId(Long complaintId);

    @Query("select avg(f.rating) from Feedback f")
    Double averageRating();
}
