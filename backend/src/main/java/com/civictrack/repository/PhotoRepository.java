package com.civictrack.repository;

import com.civictrack.model.ComplaintPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PhotoRepository extends JpaRepository<ComplaintPhoto, Long> {
    List<ComplaintPhoto> findByComplaintIdOrderByIdAsc(Long complaintId);
}
