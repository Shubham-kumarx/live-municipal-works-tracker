package com.municipal.tracker;

import com.municipal.tracker.exception.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class ApiContractTest {
    @Test
    void t03IllegalArgumentsHaveSafeBadRequestBody() {
        var response = new ApiExceptionHandler().badRequest(new IllegalArgumentException("Invalid value"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("message", "Invalid value");
    }
}
