package com.civictrack.repository;

import com.civictrack.model.ResolutionDetail;
import com.civictrack.model.ResolutionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ResolutionRepository extends JpaRepository<ResolutionDetail, Long> {
    List<ResolutionDetail> findByComplaintIdOrderBySubmittedAtAscIdAsc(Long complaintId);
    Optional<ResolutionDetail> findFirstByComplaintIdAndStatusOrderBySubmittedAtDesc(Long complaintId, ResolutionStatus status);
}
