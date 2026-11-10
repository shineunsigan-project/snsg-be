package com.shineunsigan.snsg.auth.presentation;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shineunsigan.snsg.auth.application.CheckWithdrawalFacade;
import com.shineunsigan.snsg.auth.application.WithdrawFacade;
import com.shineunsigan.snsg.auth.presentation.dto.response.WithdrawalEligibilityResponse;
import com.shineunsigan.snsg.common.web.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 회원 탈퇴. 탈퇴는 user의 일이지만 확인할 데이터(남은 일정)가 schedule에 있어 auth가 흐름을 조합한다.
 * 경로는 사용자 API와 같은 {@code /api/v1/users/me} 아래에 두고, 문서에서도 사용자 태그로 묶는다.
 */
@Tag(name = "사용자")
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class WithdrawalController {

    private final CheckWithdrawalFacade checkWithdrawalFacade;
    private final WithdrawFacade withdrawFacade;
    private final AuthCookieFactory cookieFactory;

    @GetMapping("/withdrawal-eligibility")
    @Operation(summary = "탈퇴 가능 여부",
            description = """
                    지금 탈퇴할 수 있는지와 탈퇴할 수 있게 되는 시각을 알려준다. 회원 탈퇴 화면(MY-04)에서 [회원 탈퇴]를 누르면 부른다.
                    withdrawable이 true면 재확인 팝업을 띄우고, 사용자가 확인하면 탈퇴 API(DELETE /api/v1/users/me)를 부른다.
                    false면 탈퇴 불가 팝업에 availableAt(그 시각 이후 탈퇴 가능)을 안내한다.
                    탈퇴를 막는 일정: 내가 확정 참여했거나 개설한 일정 중 취소되지 않았고(예정·완료 모두) 시작 후 24시간이 지나지 않은 것.
                    취소된 일정과 참여를 취소한 일정은 막지 않는다. 시작 + 24시간이 정확히 지금이면 막지 않는다.
                    availableAt은 막는 일정 중 시작 시각이 가장 늦은 일정의 시작 + 24시간이다. 한국 시간 ISO-8601(오프셋 없음). 탈퇴할 수 있으면 null이다.
                    결과는 안내용이다. 이 응답 뒤에 일정에 참여하면 탈퇴 API가 409 WITHDRAWAL_BLOCKED로 막을 수 있다.
                    조회만 하므로 같은 요청을 몇 번 다시 보내도 된다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "판정 결과. 탈퇴할 수 없어도 200이고 data.withdrawable=false로 알려준다"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "UNAUTHENTICATED: 토큰이 없거나 만료·무효. 재발급 후 다시 부르거나 로그인 화면으로 간다")})
    public ResponseEntity<ApiResponse<WithdrawalEligibilityResponse>> checkWithdrawal(
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(
                WithdrawalEligibilityResponse.from(checkWithdrawalFacade.check(userId))));
    }

    @DeleteMapping
    @Operation(summary = "탈퇴",
            description = """
                    사용자를 탈퇴시킨다. 회원 탈퇴 화면(MY-04)의 재확인 팝업에서 확인을 누르면 부른다.
                    먼저 탈퇴 가능 여부 API와 같은 기준으로 남은 일정을 확인하고, 탈퇴를 막는 일정이 있으면 409 WITHDRAWAL_BLOCKED로 아무것도 바꾸지 않는다.
                    계정은 삭제 표시되고, 이름·이메일·닉네임·생년월일·프로필 사진 원본은 90일 보관한 뒤 비식별한다.
                    탈퇴한 닉네임은 90일 동안 다른 사람이 쓸 수 없다.
                    카카오 연결도 끊어지므로 다시 로그인하면 동의 화면부터 시작한다.
                    되돌릴 수 없고, 다시 가입하면 이전 활동과 이어지지 않는 새 계정이 된다.
                    모든 기기의 refresh token이 무효가 되고, 성공 응답이 refreshToken 쿠키를 만료시킨다(Set-Cookie, Max-Age=0).
                    브라우저가 쿠키 만료를 적용하도록 credentials를 포함해 호출한다. 이미 발급된 access token은 클라이언트에서 버리고 로그인 전 화면으로 보낸다.
                    두 번 눌려 다시 보내면 두 번째 요청은 이미 탈퇴한 사용자라 404 USER_NOT_FOUND다. 탈퇴는 끝난 것이므로 성공과 같이 처리한다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "탈퇴 완료. refreshToken 쿠키를 만료시키는 Set-Cookie가 함께 나간다"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "UNAUTHENTICATED: 토큰이 없거나 만료·무효. 재발급 후 다시 부르거나 로그인 화면으로 간다"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "USER_NOT_FOUND: 토큰의 사용자가 없거나 이미 탈퇴했고, 탈퇴를 막는 일정은 없다. 탈퇴가 끝난 것으로 보고 토큰을 버린 뒤 로그인 전 화면으로 보낸다"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = """
                            WITHDRAWAL_BLOCKED: 확정 참여했거나 개설한 일정 중 취소되지 않았고 시작 후 24시간이 지나지 않은 것이 있다. 쿠키는 그대로다.
                            message(모든 일정 취소 혹은 진행 중 이후 24시간이 지나야 탈퇴할 수 있어요.)를 보여주고,
                            탈퇴 가능해지는 시각은 탈퇴 가능 여부 API의 availableAt으로 다시 받아 안내한다.
                            남은 일정 확인이 사용자 확인보다 먼저라, 카카오 연결 끊기로 이미 탈퇴됐고 막는 일정이 남은 사용자가 남은 access token으로 부르면 404가 아니라 이 코드다""")})
    public ResponseEntity<ApiResponse<Void>> withdraw(@AuthenticationPrincipal UUID userId) {
        withdrawFacade.withdraw(userId);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.expired(AuthCookieFactory.REFRESH_TOKEN).toString())
                .body(ApiResponse.success());
    }
}
