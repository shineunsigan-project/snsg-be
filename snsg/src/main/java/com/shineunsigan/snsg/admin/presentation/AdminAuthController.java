package com.shineunsigan.snsg.admin.presentation;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shineunsigan.snsg.admin.application.AdminLoginFacade;
import com.shineunsigan.snsg.admin.application.AdminReissueTokenFacade;
import com.shineunsigan.snsg.admin.application.SignUpAdminFacade;
import com.shineunsigan.snsg.admin.application.dto.result.AdminTokenResult;
import com.shineunsigan.snsg.admin.domain.exception.AdminErrorCode;
import com.shineunsigan.snsg.admin.presentation.dto.request.AdminLoginRequest;
import com.shineunsigan.snsg.admin.presentation.dto.request.AdminSignUpRequest;
import com.shineunsigan.snsg.admin.presentation.dto.response.AdminTokenResponse;
import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.common.web.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "어드민 인증", description = "어드민 가입 신청, 로그인, 토큰 재발급, 로그아웃")
@RestController
@RequestMapping("/api/admin/v1/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final SignUpAdminFacade signUpAdminFacade;
    private final AdminLoginFacade adminLoginFacade;
    private final AdminReissueTokenFacade adminReissueTokenFacade;
    private final AdminCookieFactory cookieFactory;

    @SecurityRequirements
    @PostMapping("/signup")
    @Operation(summary = "어드민 가입 신청",
            description = """
                    가입 화면에서 제출하면 부른다. 승인 대기 상태로 저장되고 토큰·쿠키는 주지 않는다.
                    MASTER가 승인하기 전까지는 로그인하면 403 ADMIN_PENDING_APPROVAL이다.
                    필드 형식이 틀리면 400 INVALID_REQUEST와 함께 errors에 틀린 필드와 문구가 담긴다.
                    team에 목록 밖의 값을 보내거나 없는 날짜를 보내면 JSON 변환 단계에서 실패해 400 INVALID_REQUEST만 오고 errors는 없다.
                    성공한 요청을 다시 보내면 같은 아이디가 이미 있으므로 409 DUPLICATED_LOGIN_ID다. 응답 전까지 버튼을 막는다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                    description = "가입 신청 완료. data는 없다. 승인 대기 안내 후 로그인 화면으로 보낸다"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "INVALID_REQUEST: 필드 형식 오류. errors의 필드별 문구를 입력칸 아래에 보여준다"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "DUPLICATED_LOGIN_ID: 이미 쓰이는 아이디. 다른 아이디를 입력받는다")})
    public ResponseEntity<ApiResponse<Void>> signUp(@Valid @RequestBody AdminSignUpRequest request) {
        signUpAdminFacade.signUp(request.toCommand());

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success());
    }

    @SecurityRequirements
    @PostMapping("/login")
    @Operation(summary = "어드민 로그인",
            description = """
                    아이디·비밀번호로 로그인한다. access token을 본문으로, refresh token을 adminRefreshToken 쿠키로 준다.
                    쿠키를 받아야 하므로 credentials를 포함해 호출한다.
                    아이디가 없는 것과 비밀번호가 틀린 것은 구분하지 않고 401 INVALID_ADMIN_CREDENTIALS다.
                    같은 아이디로 5회 연속 실패하면 15분 동안 잠기고(401 ADMIN_LOGIN_LOCKED), 잠긴 동안은 비밀번호가 맞아도 같은 코드다.
                    비밀번호가 맞았지만 승인 대기면 403 ADMIN_PENDING_APPROVAL이다.
                    로그인할 때마다 새 기기 세션이 생긴다. 다른 기기의 로그인은 끊기지 않는다.
                    세션은 로그인 후 12시간에 끝난다. 재발급해도 늘어나지 않는다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "로그인 성공. adminRefreshToken 쿠키가 함께 나간다"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "INVALID_REQUEST: 아이디나 비밀번호가 비었거나 64자를 넘는다"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = """
                            INVALID_ADMIN_CREDENTIALS: 아이디가 없거나 비밀번호가 틀렸다. 아이디 또는 비밀번호가 틀렸다고 안내한다.
                            ADMIN_LOGIN_LOCKED: 같은 아이디로 5회 연속 실패해 15분 동안 잠겼다. 잠시 후 다시 시도하라고 안내한다"""),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                    description = "ADMIN_PENDING_APPROVAL: 비밀번호는 맞지만 아직 승인되지 않았다. 승인 대기 안내를 보여준다")})
    public ResponseEntity<ApiResponse<AdminTokenResponse>> login(@Valid @RequestBody AdminLoginRequest request) {
        AdminTokenResult result = adminLoginFacade.login(request.toCommand());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.refreshToken(result.refreshToken(), result.refreshExpiresAt()).toString())
                .body(ApiResponse.success(AdminTokenResponse.from(result)));
    }

    @SecurityRequirements
    @PostMapping("/tokens")
    @Operation(summary = "어드민 access token 재발급",
            description = """
                    adminRefreshToken 쿠키로 새 access token을 발급한다. credentials를 포함해 호출한다.
                    어드민 앱에 들어올 때(새로고침 포함) 먼저 부르고, 401을 받은 요청을 다시 보내기 전에 부른다.
                    발급하면 같은 기기의 이전 refresh token은 무효가 되므로 동시에 여러 번 호출하지 않는다.
                    재발급해도 세션은 늘어나지 않는다. 로그인 후 12시간이 지나면 401이고 다시 로그인해야 한다.
                    새 쿠키의 수명은 로그인 후 12시간까지 남은 시간이다. 승인이 풀린 어드민은 재발급되지 않는다.
                    401 INVALID_ADMIN_REFRESH_TOKEN이면 로그인 화면으로 보낸다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "재발급 성공. 새 adminRefreshToken 쿠키가 함께 나간다"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "INVALID_ADMIN_REFRESH_TOKEN: 쿠키가 없거나 만료·위조·회전됐거나 로그아웃됐거나, 어드민이 승인 상태가 아니다. 로그인 화면으로 보낸다")})
    public ResponseEntity<ApiResponse<AdminTokenResponse>> reissue(
            @CookieValue(value = AdminCookieFactory.REFRESH_TOKEN, required = false) String refreshToken) {
        requireRefreshToken(refreshToken);
        AdminTokenResult result = adminReissueTokenFacade.reissue(refreshToken);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.refreshToken(result.refreshToken(), result.refreshExpiresAt()).toString())
                .body(ApiResponse.success(AdminTokenResponse.from(result)));
    }

    @DeleteMapping("/tokens")
    @Operation(summary = "어드민 로그아웃",
            description = """
                    adminRefreshToken 쿠키가 가리키는 이 기기의 세션만 끝내고 쿠키를 만료시킨다. 다른 기기의 로그인은 유지된다.
                    쿠키를 읽으므로 credentials를 포함해 호출한다.
                    쿠키가 없거나 이미 끝난 세션이어도 200이다. 다시 호출해도 안전하다.
                    이미 발급된 access token은 만료까지 유효하므로 클라이언트에서 버린다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "로그아웃 완료. adminRefreshToken 쿠키를 만료시키는 Set-Cookie가 함께 나간다"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "UNAUTHENTICATED: access token이 없거나 만료·무효. 토큰을 버리고 로그인 화면으로 간다")})
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal UUID adminId,
            @CookieValue(value = AdminCookieFactory.REFRESH_TOKEN, required = false) String refreshToken) {
        adminReissueTokenFacade.logout(adminId, refreshToken);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.expiredRefreshToken().toString())
                .body(ApiResponse.success());
    }

    /**
     * 쿠키가 없는 것은 요청 형식 오류(400)가 아니라 인증 실패(401)다.
     */
    private void requireRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(AdminErrorCode.INVALID_ADMIN_REFRESH_TOKEN);
        }
    }
}
