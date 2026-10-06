package com.jobhub.repository.access;

import com.jobhub.entity.access.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    boolean existsByUserIdAndRoleId(Long userId, Long roleId);

    @Query("""
            select role.name
            from UserRole userRole, Role role
            where userRole.roleId = role.roleId
              and userRole.userId = :userId
              and role.status = 'ACTIVE'
            order by role.name
            """)
    List<String> findActiveRoleNamesByUserId(@Param("userId") Long userId);

    @Query("""
            select userRole.userId
            from UserRole userRole, Role role
            where userRole.roleId = role.roleId
              and upper(role.name) = upper(:role)
              and role.status = 'ACTIVE'
            """)
    List<Long> findUserIdsByRoleName(@Param("role") String role);
}
