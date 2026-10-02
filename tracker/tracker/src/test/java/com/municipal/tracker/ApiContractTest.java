package com.municipal.tracker;

import com.municipal.tracker.exception.ApiExceptionHandler;
import com.municipal.tracker.controller.WardController;
import com.municipal.tracker.dto.WardProjectStatsResponse;
import com.municipal.tracker.model.Ward;
import com.municipal.tracker.service.WardService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

class ApiContractTest {
    @Test
    void t03IllegalArgumentsHaveSafeBadRequestBody() {
        var response = new ApiExceptionHandler().badRequest(new IllegalArgumentException("Invalid value"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Invalid value");
    }

    @Test
    void authenticationFailuresHaveSafeUnauthorizedBody() {
        var response = new ApiExceptionHandler().unauthorized();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Invalid email or password");
    }

    @Test
    void wardControllerReturnsDtoDataRatherThanEntities() {
        WardService wards = mock(WardService.class);
        Ward ward = new Ward();
        ward.setId(1L);
        ward.setWardName("Central");
        when(wards.getAllActiveWards()).thenReturn(List.of(ward));

        var response = new WardController(wards).getAllActiveWards();

        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).id()).isEqualTo(1L);
    }

    @Test
    void wardProjectStatisticsHaveAStableTypedResponse() {
        WardProjectStatsResponse response = new WardProjectStatsResponse(
                10, 2, 3, 4, 1, 1_000_000.0, 600_000.0);

        assertThat(response.totalProjects()).isEqualTo(10);
        assertThat(response.sanctioned()).isEqualTo(2);
        assertThat(response.inProgress()).isEqualTo(3);
        assertThat(response.completed()).isEqualTo(4);
        assertThat(response.delayed()).isEqualTo(1);
        assertThat(response.totalBudgetAllocated()).isEqualTo(1_000_000.0);
        assertThat(response.totalBudgetSpent()).isEqualTo(600_000.0);
    }
}
