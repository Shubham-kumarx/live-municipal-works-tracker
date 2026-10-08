package com.municipal.tracker.dto;

import com.municipal.tracker.model.Ward;

import java.time.LocalDateTime;

public record WardResponse(
        Long id,
        String wardNumber,
        String wardName,
        String city,
        String district,
        String state,
        Double centerLatitude,
        Double centerLongitude,
        String boundaryGeoJson,
        Boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static WardResponse from(Ward ward) {
        return new WardResponse(
                ward.getId(),
                ward.getWardNumber(),
                ward.getWardName(),
                ward.getCity(),
                ward.getDistrict(),
                ward.getState(),
                ward.getCenterLatitude(),
                ward.getCenterLongitude(),
                ward.getBoundaryGeoJson(),
                ward.getActive(),
                ward.getCreatedAt(),
                ward.getUpdatedAt());
    }
}
