package com.civictrack.repository;

import com.civictrack.model.Category;
import com.civictrack.model.CategoryDepartment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CategoryDepartmentRepository extends JpaRepository<CategoryDepartment, Long> {
    Optional<CategoryDepartment> findByCategory(Category category);
}
