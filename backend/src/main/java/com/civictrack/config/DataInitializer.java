package com.civictrack.config;

import com.civictrack.model.*;
import com.civictrack.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Idempotent start-up seeding: required departments + category mapping always; demo accounts only when
 * app.seed.demo-accounts=true (set SEED_DEMO_ACCOUNTS=false for a real deployment). Nothing existing is overwritten.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {
    private final DepartmentRepository departments;
    private final UserRepository users;
    private final CategoryDepartmentRepository mappings;
    private final PasswordEncoder encoder;

    @Value("${app.seed.demo-accounts:true}") private boolean seedDemo;
    @Value("${app.seed.admin-password:}") private String adminPassword;
    @Value("${app.seed.officer-password:}") private String officerPassword;
    @Value("${app.seed.citizen-password:}") private String citizenPassword;

    @Override
    public void run(String... args) {
        Map<String, String> depts = new LinkedHashMap<>();
        depts.put("Roads & Public Works", "Potholes, road damage, footpaths, bridges");
        depts.put("Water Supply", "Water leakage, supply interruption, contamination");
        depts.put("Sanitation & Waste", "Garbage collection, public toilets, cleanliness");
        depts.put("Electricity & Street Lights", "Power faults, street light repairs");
        depts.put("Drainage & Sewerage", "Blocked drains, sewage overflow, flooding");
        depts.put("Parks & Environment", "Gardens, trees, pollution, public spaces");
        depts.put("Public Safety", "Hazards, encroachment, unsafe structures");
        depts.put("Municipal Maintenance", "Public buildings and property, general municipal issues");
        depts.forEach((name, desc) -> {
            if (!departments.existsByNameIgnoreCase(name)) {
                Department x = new Department(); x.setName(name); x.setDescription(desc); departments.save(x);
            }
        });

        // category -> default department (only fills categories that have no mapping yet; admins can change it)
        Map<Category, String> map = new LinkedHashMap<>();
        map.put(Category.ROADS, "Roads & Public Works");
        map.put(Category.WATER_SUPPLY, "Water Supply");
        map.put(Category.SANITATION, "Sanitation & Waste");
        map.put(Category.ELECTRICITY, "Electricity & Street Lights");
        map.put(Category.STREET_LIGHTS, "Electricity & Street Lights");
        map.put(Category.DRAINAGE, "Drainage & Sewerage");
        map.put(Category.PARKS, "Parks & Environment");
        map.put(Category.PUBLIC_SAFETY, "Public Safety");
        map.put(Category.PUBLIC_PROPERTY, "Municipal Maintenance");
        map.put(Category.OTHER, "Municipal Maintenance");
        map.forEach((cat, deptName) -> {
            if (mappings.findByCategory(cat).isEmpty()) {
                departments.findByNameIgnoreCase(deptName).ifPresent(d -> {
                    CategoryDepartment m = new CategoryDepartment(); m.setCategory(cat); m.setDepartment(d); mappings.save(m);
                });
            }
        });

        if (!seedDemo) { log.info("Demo account seeding is disabled."); return; }
        // Passwords come from configuration; if not configured, the documented local-demo defaults are used.
        seed("System Admin", "admin@civictrack.com", pick(adminPassword, "Admin@123"), Role.ADMIN, null);
        seed("Road Officer", "officer@civictrack.com", pick(officerPassword, "Officer@123"), Role.OFFICER, dept("Roads & Public Works"));
        seed("Water Officer", "officer2@civictrack.com", pick(officerPassword, "Officer@123"), Role.OFFICER, dept("Water Supply"));
        seed("Demo Citizen", "citizen@civictrack.com", pick(citizenPassword, "Citizen@123"), Role.CITIZEN, null);
        log.warn("Demo accounts are enabled (admin@/officer@/officer2@/citizen@civictrack.com). Disable them with SEED_DEMO_ACCOUNTS=false for real use.");
    }

    private static String pick(String configured, String fallback) { return configured == null || configured.isBlank() ? fallback : configured; }
    private Department dept(String name) { return departments.findByNameIgnoreCase(name).orElse(null); }

    private void seed(String name, String email, String pwd, Role role, Department dept) {
        if (users.existsByEmail(email)) return;
        User u = new User();
        u.setName(name); u.setEmail(email); u.setPassword(encoder.encode(pwd)); u.setRole(role); u.setDepartment(dept);
        users.save(u);
    }
}
