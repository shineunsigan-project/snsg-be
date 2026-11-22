package com.shineunsigan.snsg.auth.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.greaterThan;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.shineunsigan.snsg.auth.application.AuthProperties;
import com.shineunsigan.snsg.auth.application.CompleteSignUpFacade;
import com.shineunsigan.snsg.auth.application.ReissueTokenFacade;
import com.shineunsigan.snsg.auth.application.SocialLoginFacade;
import com.shineunsigan.snsg.auth.application.dto.result.SocialAuthResult;
import com.shineunsigan.snsg.auth.application.dto.result.TokenResult;
import com.shineunsigan.snsg.auth.domain.constants.SocialProvider;
import com.shineunsigan.snsg.auth.domain.exception.AuthErrorCode;
import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.common.exception.ErrorCode;
import com.shineunsigan.snsg.common.exception.ErrorType;
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
    private static final String STATE = "state-value";
    private static final String NAME_MESSAGE = "이름은 2~20자의 한글 또는 영문만 쓸 수 있어요.";
    private static final String NICKNAME_MESSAGE = "닉네임은 2~10자의 한글·영문·숫자·.·_만 쓸 수 있어요.";
    private static final String EMAIL_MESSAGE = "이메일 형식이 올바르지 않아요.";
    private static final String BIRTH_DATE_MESSAGE = "생년월일이 올바르지 않습니다.";

    /**
     * user 도메인이 내는 업무 오류를 흉내 낸다. auth presentation은 user의 ErrorCode를 import하지 않는다.
     */
    private enum UserErrorStub implements ErrorCode {

        DUPLICATED_NICKNAME;

        @Override
        public ErrorType errorType() {
            return ErrorType.CONFLICT;
        }

        @Override
        public String message() {
            return "이미 사용 중인 닉네임이에요. 다른 닉네임을 입력해주세요.";
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SocialLoginFacade socialLoginFacade;

    @MockitoBean
    private CompleteSignUpFacade completeSignUpFacade;

    @MockitoBean
    private ReissueTokenFacade reissueTokenFacade;

    @MockitoBean
    private AuthProperties authProperties;

    /** 인증 필터가 빈으로 올라오면서 필요하다. 이 테스트에서는 필터를 끄고 쓰지 않는다. */
    @MockitoBean
    private com.shineunsigan.snsg.auth.application.port.out.TokenPort tokenPort;

    @Test
    @DisplayName("로그인 시작은 요청마다 새 state를 소셜 주소에 싣고, 같은 값을 Lax state 쿠키로 심는다")
    void authorizeBindsStateToBrowser() throws Exception {
        when(socialLoginFacade.authorizeUrl(eq(SocialProvider.KAKAO), anyString()))
                .thenAnswer(invocation -> "https://kauth.kakao.com/oauth/authorize?state=" + invocation.getArgument(1));

        String first = authorizeAndReturnState();
        String second = authorizeAndReturnState();

        assertThat(first).isNotEqualTo(second);
    }

    private String authorizeAndReturnState() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/kakao/authorize"))
                .andExpect(status().isFound())
                .andExpect(cookie().httpOnly(AuthCookieFactory.OAUTH_STATE, true))
                .andExpect(cookie().secure(AuthCookieFactory.OAUTH_STATE, true))
                .andExpect(cookie().sameSite(AuthCookieFactory.OAUTH_STATE, "Lax"))
                .andExpect(cookie().path(AuthCookieFactory.OAUTH_STATE, "/api/v1/auth"))
                .andExpect(cookie().maxAge(AuthCookieFactory.OAUTH_STATE, greaterThan(0)))
                .andReturn();

        String state = result.getResponse().getCookie(AuthCookieFactory.OAUTH_STATE).getValue();
        assertThat(state).isNotBlank();
        assertThat(result.getResponse().getHeader("Location")).endsWith("state=" + state);
        return state;
    }

    @Test
    @DisplayName("기존 회원 콜백은 refresh를 쿠키로 심고 프론트엔드로 보낸다. 토큰은 URL에 넣지 않는다")
    void callbackForExistingUser() throws Exception {
        when(socialLoginFacade.authenticate(SocialProvider.KAKAO, "code"))
                .thenReturn(new SocialAuthResult.LoggedIn(new TokenResult("access", REFRESH_TOKEN)));

        mockMvc.perform(callbackWithMatchingState())
                .andExpect(status().isFound())
                .andExpect(cookie().value(AuthCookieFactory.REFRESH_TOKEN, REFRESH_TOKEN))
                .andExpect(cookie().httpOnly(AuthCookieFactory.REFRESH_TOKEN, true))
                .andExpect(header().string("Location", "http://localhost:3000/auth/callback"))
                .andExpect(cookie().maxAge(AuthCookieFactory.OAUTH_STATE, 0));
    }

    @Test
    @DisplayName("신규 사용자 콜백은 가입 토큰 쿠키를 심고 signUp 표시와 함께 보낸다")
    void callbackForNewUser() throws Exception {
        when(socialLoginFacade.authenticate(SocialProvider.KAKAO, "code"))
                .thenReturn(new SocialAuthResult.SignUpRequired("signup-token"));

        mockMvc.perform(callbackWithMatchingState())
                .andExpect(status().isFound())
                .andExpect(cookie().value(AuthCookieFactory.SIGN_UP_TOKEN, "signup-token"))
                .andExpect(cookie().httpOnly(AuthCookieFactory.SIGN_UP_TOKEN, true))
                .andExpect(header().string("Location", "http://localhost:3000/auth/callback?signUp=true"))
                .andExpect(cookie().maxAge(AuthCookieFactory.OAUTH_STATE, 0));
    }

    private MockHttpServletRequestBuilder callbackWithMatchingState() {
        return get("/api/v1/auth/kakao/callback").param("code", "code")
                .param("state", STATE)
                .cookie(new Cookie(AuthCookieFactory.OAUTH_STATE, STATE));
    }

    @Test
    @DisplayName("state 쿼리와 쿠키가 모두 없는 콜백은 401 INVALID_OAUTH_STATE이고 소셜 인증을 하지 않는다(로그인 CSRF)")
    void callbackWithoutStateIsRejected() throws Exception {
        expectInvalidOAuthState(get("/api/v1/auth/kakao/callback").param("code", "code"));
    }

    @Test
    @DisplayName("state 쿠키만 있고 쿼리 state가 없으면 401 INVALID_OAUTH_STATE")
    void callbackWithoutStateQueryIsRejected() throws Exception {
        expectInvalidOAuthState(get("/api/v1/auth/kakao/callback").param("code", "code")
                .cookie(new Cookie(AuthCookieFactory.OAUTH_STATE, STATE)));
    }

    @Test
    @DisplayName("쿼리 state만 있고 state 쿠키가 없으면 401 INVALID_OAUTH_STATE")
    void callbackWithoutStateCookieIsRejected() throws Exception {
        expectInvalidOAuthState(get("/api/v1/auth/kakao/callback").param("code", "code")
                .param("state", STATE));
    }

    @Test
    @DisplayName("쿼리 state와 state 쿠키가 다르면 401 INVALID_OAUTH_STATE")
    void callbackWithMismatchedStateIsRejected() throws Exception {
        expectInvalidOAuthState(get("/api/v1/auth/kakao/callback").param("code", "code")
                .param("state", "attacker-state")
                .cookie(new Cookie(AuthCookieFactory.OAUTH_STATE, STATE)));
    }

    /**
     * 소셜 인증이 성공하도록 stub해 둔다. state를 확인하지 않으면 공격자 계정의 refresh 쿠키가 심긴다.
     */
    private void expectInvalidOAuthState(MockHttpServletRequestBuilder request) throws Exception {
        when(socialLoginFacade.authenticate(SocialProvider.KAKAO, "code"))
                .thenReturn(new SocialAuthResult.LoggedIn(new TokenResult("access", REFRESH_TOKEN)));

        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_OAUTH_STATE"))
                .andExpect(cookie().doesNotExist(AuthCookieFactory.REFRESH_TOKEN))
                .andExpect(cookie().doesNotExist(AuthCookieFactory.SIGN_UP_TOKEN));

        verify(socialLoginFacade, never()).authenticate(any(), any());
    }

    @Test
    @DisplayName("재발급 응답 본문에는 access token만 담고 refresh는 쿠키로만 내보낸다")
    void reissueDoesNotLeakRefreshToken() throws Exception {
        when(reissueTokenFacade.reissue(REFRESH_TOKEN)).thenReturn(new TokenResult("new-access", "new-refresh"));

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
        when(reissueTokenFacade.reissue(REFRESH_TOKEN))
                .thenThrow(new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));

        mockMvc.perform(post("/api/v1/auth/tokens").cookie(new Cookie(AuthCookieFactory.REFRESH_TOKEN, REFRESH_TOKEN)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    @DisplayName("가입을 마치면 가입 토큰 쿠키를 지우고 refresh 쿠키를 심는다")
    void signUpReplacesCookies() throws Exception {
        when(completeSignUpFacade.completeSignUp(any())).thenReturn(new TokenResult("access", REFRESH_TOKEN));

        signUp("""
                {"name": "김민트", "nickname": "민트초코", "gender": "FEMALE", "birthDate": "1996-04-12"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andExpect(cookie().value(AuthCookieFactory.REFRESH_TOKEN, REFRESH_TOKEN))
                .andExpect(cookie().maxAge(AuthCookieFactory.SIGN_UP_TOKEN, 0));
    }

    @Test
    @DisplayName("성별이 없으면 가입을 시도하지 않고 gender 필드 오류를 알린다")
    void signUpRejectsMissingGender() throws Exception {
        signUp("""
                {"name": "김민트", "nickname": "민트초코", "birthDate": "1996-04-12"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("gender"))
                .andExpect(jsonPath("$.errors[0].message").value("성별을 선택해주세요."));

        verify(completeSignUpFacade, never()).completeSignUp(any());
    }

    @Test
    @DisplayName("이름에 공백이 있으면 name 필드 오류")
    void signUpRejectsInvalidName() throws Exception {
        signUp("""
                {"name": "김 민트", "nickname": "민트초코", "gender": "FEMALE", "birthDate": "1996-04-12"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("요청 형식이 올바르지 않습니다."))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").value(NAME_MESSAGE));

        verify(completeSignUpFacade, never()).completeSignUp(any());
    }

    @Test
    @DisplayName("닉네임에 허용하지 않는 문자가 있으면 nickname 필드 오류")
    void signUpRejectsInvalidNickname() throws Exception {
        signUp("""
                {"name": "김민트", "nickname": "민트-초코", "gender": "FEMALE", "birthDate": "1996-04-12"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("nickname"))
                .andExpect(jsonPath("$.errors[0].message").value(NICKNAME_MESSAGE));
    }

    @Test
    @DisplayName("이메일에 최상위 도메인이 없으면 email 필드 오류")
    void signUpRejectsInvalidEmail() throws Exception {
        signUp("""
                {"name": "김민트", "nickname": "민트초코", "email": "a@b", "gender": "FEMALE", "birthDate": "1996-04-12"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.errors[0].message").value(EMAIL_MESSAGE));
    }

    @Test
    @DisplayName("이메일이 빈 문자열이면 없음으로 보고 받는다")
    void signUpAcceptsEmptyEmail() throws Exception {
        when(completeSignUpFacade.completeSignUp(any())).thenReturn(new TokenResult("access", REFRESH_TOKEN));

        signUp("""
                {"name": "김민트", "nickname": "민트초코", "email": "", "gender": "FEMALE", "birthDate": "1996-04-12"}
                """)
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("미래 생년월일은 birthDate 필드 오류")
    void signUpRejectsFutureBirthDate() throws Exception {
        signUp("""
                {"name": "김민트", "nickname": "민트초코", "gender": "FEMALE", "birthDate": "2999-01-01"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("birthDate"))
                .andExpect(jsonPath("$.errors[0].message").value(BIRTH_DATE_MESSAGE));
    }

    @Test
    @DisplayName("이름·닉네임·생년월일이 비었을 때도 각 필드의 형식 오류 문구를 쓴다")
    void signUpRejectsMissingFieldsWithFormatMessages() throws Exception {
        signUp("""
                {"gender": "FEMALE"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(3))
                .andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("name", "nickname", "birthDate")))
                .andExpect(jsonPath("$.errors[*].message")
                        .value(containsInAnyOrder(NAME_MESSAGE, NICKNAME_MESSAGE, BIRTH_DATE_MESSAGE)));
    }

    @Test
    @DisplayName("두 필드가 동시에 틀리면 errors에 둘 다 담긴다")
    void signUpReportsEveryInvalidField() throws Exception {
        signUp("""
                {"name": "김민트1", "nickname": "민트초코", "email": " a@b.com", "gender": "FEMALE", "birthDate": "1996-04-12"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("name", "email")))
                .andExpect(jsonPath("$.errors[*].message").value(containsInAnyOrder(NAME_MESSAGE, EMAIL_MESSAGE)));
    }

    @Test
    @DisplayName("FEMALE·MALE 밖의 성별은 400 INVALID_REQUEST이고 errors가 없다")
    void signUpRejectsUnknownGenderWithoutErrors() throws Exception {
        signUp("""
                {"name": "김민트", "nickname": "민트초코", "gender": "OTHER", "birthDate": "1996-04-12"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors").doesNotExist());

        verify(completeSignUpFacade, never()).completeSignUp(any());
    }

    @Test
    @DisplayName("없는 날짜는 400 INVALID_REQUEST이고 errors가 없다")
    void signUpRejectsNonexistentDateWithoutErrors() throws Exception {
        signUp("""
                {"name": "김민트", "nickname": "민트초코", "gender": "FEMALE", "birthDate": "2010-02-30"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    @DisplayName("업무 오류(409 DUPLICATED_NICKNAME) 응답에는 errors가 없다")
    void businessErrorHasNoErrors() throws Exception {
        when(completeSignUpFacade.completeSignUp(any()))
                .thenThrow(new BusinessException(UserErrorStub.DUPLICATED_NICKNAME));

        signUp("""
                {"name": "김민트", "nickname": "mint", "gender": "FEMALE", "birthDate": "1996-04-12"}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATED_NICKNAME"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }


    @Test
    @DisplayName("쿠키가 없으면 400이 아니라 401로 알린다. 로그아웃했거나 만료된 경우다")
    void missingCookieIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/auth/tokens"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"))
                .andExpect(jsonPath("$.errors").doesNotExist());

        verify(reissueTokenFacade, never()).reissue(any());
    }

    @Test
    @DisplayName("로그아웃은 인증된 사용자와 refresh 쿠키로 이 기기의 세션을 지우고 쿠키를 만료시킨다")
    void logout() throws Exception {
        UUID userId = UUID.randomUUID();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(userId, null, java.util.List.of()));

        mockMvc.perform(delete("/api/v1/auth/tokens").cookie(new Cookie(AuthCookieFactory.REFRESH_TOKEN, REFRESH_TOKEN)))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge(AuthCookieFactory.REFRESH_TOKEN, 0));

        verify(reissueTokenFacade).logout(userId, REFRESH_TOKEN);
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("refresh 쿠키가 없어도 로그아웃은 200이고 쿠키를 만료시킨다")
    void logoutWithoutCookie() throws Exception {
        UUID userId = UUID.randomUUID();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(userId, null, java.util.List.of()));

        mockMvc.perform(delete("/api/v1/auth/tokens"))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge(AuthCookieFactory.REFRESH_TOKEN, 0));

        verify(reissueTokenFacade).logout(userId, null);
        SecurityContextHolder.clearContext();
    }

    private ResultActions signUp(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/signup")
                .cookie(new Cookie(AuthCookieFactory.SIGN_UP_TOKEN, "signup-token"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
