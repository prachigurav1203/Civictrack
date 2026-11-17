package com.civictrack.controller;

import com.civictrack.dto.Dtos.*;
import com.civictrack.exception.ApiException;
import com.civictrack.model.*;
import com.civictrack.repository.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Everything under /api/admin requires the ADMIN role (SecurityConfig). */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {
    private final UserRepository users;
    private final DepartmentRepository departments;
    private final CategoryDepartmentRepository mappings;
    private final PasswordEncoder encoder;

    @GetMapping("/users")
    public List<UserResponse> users(@RequestParam(required = false) Role role, @RequestParam(required = false) String search) {
        List<User> list = role == null ? users.findAllByOrderByCreatedAtDesc() : users.findByRoleOrderByNameAsc(role);
        String q = StringUtils.hasText(search) ? search.trim().toLowerCase(Locale.ROOT) : null;
        return list.stream()
                .filter(u -> q == null || u.getName().toLowerCase(Locale.ROOT).contains(q) || u.getEmail().toLowerCase(Locale.ROOT).contains(q))
                .map(UserResponse::from).toList();
    }

    /** Active officers (optionally of one department) - used by the assignment form. */
    @GetMapping("/officers")
    public List<UserResponse> officers(@RequestParam(required = false) Long departmentId) {
        List<User> list = departmentId == null ? users.findByRoleOrderByNameAsc(Role.OFFICER)
                : users.findByRoleAndDepartmentIdOrderByNameAsc(Role.OFFICER, departmentId);
        return list.stream().filter(User::isActive).map(UserResponse::from).toList();
    }

    @PostMapping("/officers")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createOfficer(@Valid @RequestBody OfficerRequest r) {
        String email = r.email().trim().toLowerCase();
        if (users.existsByEmail(email)) throw ApiException.conflict("Email is already registered");
        Department d = departments.findById(r.departmentId()).orElseThrow(() -> ApiException.notFound("Department not found"));
        User u = new User();
        u.setName(r.name().trim()); u.setEmail(email);
        u.setPhone(StringUtils.hasText(r.phone()) ? r.phone().trim() : null);
        u.setPassword(encoder.encode(r.password())); u.setRole(Role.OFFICER); u.setDepartment(d);
        return UserResponse.from(users.save(u));
    }

    @PutMapping("/officers/{id}")
    public UserResponse updateOfficer(@PathVariable Long id, @Valid @RequestBody OfficerUpdateRequest r) {
        User u = officer(id);
        Department d = departments.findById(r.departmentId()).orElseThrow(() -> ApiException.notFound("Department not found"));
        u.setName(r.name().trim());
        u.setPhone(StringUtils.hasText(r.phone()) ? r.phone().trim() : null);
        u.setDepartment(d);
        return UserResponse.from(users.save(u));
    }

    @PostMapping("/officers/{id}/password")
    public UserResponse resetOfficerPassword(@PathVariable Long id, @Valid @RequestBody PasswordResetRequest r) {
        User u = officer(id);
        u.setPassword(encoder.encode(r.newPassword()));
        return UserResponse.from(users.save(u));
    }

    @PatchMapping("/users/{id}/active")
    public UserResponse setActive(@AuthenticationPrincipal User admin, @PathVariable Long id, @RequestBody ActiveRequest r) {
        if (admin.getId().equals(id)) throw ApiException.badRequest("You cannot deactivate your own account");
        User u = users.findById(id).orElseThrow(() -> ApiException.notFound("User not found"));
        u.setActive(r.active());
        return UserResponse.from(users.save(u));
    }

    // ---- category -> department mapping used for automatic assignment
    @GetMapping("/category-departments")
    public List<CategoryMappingResponse> mappings() {
        return Arrays.stream(Category.values()).map(c -> mappings.findByCategory(c)
                .map(m -> new CategoryMappingResponse(c, m.getDepartment().getId(), m.getDepartment().getName()))
                .orElse(new CategoryMappingResponse(c, null, null))).toList();
    }

    @PutMapping("/category-departments/{category}")
    @Transactional
    public CategoryMappingResponse setMapping(@PathVariable Category category, @Valid @RequestBody CategoryMappingRequest r) {
        Department d = departments.findById(r.departmentId()).orElseThrow(() -> ApiException.notFound("Department not found"));
        CategoryDepartment m = mappings.findByCategory(category).orElseGet(() -> { CategoryDepartment x = new CategoryDepartment(); x.setCategory(category); return x; });
        m.setDepartment(d);
        mappings.save(m);
        return new CategoryMappingResponse(category, d.getId(), d.getName());
    }

    private User officer(Long id) {
        User u = users.findById(id).orElseThrow(() -> ApiException.notFound("User not found"));
        if (u.getRole() != Role.OFFICER) throw ApiException.badRequest("This user is not an officer");
        return u;
    }
}
