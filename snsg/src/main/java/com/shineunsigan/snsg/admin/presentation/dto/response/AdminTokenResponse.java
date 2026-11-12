package com.shineunsigan.snsg.admin.presentation.dto.response;

import com.shineunsigan.snsg.admin.application.dto.result.AdminTokenResult;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * refresh token은 담지 않는다. HttpOnly 쿠키({@code adminRefreshToken})로만 나간다.
 */
public record AdminTokenResponse(

        @Schema(description = "어드민 API의 Authorization 헤더에 Bearer로 넣는다. 30분 뒤 만료되며, 로그인 후 12시간(세션 만료)이 30분 안에 오면 그때 만료된다. 메모리에만 보관한다. 사용자 앱의 토큰과 섞지 않는다")
        String accessToken) {

    public static AdminTokenResponse from(AdminTokenResult result) {
        return new AdminTokenResponse(result.accessToken());
    }
}
