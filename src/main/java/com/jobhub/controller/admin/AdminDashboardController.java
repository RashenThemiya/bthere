package com.jobhub.controller.admin;

import com.jobhub.dto.admin.AdminDashboardResponse;
import com.jobhub.service.admin.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api/v1/admin/dashboard") @RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
public class AdminDashboardController {
    private final AdminDashboardService service;
    @GetMapping("/statistics")
    public ResponseEntity<AdminDashboardResponse> statistics() {
        return ResponseEntity.ok(service.get());
    }
}
