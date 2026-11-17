package com.civictrack.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Category -> department mapping used for automatic department assignment. Editable by admins. */
@Entity
@Table(name = "category_departments", uniqueConstraints = @UniqueConstraint(name = "uk_category_dept", columnNames = "category"))
@Getter @Setter @NoArgsConstructor
public class CategoryDepartment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 30)
    private Category category;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;
}
