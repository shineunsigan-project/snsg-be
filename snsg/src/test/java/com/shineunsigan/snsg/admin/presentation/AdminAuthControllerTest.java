package com.shineunsigan.snsg.admin.presentation;

import static org.hamcrest.Matchers.allOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.shineunsigan.snsg.admin.application.AdminLoginFacade;
import com.shineunsigan.snsg.admin.application.AdminReissueTokenFacade;
import com.shineunsigan.snsg.admin.application.SignUpAdminFacade;
import com.shineunsigan.snsg.admin.application.dto.command.AdminLoginCommand;
import com.shineunsigan.snsg.admin.application.dto.result.AdminTokenResult;
import com.shineunsigan.snsg.admin.domain.exception.AdminErrorCode;
import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.common.web.GlobalExceptionHandler;

import jakarta.servlet.http.Cookie;

/**
 * 가입 요청 검증 규칙, 토큰이 쿠키로만 나가는지, 쿠키 속성과 에러 코드를 본다. 인가 규칙은 {@code AdminSecurityConfigTest}가 본다.
 * <p>
 * auth를 import하지 않는다. 사용자 인증 필터는 auth 패키지를 스캔에서 빼서 올리지 않고, 인증 주체는 SecurityContext에 직접 넣는다.
 */
@WebMvcTest(controllers = AdminAuthController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.shineunsigan\\.snsg\\.auth\\..*"))
@AutoConfigureMockMvc(addFilters = false)
@Import({AdminCookieFactory.class, GlobalExceptionHandler.class})
class AdminAuthControllerTest {

    private static final Duration REFRESH_VALIDITY = Duration.ofHours(12);
    private static final String VALID_SIGN_UP = """
            {"loginId": "mintadmin", "password": "mint1234!", "name": "김민트", "team": "BACKEND", "birthDate": "1996-04-12"}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SignUpAdminFacade signUpAdminFacade;

    @MockitoBean
    private AdminLoginFacade adminLoginFacade;

    @MockitoBean
    private AdminReissueTokenFacade adminReissueTokenFacade;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("가입 신청")
    class SignUp {

        @Test
        @DisplayName("성공하면 201 {isSuccess:true}이고 쿠키·토큰을 주지 않는다")
        void created() throws Exception {
            mockMvc.perform(post("/api/admin/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_SIGN_UP))
                    .andExpect(status().isCreated())
                    .andExpect(content().json("{\"isSuccess\":true}", true))
                    .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

            verify(signUpAdminFacade).signUp(any());
        }

        @ParameterizedTest(name = "{0} = {1}")
        @CsvSource(delimiter = '|', value = {
                "loginId|MintAdmin",
                "loginId|abc",
                "loginId|1mint",
                "password|mintmint",
                "password|mint 1234",
                "password|민트mint1234",
                "name|김",
                "name|김민트1",
                "birthDate|2999-01-01"})
        @DisplayName("필드 형식이 틀리면 400 INVALID_REQUEST, errors에 그 필드가 담기고 facade를 부르지 않는다")
        void invalidField(String field, String value) throws Exception {
            String body = VALID_SIGN_UP.replaceFirst("\"" + field + "\": \"[^\"]*\"",
                    "\"" + field + "\": \"" + value + "\"");

            mockMvc.perform(post("/api/admin/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.errors[0].field").value(field));

            verify(signUpAdminFacade, never()).signUp(any());
        }

        @Test
        @DisplayName("소속팀이 없으면 400 INVALID_REQUEST, errors에 team이 담긴다")
        void missingTeam() throws Exception {
            String body = VALID_SIGN_UP.replace("\"team\": \"BACKEND\", ", "");

            mockMvc.perform(post("/api/admin/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("team"));

            verify(signUpAdminFacade, never()).signUp(any());
        }

        @Test
        @DisplayName("이미 쓰는 아이디면 409 DUPLICATED_LOGIN_ID")
        void duplicated() throws Exception {
            doThrow(new BusinessException(AdminErrorCode.DUPLICATED_LOGIN_ID))
                    .when(signUpAdminFacade).signUp(any());

            mockMvc.perform(post("/api/admin/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_SIGN_UP))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DUPLICATED_LOGIN_ID"));
        }
    }

    @Nested
    @DisplayName("로그인")
    class Login {

        @Test
        @DisplayName("성공하면 access는 본문으로, refresh는 adminRefreshToken 쿠키(HttpOnly·Secure·SameSite=None·Path·Max-Age)로 준다")
        void login() throws Exception {
            when(adminLoginFacade.login(new AdminLoginCommand("mintadmin", "mint1234!")))
                    .thenReturn(new AdminTokenResult("access", "refresh", Instant.now().plus(REFRESH_VALIDITY)));

            mockMvc.perform(post("/api/admin/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"loginId\": \"mintadmin\", \"password\": \"mint1234!\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken").value("access"))
                    .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                            containsString("adminRefreshToken=refresh"),
                            containsString("HttpOnly"),
                            containsString("Secure"),
                            containsString("SameSite=None"),
                            containsString("Path=/api/admin/v1/auth"),
                            containsString("Max-Age=" + REFRESH_VALIDITY.toSeconds()))));
        }

        @Test
        @DisplayName("아이디나 비밀번호가 비었으면 400 INVALID_REQUEST")
        void blank() throws Exception {
            mockMvc.perform(post("/api/admin/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"loginId\": \"\", \"password\": \"mint1234!\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
            mockMvc.perform(post("/api/admin/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"loginId\": \"mintadmin\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

            verify(adminLoginFacade, never()).login(any());
        }

        @Test
        @DisplayName("형식이 틀린 아이디(대문자)는 400이 아니라 facade로 넘어간다")
        void malformedLoginIdReachesFacade() throws Exception {
            when(adminLoginFacade.login(new AdminLoginCommand("MintAdmin", "mint1234!")))
                    .thenThrow(new BusinessException(AdminErrorCode.INVALID_ADMIN_CREDENTIALS));

            mockMvc.perform(post("/api/admin/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"loginId\": \"MintAdmin\", \"password\": \"mint1234!\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_ADMIN_CREDENTIALS"));
        }
    }

    @Nested
    @DisplayName("재발급")
    class Reissue {

        @Test
        @DisplayName("쿠키가 없으면 401 INVALID_ADMIN_REFRESH_TOKEN")
        void noCookie() throws Exception {
            mockMvc.perform(post("/api/admin/v1/auth/tokens"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_ADMIN_REFRESH_TOKEN"));

            verify(adminReissueTokenFacade, never()).reissue(any());
        }

        @Test
        @DisplayName("성공하면 access는 본문으로, 새 refresh는 쿠키로 준다. 쿠키 수명은 세션 만료까지 남은 시간(늘지 않음)이다")
        void reissue() throws Exception {
            when(adminReissueTokenFacade.reissue("old-refresh"))
                    .thenReturn(new AdminTokenResult("access", "new-refresh", Instant.now().plus(Duration.ofHours(1))));

            mockMvc.perform(post("/api/admin/v1/auth/tokens")
                            .cookie(new Cookie(AdminCookieFactory.REFRESH_TOKEN, "old-refresh")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken").value("access"))
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                            containsString("adminRefreshToken=new-refresh"),
                            containsString("Path=/api/admin/v1/auth"))))
                    .andExpect(result -> assertThat(maxAge(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)))
                            .isPositive().isLessThanOrEqualTo(Duration.ofHours(1).toSeconds()));
        }
    }

    @Test
    @DisplayName("로그아웃은 200이고 같은 Path의 만료된 adminRefreshToken 쿠키를 준다")
    void logout() throws Exception {
        UUID adminId = UUID.randomUUID();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(adminId, null, List.of()));

        mockMvc.perform(delete("/api/admin/v1/auth/tokens")
                        .cookie(new Cookie(AdminCookieFactory.REFRESH_TOKEN, "refresh")))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"isSuccess\":true}", true))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                        containsString("adminRefreshToken="),
                        containsString("Max-Age=0"),
                        containsString("Path=/api/admin/v1/auth"),
                        not(containsString("adminRefreshToken=refresh")))));

        verify(adminReissueTokenFacade).logout(adminId, "refresh");
    }

    private long maxAge(String setCookie) {
        Matcher matcher = Pattern.compile("Max-Age=(\\d+)").matcher(setCookie);
        assertThat(matcher.find()).isTrue();
        return Long.parseLong(matcher.group(1));
    }
}
