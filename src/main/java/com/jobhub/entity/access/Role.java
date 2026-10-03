package com.jobhub.entity.access;

import com.jobhub.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "role",
        uniqueConstraints = @UniqueConstraint(name = "uk_role_name", columnNames = "name")
)
@Getter
@Setter
@NoArgsConstructor
public class Role extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long roleId;
    private String name;
    private String description;
    private String status;
}

