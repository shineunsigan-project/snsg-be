package com.shineunsigan.snsg.auth.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record ProfileImageResponse(

        @Schema(description = "저장된 사진 경로. 가입 요청의 profileImageUrl에 그대로 넣는다",
                example = "/images/profiles/0a1b2c3d-4e5f-6071-8293-a4b5c6d7e8f9.png")
        String profileImageUrl) {
}
