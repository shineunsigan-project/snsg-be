package com.shineunsigan.snsg.auth.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.shineunsigan.snsg.auth.application.AuthProperties;
import com.shineunsigan.snsg.auth.application.CompleteSignUpUseCase;
import com.shineunsigan.snsg.auth.application.ReissueTokenUseCase;
import com.shineunsigan.snsg.auth.application.SocialLoginUseCase;
import com.shineunsigan.snsg.auth.application.dto.result.SocialAuthResult;
import com.shineunsigan.snsg.auth.application.dto.result.TokenResult;
import com.shineunsigan.snsg.auth.domain.constants.SocialProvider;
import com.shineunsigan.snsg.auth.domain.exception.AuthErrorCode;
import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.common.web.GlobalExceptionHandler;

import jakarta.servlet.http.Cookie;

/**
 * 토큰이 쿠키로 나가는지, 본문으로 새지 않는지, 보호된 경로가 막히는지만 본다.
 * 라우팅·직렬화 같은 프레임워크 동작은 검증하지 않는다.
 */
@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({AuthCookieFactory.class, StringToSocialProviderConverter.class, GlobalExceptionHandler.class})
class AuthControllerTest {

    private static final String REFRESH_TOKEN = "refresh-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SocialLoginUseCase socialLoginUseCase;

    @MockitoBean
    private CompleteSignUpUseCase completeSignUpUseCase;

    @MockitoBean
    private ReissueTokenUseCase reissueTokenUseCase;

    @MockitoBean
    private AuthProperties authProperties;

    /** 인증 필터가 빈으로 올라오면서 필요하다. 이 테스트에서는 필터를 끄고 쓰지 않는다. */
    @MockitoBean
    private com.shineunsigan.snsg.auth.application.port.out.TokenPort tokenPort;

    @Test
    @DisplayName("기존 회원 콜백은 refresh를 쿠키로 심고 프론트엔드로 보낸다. 토큰은 URL에 넣지 않는다")
    void callbackForExistingUser() throws Exception {
        when(socialLoginUseCase.authenticate(SocialProvider.KAKAO, "code"))
                .thenReturn(new SocialAuthResult.LoggedIn(new TokenResult("access", REFRESH_TOKEN)));

        mockMvc.perform(get("/api/v1/auth/kakao/callback").param("code", "code"))
                .andExpect(status().isFound())
                .andExpect(cookie().value(AuthCookieFactory.REFRESH_TOKEN, REFRESH_TOKEN))
                .andExpect(cookie().httpOnly(AuthCookieFactory.REFRESH_TOKEN, true))
                .andExpect(header().string("Location", "http://localhost:3000/auth/callback"));
    }

    @Test
    @DisplayName("신규 사용자 콜백은 가입 토큰 쿠키를 심고 signUp 표시와 함께 보낸다")
    void callbackForNewUser() throws Exception {
        when(socialLoginUseCase.authenticate(SocialProvider.KAKAO, "code"))
                .thenReturn(new SocialAuthResult.SignUpRequired("signup-token"));

        mockMvc.perform(get("/api/v1/auth/kakao/callback").param("code", "code"))
                .andExpect(status().isFound())
                .andExpect(cookie().value(AuthCookieFactory.SIGN_UP_TOKEN, "signup-token"))
                .andExpect(cookie().httpOnly(AuthCookieFactory.SIGN_UP_TOKEN, true))
                .andExpect(header().string("Location", "http://localhost:3000/auth/callback?signUp=true"));
    }

    @Test
    @DisplayName("재발급 응답 본문에는 access token만 담고 refresh는 쿠키로만 내보낸다")
    void reissueDoesNotLeakRefreshToken() throws Exception {
        when(reissueTokenUseCase.reissue(REFRESH_TOKEN)).thenReturn(new TokenResult("new-access", "new-refresh"));

        mockMvc.perform(post("/api/v1/auth/tokens").cookie(new Cookie(AuthCookieFactory.REFRESH_TOKEN, REFRESH_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("new-access"))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(cookie().value(AuthCookieFactory.REFRESH_TOKEN, "new-refresh"));
    }

    @Test
    @DisplayName("저장된 토큰과 다르면 401 INVALID_REFRESH_TOKEN")
    void reissueWithInvalidToken() throws Exception {
        when(reissueTokenUseCase.reissue(REFRESH_TOKEN))
                .thenThrow(new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));

        mockMvc.perform(post("/api/v1/auth/tokens").cookie(new Cookie(AuthCookieFactory.REFRESH_TOKEN, REFRESH_TOKEN)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    @DisplayName("가입을 마치면 가입 토큰 쿠키를 지우고 refresh 쿠키를 심는다")
    void signUpReplacesCookies() throws Exception {
        when(completeSignUpUseCase.completeSignUp(any())).thenReturn(new TokenResult("access", REFRESH_TOKEN));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .cookie(new Cookie(AuthCookieFactory.SIGN_UP_TOKEN, "signup-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname": "민트초코", "gender": "FEMALE", "birthDate": "1996-04-12"}
                                """))
                .andExpect(status().isOk())
                .andExpect(cookie().value(AuthCookieFactory.REFRESH_TOKEN, REFRESH_TOKEN))
                .andExpect(cookie().maxAge(AuthCookieFactory.SIGN_UP_TOKEN, 0));
    }

    @Test
    @DisplayName("성별이 없으면 가입을 시도하지 않는다")
    void signUpRejectsMissingGender() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .cookie(new Cookie(AuthCookieFactory.SIGN_UP_TOKEN, "signup-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname": "민트초코", "birthDate": "1996-04-12"}
                                """))
                .andExpect(status().isBadRequest());

        verify(completeSignUpUseCase, never()).completeSignUp(any());
    }


    @Test
    @DisplayName("쿠키가 없으면 400이 아니라 401로 알린다. 로그아웃했거나 만료된 경우다")
    void missingCookieIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/auth/tokens"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        verify(reissueTokenUseCase, never()).reissue(any());
    }

    @Test
    @DisplayName("로그아웃은 인증된 사용자의 토큰을 지우고 쿠키를 만료시킨다")
    void logout() throws Exception {
        UUID userId = UUID.randomUUID();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(userId, null, java.util.List.of()));

        mockMvc.perform(delete("/api/v1/auth/tokens"))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge(AuthCookieFactory.REFRESH_TOKEN, 0));

        verify(reissueTokenUseCase).logout(userId);
        SecurityContextHolder.clearContext();
    }
}
