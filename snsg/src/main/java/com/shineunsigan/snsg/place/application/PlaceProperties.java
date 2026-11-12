package com.shineunsigan.snsg.place.application;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.shineunsigan.snsg.place.domain.constants.PlaceType;

/**
 * 장소 적재 정책과 어드민 장소 목록의 페이지 크기. 실행 시각({@code place.import-cron})은 {@code @Scheduled}가 자리표시자로 읽어 여기 두지 않는다.
 *
 * @param closeMaxRatio      폐업 처리 안전장치. 한 지역에서 닫을 행이 열려 있는 행 대비 이 비율을 넘으면
 *                           원천 장애로 보고 그 지역은 닫지 않는다. 같으면 닫는다
 * @param importTimeout      원천 호출 하나의 연결·읽기 시간 초과
 * @param importMaxAttempts  원천 페이지 하나를 받는 최대 시도 횟수(첫 시도 포함)
 * @param semasCategoryCodes 상호명과 무관하게 적재하는 상가정보 상권업종 소분류 코드
 * @param nameKeywords       그 밖 업종이라도 상호명(+지점명)에 들면 적재하는 키워드. 대소문자 무시
 * @param typeKeywords       유형 추정 키워드. 상호명(+지점명)에 든 키워드의 유형으로 추정한다. 대소문자 무시
 * @param pageSizeDefault    어드민 장소 목록 한 페이지의 기본 개수(size 생략 시)
 * @param pageSizeMax        어드민 장소 목록 한 페이지의 최대 개수. 넘게 요청하면 이 값으로 줄인다
 */
@ConfigurationProperties(prefix = "place")
public record PlaceProperties(BigDecimal closeMaxRatio, Duration importTimeout, int importMaxAttempts,
        List<String> semasCategoryCodes, List<String> nameKeywords, Map<PlaceType, List<String>> typeKeywords,
        int pageSizeDefault, int pageSizeMax) {
}
