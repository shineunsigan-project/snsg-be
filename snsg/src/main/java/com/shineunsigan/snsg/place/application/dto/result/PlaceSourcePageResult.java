package com.shineunsigan.snsg.place.application.dto.result;

import java.util.List;

/**
 * 원천 페이지 하나를 받은 결과. 실패(호출 예외·시간 초과·HTTP 오류·응답 해석 실패·결과 코드 오류)면 이유만 담는다.
 *
 * @param noData        원천이 "데이터 없음"으로 답했다. 다시 불러도 같은 답이라 재시도하지 않는다
 * @param failureReason 실패 이유(결과 코드·메시지, HTTP 상태, 예외 종류). 요청 URL은 인증키가 들어 있어 담지 않는다
 * @param totalCount    그 지역의 전체 건수
 * @param rows          이 페이지의 원천 행
 */
public record PlaceSourcePageResult(boolean success, boolean noData, String failureReason, int totalCount,
        List<PlaceSourceRow> rows) {

    public static PlaceSourcePageResult success(int totalCount, List<PlaceSourceRow> rows) {
        return new PlaceSourcePageResult(true, false, "", totalCount, rows);
    }

    public static PlaceSourcePageResult failure(String reason) {
        return new PlaceSourcePageResult(false, false, reason, 0, List.of());
    }

    public static PlaceSourcePageResult noData(String reason) {
        return new PlaceSourcePageResult(false, true, reason, 0, List.of());
    }
}
