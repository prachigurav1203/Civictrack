package com.civictrack.repository;

import com.civictrack.model.Complaint;
import com.civictrack.model.ComplaintStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.*;

public interface ComplaintRepository extends JpaRepository<Complaint, Long>, JpaSpecificationExecutor<Complaint> {
    Optional<Complaint> findByTrackingId(String trackingId);
    boolean existsByTrackingId(String trackingId);
    long countByDepartmentId(Long departmentId);
    long countByAssignedOfficerIdAndStatusIn(Long officerId, Collection<ComplaintStatus> statuses);

    List<Complaint> findByStatusNotInAndSlaDueAtBeforeAndEscalatedAtIsNull(Collection<ComplaintStatus> statuses, LocalDateTime now);
    List<Complaint> findTop500ByLatitudeIsNotNullAndLongitudeIsNotNullOrderByCreatedAtDesc();

    /** Bounding-box pre-filter for duplicate detection (exact distance is computed in the service). */
    @Query("select c from Complaint c where c.id <> :id and c.latitude between :minLat and :maxLat "
            + "and c.longitude between :minLng and :maxLng and c.status <> com.civictrack.model.ComplaintStatus.REJECTED")
    List<Complaint> findNearby(@Param("id") Long id, @Param("minLat") double minLat, @Param("maxLat") double maxLat,
                               @Param("minLng") double minLng, @Param("maxLng") double maxLng);
}
