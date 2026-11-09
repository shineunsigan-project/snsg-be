package com.shineunsigan.snsg.auth.infrastructure.security;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.shineunsigan.snsg.auth.application.port.out.TokenPort;
import com.shineunsigan.snsg.auth.domain.exception.AuthErrorCode;
import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.user.application.UserCommandService;
import com.shineunsigan.snsg.user.application.UserQueryService;
import com.shineunsigan.snsg.user.application.dto.result.NicknameAvailabilityResult;
import com.shineunsigan.snsg.user.presentation.UserController;

/**
 * 실제 {@link SecurityConfig}와 인증 필터·진입점을 올려 공개 경로의 인가 규칙을 본다.
 * 토큰 검증 adapter만 mock으로 바꾼다.
 */
@WebMvcTest(controllers = UserController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class,
        SecurityConfigTest.CorsTestConfig.class})
class SecurityConfigTest {

    private static final String NICKNAME_AVAILABILITY = "/api/v1/users/nickname-availability";

    /** CORS 규칙은 이 테스트의 대상이 아니다. 설정값(CorsProperties) 없이 SecurityConfig를 올리기 위해 빈 설정을 준다. */
    @TestConfiguration
    static class CorsTestConfig {

        @Bean
        CorsConfigurationSource corsConfigurationSource() {
            return new UrlBasedCorsConfigurationSource();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TokenPort tokenPort;

    @MockitoBean
    private UserCommandService userCommandService;

    @MockitoBean
    private UserQueryService userQueryService;

    @Test
    @DisplayName("닉네임 사용 가능 여부는 토큰 없이 부를 수 있다")
    void nicknameAvailabilityWithoutToken() throws Exception {
        when(userQueryService.checkNicknameAvailability("민트초코", null))
                .thenReturn(new NicknameAvailabilityResult(true, null));

        mockMvc.perform(get(NICKNAME_AVAILABILITY).param("nickname", "민트초코"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("닉네임 사용 가능 여부에 틀린 토큰을 보내면 401이 아니라 익명(userId null)으로 처리한다")
    void nicknameAvailabilityWithInvalidToken() throws Exception {
        when(tokenPort.parseUserId("invalid-token")).thenThrow(new BusinessException(AuthErrorCode.INVALID_ACCESS_TOKEN));
        when(userQueryService.checkNicknameAvailability("민트초코", null))
                .thenReturn(new NicknameAvailabilityResult(true, null));

        mockMvc.perform(get(NICKNAME_AVAILABILITY).param("nickname", "민트초코")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isOk());

        verify(userQueryService).checkNicknameAvailability(eq("민트초코"), isNull());
    }

    @Test
    @DisplayName("같은 경로라도 GET이 아니면 토큰 없이 부를 때 401")
    void otherMethodOnNicknameAvailabilityRequiresToken() throws Exception {
        mockMvc.perform(post(NICKNAME_AVAILABILITY).param("nickname", "민트초코"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }
}
