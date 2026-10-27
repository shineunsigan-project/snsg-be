package com.shineunsigan.snsg.place.application.dto.result;

import com.shineunsigan.snsg.place.domain.model.Region;

public record RegionResult(String code, String sidoName, String sigunguName, boolean active) {

    public static RegionResult from(Region region) {
        return new RegionResult(region.getCode(), region.getSidoName(), region.getSigunguName(), region.isActive());
    }
}
