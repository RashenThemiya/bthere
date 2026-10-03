package com.jobhub.entity.access;

import com.jobhub.entity.CreatedEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "user_role",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_role",
                columnNames = {"user_id", "role_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class UserRole extends CreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userRoleId;
    private Long userId;
    private Long roleId;
}

