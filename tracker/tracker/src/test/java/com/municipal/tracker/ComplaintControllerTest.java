package com.municipal.tracker;

import com.municipal.tracker.dto.AIAnalysisResponse;
import com.municipal.tracker.dto.ComplaintResponse;
import com.municipal.tracker.model.*;
import com.municipal.tracker.service.ComplaintService;
import com.municipal.tracker.service.ImageAnalysisService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ComplaintControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean ImageAnalysisService imageAnalysisService;
    @MockitoBean ComplaintService complaintService;

    @Test
    @WithMockUser(roles = "CITIZEN")
    void citizenCanAnalyzeThroughSpringEndpoint() throws Exception {
        when(imageAnalysisService.analyze(any())).thenReturn(new AIAnalysisResponse(
                ComplaintIssueType.POTHOLE, ComplaintIssueType.POTHOLE, 0.91,
                AIAnalysisResponse.ConfidenceLevel.HIGH, ComplaintCategory.ROAD,
                ComplaintSeverity.HIGH, false));
        MockMultipartFile image = new MockMultipartFile(
                "image", "road.png", "image/png", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/complaints/analyze").file(image))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issueType").value("POTHOLE"))
                .andExpect(jsonPath("$.candidateIssueType").value("POTHOLE"))
                .andExpect(jsonPath("$.confidence").value(0.91))
                .andExpect(jsonPath("$.confidenceLevel").value("HIGH"))
                .andExpect(jsonPath("$.category").value("ROAD"))
                .andExpect(jsonPath("$.suggestedSeverity").value("HIGH"))
                .andExpect(jsonPath("$.requiresManualReview").value(false));
    }

    @Test
    @WithMockUser(roles = "WARD_OFFICER")
    void nonCitizenCannotAnalyze() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "image", "road.png", "image/png", new byte[]{1});
        mockMvc.perform(multipart("/api/complaints/analyze").file(image))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CITIZEN")
    void analysisRequiresTheImageMultipartPart() throws Exception {
        mockMvc.perform(multipart("/api/complaints/analyze"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @WithMockUser(roles = "CITIZEN")
    void citizenCanCreateMultipartComplaint() throws Exception {
        ComplaintResponse saved = new ComplaintResponse(44L, 5L, "Citizen",
                "/uploads/complaints/test.png", "Pothole", "Test Road", null, null,
                null, null, null, null, ComplaintIssueType.POTHOLE, ComplaintSeverity.HIGH,
                ComplaintPredictionState.MANUAL, ComplaintStatus.SUBMITTED, null,
                LocalDateTime.now(), LocalDateTime.now());
        when(complaintService.create(any(), any(), nullable(User.class))).thenReturn(saved);
        MockMultipartFile request = new MockMultipartFile("complaint", "", "application/json", """
                {"description":"Pothole","locationAddress":"Test Road","finalIssueType":"POTHOLE",
                 "finalSeverity":"HIGH","predictionState":"MANUAL"}
                """.getBytes());
        MockMultipartFile image = new MockMultipartFile(
                "image", "road.png", "image/png", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/complaints").file(request).file(image))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(44))
                .andExpect(jsonPath("$.reportingUserId").value(5))
                .andExpect(jsonPath("$.finalIssueType").value("POTHOLE"))
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
    }

    @Test
    @WithMockUser(roles = "CITIZEN")
    void unsupportedSeverityReturnsSafeValidationError() throws Exception {
        MockMultipartFile request = new MockMultipartFile("complaint", "", "application/json", """
                {"description":"Pothole","locationAddress":"Test Road","finalIssueType":"POTHOLE",
                 "finalSeverity":"EXTREME","predictionState":"MANUAL"}
                """.getBytes());
        MockMultipartFile image = new MockMultipartFile(
                "image", "road.png", "image/png", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/complaints").file(request).file(image))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unsupported complaint severity"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/api/complaints"));
    }

    @Test
    @WithMockUser(roles = "CITIZEN")
    void unsupportedIssueTypeReturnsSafeValidationError() throws Exception {
        MockMultipartFile request = new MockMultipartFile("complaint", "", "application/json", """
                {"description":"Pothole","locationAddress":"Test Road","finalIssueType":"SINKHOLE",
                 "finalSeverity":"HIGH","predictionState":"MANUAL"}
                """.getBytes());
        MockMultipartFile image = new MockMultipartFile(
                "image", "road.png", "image/png", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/complaints").file(request).file(image))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unsupported complaint issue type"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @WithMockUser(roles = "CITIZEN")
    void legacyIssueTypeCannotBeUsedForNewComplaint() throws Exception {
        MockMultipartFile request = new MockMultipartFile("complaint", "", "application/json", """
                {"description":"Road damage","locationAddress":"Test Road","finalIssueType":"ROAD_CRACK",
                 "finalSeverity":"HIGH","predictionState":"MANUAL"}
                """.getBytes());
        MockMultipartFile image = new MockMultipartFile(
                "image", "road.png", "image/png", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/complaints").file(request).file(image))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Final issue type must be DOMESTIC_TRASH, ILLEGAL_PARKING, DAMAGED_SIGN, or POTHOLE"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @WithMockUser(roles = "WARD_OFFICER")
    void managerCanListComplaintsAsSafeResponseDtos() throws Exception {
        ComplaintResponse complaint = new ComplaintResponse(44L, 5L, "Citizen",
                "/uploads/complaints/test.png", "Pothole", "Test Road", null, null,
                null, null, null, null, ComplaintIssueType.POTHOLE, ComplaintSeverity.HIGH,
                ComplaintPredictionState.MANUAL, ComplaintStatus.SUBMITTED, null,
                LocalDateTime.now(), LocalDateTime.now());
        when(complaintService.listForManager(nullable(User.class))).thenReturn(List.of(complaint));

        mockMvc.perform(get("/api/complaints"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(44))
                .andExpect(jsonPath("$[0].finalSeverity").value("HIGH"));
    }

    @Test
    @WithMockUser(roles = "CITIZEN")
    void citizenCannotUseAdministrativeComplaintList() throws Exception {
        mockMvc.perform(get("/api/complaints"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CITIZEN")
    void citizenCanListOwnComplaints() throws Exception {
        ComplaintResponse complaint = new ComplaintResponse(45L, 5L, "Citizen",
                "/uploads/complaints/test.png", "Pothole", "Test Road", null, null,
                null, null, null, null, ComplaintIssueType.POTHOLE, ComplaintSeverity.HIGH,
                ComplaintPredictionState.MANUAL, ComplaintStatus.SUBMITTED, null,
                LocalDateTime.now(), LocalDateTime.now());
        when(complaintService.listForCitizen(nullable(User.class))).thenReturn(List.of(complaint));

        mockMvc.perform(get("/api/complaints/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(45))
                .andExpect(jsonPath("$[0].reportingUserId").value(5));
    }

    @Test
    @WithMockUser(roles = "MUNICIPAL_ADMIN")
    void managerCannotUseCitizenComplaintList() throws Exception {
        mockMvc.perform(get("/api/complaints/my"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CITIZEN")
    void aiOutageReturnsServiceUnavailableWithManualFallbackMessage() throws Exception {
        when(imageAnalysisService.analyze(any())).thenThrow(new ResponseStatusException(
                SERVICE_UNAVAILABLE, "AI analysis is temporarily unavailable; continue with manual classification"));
        MockMultipartFile image = new MockMultipartFile(
                "image", "road.png", "image/png", new byte[]{1});

        mockMvc.perform(multipart("/api/complaints/analyze").file(image))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(
                        "AI analysis is temporarily unavailable; continue with manual classification"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("Service Unavailable"))
                .andExpect(jsonPath("$.path").value("/api/complaints/analyze"));
    }
}
