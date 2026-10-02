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
                .andExpect(jsonPath("$.workMetrics.total").value(4));
    }

    @Test
    @WithMockUser(roles = "WARD_OFFICER")
    void wardOfficerCanLoadDashboard() throws Exception {
        stubResponse();

        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "CITIZEN")
    void citizenCannotLoadDashboard() throws Exception {
        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isForbidden());
    }

    private void stubResponse() {
        AdminDashboardResponse response = new AdminDashboardResponse(
                new AdminDashboardResponse.WorkMetrics(4, 1, 1, 1, 1, 1),
                new AdminDashboardResponse.ComplaintMetrics(2, 2, 1),
                List.of(), List.of(), List.of(), List.of(), LocalDateTime.now());
        when(dashboardService.getDashboard(nullable(User.class))).thenReturn(response);
    }
}
