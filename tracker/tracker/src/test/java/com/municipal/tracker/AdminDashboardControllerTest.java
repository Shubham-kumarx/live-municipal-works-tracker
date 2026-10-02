package com.municipal.tracker;

import com.municipal.tracker.dto.AdminDashboardResponse;
import com.municipal.tracker.model.User;
import com.municipal.tracker.service.AdminDashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminDashboardControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean AdminDashboardService dashboardService;

    @Test
    @WithMockUser(roles = "MUNICIPAL_ADMIN")
    void municipalAdminCanLoadDashboard() throws Exception {
        stubResponse();

        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workMetrics.total").value(4))
                .andExpect(jsonPath("$.workMetrics.highPriority").value(1))
                .andExpect(jsonPath("$.workMetrics.highDelayRisk").value(1))
                .andExpect(jsonPath("$.complaintMetrics.total").value(2))
                .andExpect(jsonPath("$.complaintMetrics.unresolved").value(2))
                .andExpect(jsonPath("$.complaintMetrics.aiAssisted").value(1))
                .andExpect(jsonPath("$.works").isArray())
                .andExpect(jsonPath("$.recentComplaints").isArray())
                .andExpect(jsonPath("$.generatedAt").exists());
    }

    @Test
    @WithMockUser(roles = "WARD_OFFICER")
    void wardOfficerCanLoadDashboard() throws Exception {
        stubResponse();

        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workStatusDistribution").isArray())
                .andExpect(jsonPath("$.complaintSeverityDistribution").isArray());
    }

    @Test
    @WithMockUser(roles = "CITIZEN")
    void citizenCannotLoadDashboard() throws Exception {
        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Access denied"))
                .andExpect(jsonPath("$.path").value("/api/dashboard"));
    }

    @Test
    void anonymousUserReceivesStructuredUnauthorizedResponse() throws Exception {
        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Authentication required"))
                .andExpect(jsonPath("$.path").value("/api/dashboard"));
    }

    private void stubResponse() {
        AdminDashboardResponse response = new AdminDashboardResponse(
                new AdminDashboardResponse.WorkMetrics(4, 1, 1, 1, 1, 1),
                new AdminDashboardResponse.ComplaintMetrics(2, 2, 1),
                List.of(), List.of(), List.of(), List.of(), LocalDateTime.now());
        when(dashboardService.getDashboard(nullable(User.class))).thenReturn(response);
    }
}
