package com.shineunsigan.snsg.auth.presentation;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RestController;

import com.shineunsigan.snsg.auth.application.CompleteSignUpUseCase;
import com.shineunsigan.snsg.auth.application.ReissueTokenUseCase;
import com.shineunsigan.snsg.auth.application.SocialLoginUseCase;
import com.shineunsigan.snsg.auth.application.dto.result.SocialAuthResult;
import com.shineunsigan.snsg.auth.application.dto.result.TokenResult;
import com.shineunsigan.snsg.auth.domain.constants.SocialProvider;
import com.shineunsigan.snsg.auth.domain.exception.AuthErrorCode;
import com.shineunsigan.snsg.auth.presentation.dto.request.SignUpRequest;
import com.shineunsigan.snsg.auth.presentation.dto.response.ProfileImageResponse;
import com.shineunsigan.snsg.auth.presentation.dto.response.SignUpSessionResponse;
import com.shineunsigan.snsg.auth.presentation.dto.response.TokenResponse;
import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.common.web.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "인증", description = "소셜 로그인, 가입, 토큰 재발급")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SocialLoginUseCase socialLoginUseCase;
    private final CompleteSignUpUseCase completeSignUpUseCase;
    private final ReissueTokenUseCase reissueTokenUseCase;
    private final AuthCookieFactory cookieFactory;

    @Value("${app.frontend-redirect-uri}")
    private String frontendRedirectUri;

    @SecurityRequirements
    @GetMapping("/{provider}/authorize")
    @Operation(summary = "소셜 로그인 시작",
            description = """
                    소셜 인증 페이지로 302 리다이렉트한다. 클라이언트는 소셜 앱 키를 갖지 않는다.
                    **fetch나 XHR로 부르지 않는다.** 브라우저를 이동시켜야 한다(window.location.href = ...).
                    fetch는 리다이렉트를 따라가 소셜 도메인에 닿고, 소셜은 CORS 헤더를 주지 않아 실패한다.
                    같은 이유로 Swagger의 Try it out에서도 실패한다. 주소창에 직접 입력해 시험한다.""")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "302",
            description = "소셜 인증 페이지로 이동. Location 헤더에 주소가 담긴다", content = @Content))
    public ResponseEntity<Void> authorize(@PathVariable SocialProvider provider) {
        return ResponseEntity.status(302)
                .location(URI.create(socialLoginUseCase.authorizeUrl(provider)))
                .build();
    }

    @SecurityRequirements
    @GetMapping("/{provider}/callback")
    @Operation(summary = "소셜 인증 콜백",
            description = """
                    소셜이 호출하는 주소다. 클라이언트가 직접 부르지 않는다.
                    기존 회원이면 refreshToken 쿠키를, 신규면 signUpToken 쿠키를 심고 프론트엔드로 302 리다이렉트한다.
                    신규는 쿼리로 signUp=true가 붙는다. 토큰은 URL에 담지 않는다.""")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "302",
            description = "프론트엔드로 이동. 쿠키가 심어진다", content = @Content))
    public ResponseEntity<Void> callback(@PathVariable SocialProvider provider, @RequestParam("code") String code) {
        SocialAuthResult result = socialLoginUseCase.authenticate(provider, code);

        if (result instanceof SocialAuthResult.LoggedIn loggedIn) {
            return redirectToFrontend(cookieFactory.refreshToken(loggedIn.tokens().refreshToken()), false);
        }
        SocialAuthResult.SignUpRequired signUpRequired = (SocialAuthResult.SignUpRequired) result;
        return redirectToFrontend(cookieFactory.signUpToken(signUpRequired.signUpToken()), true);
    }

    @SecurityRequirements
    @GetMapping("/signup/session")
    @Operation(summary = "가입 세션 조회",
            description = """
                    프로필 완성 화면의 기본값(소셜 닉네임·사진)을 돌려준다. signUpToken 쿠키가 필요하므로 credentials를 포함해 호출한다.
                    세션이 만료되면 401 INVALID_SIGNUP_TOKEN이 오고, 소셜 로그인부터 다시 해야 한다.""")
    public ResponseEntity<ApiResponse<SignUpSessionResponse>> signUpSession(
            @CookieValue(value = AuthCookieFactory.SIGN_UP_TOKEN, required = false) String signUpToken) {
        requireCookie(signUpToken, AuthErrorCode.INVALID_SIGNUP_TOKEN);

        return ResponseEntity.ok(
                ApiResponse.success(SignUpSessionResponse.from(completeSignUpUseCase.findSession(signUpToken))));
    }

    @SecurityRequirements
    @PostMapping(value = "/signup/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "프로필 사진 업로드",
            description = """
                    가입 중에 프로필 사진을 바꿀 때 쓴다. 돌려받은 경로를 가입 요청의 profileImageUrl에 넣는다.
                    5MB 이하의 JPG·PNG만 받는다. 형식은 파일 내용으로 판별하므로 확장자를 바꿔 올려도 거부된다.""")
    public ResponseEntity<ApiResponse<ProfileImageResponse>> uploadProfileImage(
            @CookieValue(value = AuthCookieFactory.SIGN_UP_TOKEN, required = false) String signUpToken,
            @RequestPart("image") MultipartFile image) throws IOException {
        requireCookie(signUpToken, AuthErrorCode.INVALID_SIGNUP_TOKEN);

        String profileImageUrl = completeSignUpUseCase.uploadProfileImage(signUpToken, image.getBytes());

        return ResponseEntity.ok(ApiResponse.success(new ProfileImageResponse(profileImageUrl)));
    }

    @SecurityRequirements
    @PostMapping("/signup")
    @Operation(summary = "가입 완료",
            description = """
                    프로필을 받아 가입을 마치고 access token을 돌려준다. signUpToken 쿠키가 필요하므로 credentials를 포함해 호출한다.
                    소셜 정보는 서버가 보관한 가입 세션에서 꺼낸다. 가입이 끝나면 signUpToken은 무효가 된다.
                    필드 형식이 틀리면 400 INVALID_REQUEST와 함께 errors에 틀린 필드와 문구가 담긴다.
                    가입일 기준 만 18세 미만이면 400 UNDERAGE, 닉네임이 이미 쓰이고 있으면(대소문자 무시) 409 DUPLICATED_NICKNAME이다.""")
    public ResponseEntity<ApiResponse<TokenResponse>> signUp(
            @CookieValue(value = AuthCookieFactory.SIGN_UP_TOKEN, required = false) String signUpToken,
            @Valid @RequestBody SignUpRequest request) {
        requireCookie(signUpToken, AuthErrorCode.INVALID_SIGNUP_TOKEN);
        TokenResult result = completeSignUpUseCase.completeSignUp(request.toCommand(signUpToken));

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.refreshToken(result.refreshToken()).toString())
                .header(HttpHeaders.SET_COOKIE, cookieFactory.expired(AuthCookieFactory.SIGN_UP_TOKEN).toString())
                .body(ApiResponse.success(TokenResponse.from(result)));
    }

    @SecurityRequirements
    @PostMapping("/tokens")
    @Operation(summary = "access token 재발급",
            description = """
                    refreshToken 쿠키로 새 access token을 발급한다. credentials를 포함해 호출한다.
                    발급하면 이전 refresh token은 무효가 되므로 동시에 여러 번 호출하지 않는다.
                    401 INVALID_REFRESH_TOKEN이면 로그인 화면으로 보낸다.""")
    public ResponseEntity<ApiResponse<TokenResponse>> reissue(
            @CookieValue(value = AuthCookieFactory.REFRESH_TOKEN, required = false) String refreshToken) {
        requireCookie(refreshToken, AuthErrorCode.INVALID_REFRESH_TOKEN);
        TokenResult result = reissueTokenUseCase.reissue(refreshToken);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.refreshToken(result.refreshToken()).toString())
                .body(ApiResponse.success(TokenResponse.from(result)));
    }

    @DeleteMapping("/tokens")
    @Operation(summary = "로그아웃",
            description = "서버에 보관된 refresh token을 지우고 쿠키를 만료시킨다. 이미 발급된 access token은 만료까지 유효하므로 클라이언트에서 버린다.")
    public ResponseEntity<ApiResponse<Void>> logout(@AuthenticationPrincipal UUID userId) {
        reissueTokenUseCase.logout(userId);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.expired(AuthCookieFactory.REFRESH_TOKEN).toString())
                .body(ApiResponse.success());
    }

    /**
     * 쿠키가 없는 것은 요청 형식 오류(400)가 아니라 인증 실패(401)다.
     * 로그아웃한 뒤나 쿠키가 만료된 뒤가 이 경우이고, 클라이언트는 로그인 화면으로 보내면 된다.
     */
    private void requireCookie(String value, AuthErrorCode errorCode) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(errorCode);
        }
    }

    private ResponseEntity<Void> redirectToFrontend(ResponseCookie cookie, boolean signUp) {
        String location = signUp ? frontendRedirectUri + "?signUp=true" : frontendRedirectUri;
        return ResponseEntity.status(302)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .location(URI.create(location))
                .build();
    }
}
