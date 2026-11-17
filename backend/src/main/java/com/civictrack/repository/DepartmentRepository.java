package com.civictrack.repository;

import com.civictrack.model.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface DepartmentRepository extends JpaRepository<Department, Long> {
    boolean existsByNameIgnoreCase(String name);
    Optional<Department> findByNameIgnoreCase(String name);
    List<Department> findAllByOrderByNameAsc();
}
