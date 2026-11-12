package com.shineunsigan.snsg.place.domain.exception;

import com.shineunsigan.snsg.common.exception.ErrorCode;
import com.shineunsigan.snsg.common.exception.ErrorType;

import lombok.RequiredArgsConstructor;

/**
 * 어드민 장소 검수·수정·등록에서 나는 오류. 형식 오류의 문구는 요청 DTO의 Bean Validation 문구와 같다.
 */
@RequiredArgsConstructor
public enum PlaceErrorCode implements ErrorCode {

    PLACE_NOT_FOUND(ErrorType.NOT_FOUND, "찾을 수 없는 장소예요."),
    INVALID_PLACE_TYPE(ErrorType.INVALID, "장소 유형을 골라주세요."),
    INVALID_REVIEW_STATUS(ErrorType.INVALID, "검수 상태는 승인 또는 거절만 고를 수 있어요."),
    UNCLASSIFIED_NOT_APPROVABLE(ErrorType.INVALID, "유형을 정하지 않은 장소는 승인할 수 없어요. 유형을 골라주세요."),
    INVALID_PLACE_NAME(ErrorType.INVALID, "장소명은 1~100자로 적어주세요."),
    INVALID_ROAD_ADDRESS(ErrorType.INVALID, "도로명 주소는 1~200자로 적어주세요."),
    INVALID_ADDRESS_DETAIL(ErrorType.INVALID, "상세주소는 100자 이내로 적어주세요."),
    INVALID_LATITUDE(ErrorType.INVALID, "위도는 33~39 사이로 적어주세요."),
    INVALID_LONGITUDE(ErrorType.INVALID, "경도는 124~132 사이로 적어주세요."),
    INVALID_REGION(ErrorType.INVALID, "선택할 수 없는 지역이에요. 지역을 다시 골라주세요."),
    INVALID_CURSOR(ErrorType.INVALID, "목록을 처음부터 다시 불러주세요.");

    private final ErrorType errorType;
    private final String message;

    @Override
    public ErrorType errorType() {
        return errorType;
    }

    @Override
    public String message() {
        return message;
    }
}
