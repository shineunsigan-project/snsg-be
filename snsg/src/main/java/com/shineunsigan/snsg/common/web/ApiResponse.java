package com.shineunsigan.snsg.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.shineunsigan.snsg.common.exception.ErrorCode;

/**
 * 모든 API가 쓰는 응답 봉투.
 * <p>
 * 성공 여부는 {@code isSuccess}로 본다. 값은 팩토리 메서드가 채우므로 HTTP 상태와 어긋나지 않는다.
 * HTTP 상태 코드는 계속 정확하게 내려준다. 상태를 200으로 고정하고 본문으로만 성공을 알리는 용도가 아니다.
 * <p>
 * {@code code}와 {@code message}는 실패 전용이다. 클라이언트의 화면 분기는 {@code code}로 한다.
 * 성공 응답에서는 두 값이 비어 있어 JSON에 나가지 않는다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean isSuccess, String code, String message, T data) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, null, null, data);
    }

    public static ApiResponse<Void> success() {
        return new ApiResponse<>(true, null, null, null);
    }

    public static ApiResponse<Void> error(ErrorCode errorCode) {
        return new ApiResponse<>(false, errorCode.name(), errorCode.message(), null);
    }
}
