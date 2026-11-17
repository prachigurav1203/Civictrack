package com.civictrack.repository;

import com.civictrack.model.StatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StatusHistoryRepository extends JpaRepository<StatusHistory, Long> {
    List<StatusHistory> findByComplaintIdOrderByChangedAtAscIdAsc(Long complaintId);
}
