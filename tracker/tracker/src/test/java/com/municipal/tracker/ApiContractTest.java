package com.municipal.tracker;

import com.municipal.tracker.exception.ApiExceptionHandler;
import com.municipal.tracker.controller.WardController;
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
        assertThat(response.getBody()).containsEntry("message", "Invalid value");
    }

    @Test
    void authenticationFailuresHaveSafeUnauthorizedBody() {
        var response = new ApiExceptionHandler().unauthorized();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("message", "Invalid email or password");
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
}
