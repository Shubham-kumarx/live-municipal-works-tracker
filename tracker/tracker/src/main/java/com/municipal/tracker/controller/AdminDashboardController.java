package com.municipal.tracker.controller;

import com.municipal.tracker.dto.AdminDashboardResponse;
import com.municipal.tracker.model.User;
import com.municipal.tracker.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {
    private final AdminDashboardService dashboardService;

    @GetMapping
    @PreAuthorize("hasAnyRole('MUNICIPAL_ADMIN','WARD_OFFICER')")
    public ResponseEntity<AdminDashboardResponse> getDashboard(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(dashboardService.getDashboard(currentUser));
    }
}
