package com.jobhub.controller.admin;

import com.jobhub.dto.admin.AdminResponse;
import com.jobhub.dto.admin.CreateAdminRequest;
import com.jobhub.dto.admin.UserAdminResponse;
import com.jobhub.dto.admin.UpdateUserStatusRequest;
import com.jobhub.dto.admin.ResetUserPasswordRequest;
import com.jobhub.security.AuthenticatedUser;
import com.jobhub.service.admin.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Page<UserAdminResponse>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String role,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(adminUserService.search(search, status, role, pageable));
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<AdminResponse> create(
            @Valid @RequestBody CreateAdminRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminUserService.create(request));
    }

    @PatchMapping("/{userId}/status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<UserAdminResponse> updateStatus(
            @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long userId,
            @Valid @RequestBody UpdateUserStatusRequest request) {
        return ResponseEntity.ok(adminUserService.updateStatus(actor.id(), userId, request.status()));
    }

    @PostMapping("/{userId}/reset-password")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> resetPassword(
            @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long userId,
            @Valid @RequestBody ResetUserPasswordRequest request) {
        adminUserService.resetPassword(actor.id(), userId, request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
