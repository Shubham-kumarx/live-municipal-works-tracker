package com.municipal.tracker;

import com.municipal.tracker.dto.WardResponse;
import com.municipal.tracker.model.Ward;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class WardResponseTest {
    @Test
    void mapsEveryExistingWardApiField() {
        Ward ward = new Ward();
        ward.setId(3L);
        ward.setWardNumber("W-03");
        ward.setWardName("Central");
        ward.setCity("Delhi");
        ward.setDistrict("North");
        ward.setState("Delhi");
        ward.setCenterLatitude(28.7);
        ward.setCenterLongitude(77.2);
        ward.setBoundaryGeoJson("{}");
        ward.setActive(true);
        ward.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
        ward.setUpdatedAt(LocalDateTime.of(2026, 1, 2, 10, 0));

        WardResponse response = WardResponse.from(ward);

        assertThat(response.id()).isEqualTo(3L);
        assertThat(response.wardNumber()).isEqualTo("W-03");
        assertThat(response.wardName()).isEqualTo("Central");
        assertThat(response.centerLatitude()).isEqualTo(28.7);
        assertThat(response.centerLongitude()).isEqualTo(77.2);
        assertThat(response.boundaryGeoJson()).isEqualTo("{}");
        assertThat(response.active()).isTrue();
        assertThat(response.createdAt()).isEqualTo(ward.getCreatedAt());
        assertThat(response.updatedAt()).isEqualTo(ward.getUpdatedAt());
    }
}
