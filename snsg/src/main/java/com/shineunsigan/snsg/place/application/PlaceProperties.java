package com.shineunsigan.snsg.place.application;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 장소 적재 정책.
 *
 * @param closeMaxRatio 폐업 처리 안전장치. 한 지역에서 닫을 행이 열려 있는 행 대비 이 비율을 넘으면
 *                      원천 장애로 보고 그 지역은 닫지 않는다. 같으면 닫는다
 */
@ConfigurationProperties(prefix = "place")
public record PlaceProperties(BigDecimal closeMaxRatio) {
}
