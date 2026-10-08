package com.shineunsigan.snsg.user.presentation;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shineunsigan.snsg.common.web.ApiResponse;
import com.shineunsigan.snsg.user.application.UserCommandService;
import com.shineunsigan.snsg.user.domain.constants.WithdrawReason;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

@Tag(name = "사용자")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserCommandService userCommandService;

    @DeleteMapping("/me")
    @Operation(summary = "탈퇴",
            description = """
                    사용자를 탈퇴시킨다. 닉네임·생년월일·프로필 사진은 비식별 값으로 바뀌고 올렸던 사진 파일은 지워진다.
                    카카오 연결도 끊어지므로 다시 로그인하면 동의 화면부터 시작한다.
                    되돌릴 수 없고, 다시 가입하면 이전 활동과 이어지지 않는 새 계정이 된다.
                    refresh token은 즉시 무효가 되지만 쿠키는 남는다. 탈퇴 직후 로그아웃 API를 한 번 호출해 쿠키를 비운다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "탈퇴 완료"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "토큰이 없거나 유효하지 않다")})
    public ResponseEntity<ApiResponse<Void>> withdraw(@AuthenticationPrincipal UUID userId) {
        userCommandService.withdraw(userId, WithdrawReason.USER_REQUESTED);

        return ResponseEntity.ok(ApiResponse.success());
    }
}
