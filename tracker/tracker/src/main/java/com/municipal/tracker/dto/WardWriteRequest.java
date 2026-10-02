package com.municipal.tracker.dto;

import com.municipal.tracker.model.Ward;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WardWriteRequest {
    @NotBlank(message = "Ward number is required") @Size(max = 100) private String wardNumber;
    @NotBlank(message = "Ward name is required") @Size(max = 255) private String wardName;
    @NotBlank(message = "City is required") @Size(max = 255) private String city;
    @NotBlank(message = "District is required") @Size(max = 255) private String district;
    @NotBlank(message = "State is required") @Size(max = 255) private String state;
    @DecimalMin("-90.0") @DecimalMax("90.0") private Double centerLatitude;
    @DecimalMin("-180.0") @DecimalMax("180.0") private Double centerLongitude;
    @Size(max = 100000) private String boundaryGeoJson;

    public Ward toEntity() {
        Ward ward = new Ward();
        ward.setWardNumber(wardNumber); ward.setWardName(wardName); ward.setCity(city);
        ward.setDistrict(district); ward.setState(state); ward.setCenterLatitude(centerLatitude);
        ward.setCenterLongitude(centerLongitude); ward.setBoundaryGeoJson(boundaryGeoJson);
        return ward;
    }
}
