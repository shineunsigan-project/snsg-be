package com.shineunsigan.snsg.auth.presentation.dto.response;

import com.shineunsigan.snsg.auth.application.dto.result.TokenResult;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * refresh token은 담지 않는다. HttpOnly 쿠키로만 나간다.
 */
public record TokenResponse(

        @Schema(description = "인증이 필요한 API의 Authorization 헤더에 Bearer로 넣는다. 30분 뒤 만료되며 메모리에만 보관한다")
        String accessToken) {

    public static TokenResponse from(TokenResult result) {
        return new TokenResponse(result.accessToken());
    }
}
