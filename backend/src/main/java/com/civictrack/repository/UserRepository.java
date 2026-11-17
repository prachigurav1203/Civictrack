package com.civictrack.repository;

import com.civictrack.model.Role;
import com.civictrack.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    List<User> findByRoleOrderByNameAsc(Role role);
    List<User> findByRoleAndDepartmentIdOrderByNameAsc(Role role, Long departmentId);
    List<User> findAllByOrderByCreatedAtDesc();
    List<User> findByRoleAndActiveTrue(Role role);
    long countByRole(Role role);
    long countByDepartmentId(Long departmentId);
}
