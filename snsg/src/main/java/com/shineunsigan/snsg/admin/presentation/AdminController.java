package com.shineunsigan.snsg.admin.presentation;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shineunsigan.snsg.admin.application.AdminQueryService;
import com.shineunsigan.snsg.admin.presentation.dto.response.AdminMeResponse;
import com.shineunsigan.snsg.common.web.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "어드민", description = "어드민 계정")
@RestController
@RequestMapping("/api/admin/v1/admins")
@RequiredArgsConstructor
public class AdminController {

    private final AdminQueryService adminQueryService;

    @GetMapping("/me")
    @Operation(summary = "내 정보",
            description = """
                    로그인한 어드민의 정보를 돌려준다. 로그인·재발급 직후 불러 화면 상단의 이름과 역할별 메뉴(MASTER 전용 메뉴)를 그린다.
                    조회만 하므로 같은 요청을 몇 번 다시 보내도 된다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "내 정보"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "UNAUTHENTICATED: 토큰이 없거나 만료·무효이거나, 어드민이 승인 상태가 아니다. 재발급 후 다시 부르고, 재발급도 401이면 로그인 화면으로 간다")})
    public ResponseEntity<ApiResponse<AdminMeResponse>> getMe(@AuthenticationPrincipal UUID adminId) {
        return ResponseEntity.ok(ApiResponse.success(AdminMeResponse.from(adminQueryService.getMe(adminId))));
    }
}
