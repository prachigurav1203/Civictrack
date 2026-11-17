package com.civictrack.controller;

import com.civictrack.dto.Dtos.*;
import com.civictrack.exception.ApiException;
import com.civictrack.model.Department;
import com.civictrack.repository.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/departments")
@RequiredArgsConstructor
public class DepartmentController {
    private final DepartmentRepository departments;
    private final ComplaintRepository complaints;
    private final UserRepository users;
    private final CategoryDepartmentRepository mappings;

    @GetMapping
    public List<DepartmentResponse> list() {
        return departments.findAllByOrderByNameAsc().stream().map(DepartmentResponse::from).toList();
    }

    /** Public: which department handles which category (used by the complaint form). */
    @GetMapping("/category-map")
    public List<CategoryMappingResponse> categoryMap() {
        return mappings.findAll().stream()
                .map(m -> new CategoryMappingResponse(m.getCategory(), m.getDepartment().getId(), m.getDepartment().getName())).toList();
    }

    @PostMapping
    public DepartmentResponse create(@Valid @RequestBody DepartmentRequest r) {
        if (departments.existsByNameIgnoreCase(r.name().trim())) throw ApiException.conflict("Department already exists");
        Department d = new Department();
        d.setName(r.name().trim()); d.setDescription(r.description());
        return DepartmentResponse.from(departments.save(d));
    }

    @PutMapping("/{id}")
    public DepartmentResponse update(@PathVariable Long id, @Valid @RequestBody DepartmentRequest r) {
        Department d = departments.findById(id).orElseThrow(() -> ApiException.notFound("Department not found"));
        departments.findByNameIgnoreCase(r.name().trim()).filter(x -> !x.getId().equals(id))
                .ifPresent(x -> { throw ApiException.conflict("Another department already uses this name"); });
        d.setName(r.name().trim()); d.setDescription(r.description());
        return DepartmentResponse.from(departments.save(d));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        if (!departments.existsById(id)) throw ApiException.notFound("Department not found");
        if (complaints.countByDepartmentId(id) > 0 || users.countByDepartmentId(id) > 0 || mappings.findAll().stream().anyMatch(m -> m.getDepartment().getId().equals(id)))
            throw ApiException.conflict("Department has complaints, officers or category mappings and cannot be deleted");
        departments.deleteById(id);
    }
}
