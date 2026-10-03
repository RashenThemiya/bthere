package com.jobhub.controller.admin;

import com.jobhub.dto.admin.AdminResponse;
import com.jobhub.dto.admin.CreateAdminRequest;
import com.jobhub.service.admin.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<AdminResponse> create(
            @Valid @RequestBody CreateAdminRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminUserService.create(request));
    }
}
