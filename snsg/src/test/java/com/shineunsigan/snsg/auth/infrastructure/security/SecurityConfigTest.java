package com.shineunsigan.snsg.auth.infrastructure.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.shineunsigan.snsg.auth.application.CheckWithdrawalFacade;
import com.shineunsigan.snsg.auth.application.WithdrawFacade;
import com.shineunsigan.snsg.auth.application.port.out.TokenPort;
import com.shineunsigan.snsg.auth.domain.exception.AuthErrorCode;
import com.shineunsigan.snsg.auth.presentation.AuthCookieFactory;
import com.shineunsigan.snsg.auth.presentation.WithdrawalController;
import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.schedule.application.CreateScheduleFacade;
import com.shineunsigan.snsg.schedule.application.GetScheduleDetailFacade;
import com.shineunsigan.snsg.schedule.application.JoinScheduleFacade;
import com.shineunsigan.snsg.schedule.application.ListParticipantsFacade;
import com.shineunsigan.snsg.schedule.application.ListSchedulesFacade;
import com.shineunsigan.snsg.schedule.application.ScheduleCommandService;
import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleListResult;
import com.shineunsigan.snsg.schedule.domain.exception.ScheduleErrorCode;
import com.shineunsigan.snsg.schedule.presentation.ScheduleController;
import com.shineunsigan.snsg.user.application.UploadProfileImageFacade;
import com.shineunsigan.snsg.user.application.UserProfileCommandService;
import com.shineunsigan.snsg.user.application.UserQueryService;
import com.shineunsigan.snsg.user.application.dto.result.NicknameAvailabilityResult;
import com.shineunsigan.snsg.user.presentation.UserController;

/**
 * 실제 {@link SecurityConfig}와 인증 필터·진입점을 올려 공개 경로의 인가 규칙을 본다.
 * 토큰 검증 adapter만 mock으로 바꾼다.
 */
@WebMvcTest(controllers = {UserController.class, ScheduleController.class, WithdrawalController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class,
        SecurityConfigTest.CorsTestConfig.class})
class SecurityConfigTest {

    private static final String NICKNAME_AVAILABILITY = "/api/v1/users/nickname-availability";
    private static final String ME = "/api/v1/users/me";

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
    private CheckWithdrawalFacade checkWithdrawalFacade;

    @MockitoBean
    private WithdrawFacade withdrawFacade;

    @MockitoBean
    private AuthCookieFactory authCookieFactory;

    @MockitoBean
    private UserQueryService userQueryService;

    @MockitoBean
    private UserProfileCommandService userProfileCommandService;

    @MockitoBean
    private UploadProfileImageFacade uploadProfileImageFacade;

    @MockitoBean
    private CreateScheduleFacade createScheduleFacade;

    @MockitoBean
    private JoinScheduleFacade joinScheduleFacade;

    @MockitoBean
    private ScheduleCommandService scheduleCommandService;

    @MockitoBean
    private ListSchedulesFacade listSchedulesFacade;

    @MockitoBean
    private GetScheduleDetailFacade getScheduleDetailFacade;

    @MockitoBean
    private ListParticipantsFacade listParticipantsFacade;

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

    @Test
    @DisplayName("내 프로필 조회는 토큰 없이 부르면 401")
    void getMyProfileRequiresToken() throws Exception {
        mockMvc.perform(get(ME))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("내 프로필 수정은 토큰 없이 부르면 401")
    void updateMyProfileRequiresToken() throws Exception {
        mockMvc.perform(patch(ME).contentType(MediaType.APPLICATION_JSON).content("{\"nickname\": \"민트라떼\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("프로필 사진 업로드는 토큰 없이 부르면 401")
    void uploadProfileImageRequiresToken() throws Exception {
        mockMvc.perform(multipart(ME + "/profile-image")
                        .file(new MockMultipartFile("image", "me.png", MediaType.IMAGE_PNG_VALUE, new byte[] {1})))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("탈퇴 가능 여부는 토큰 없이 부르면 401")
    void withdrawalEligibilityRequiresToken() throws Exception {
        mockMvc.perform(get(ME + "/withdrawal-eligibility"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("탈퇴는 토큰 없이 부르면 401")
    void withdrawRequiresToken() throws Exception {
        mockMvc.perform(delete(ME))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("모집 중 일정 목록은 토큰 없이 부를 수 있다")
    void recruitingSchedulesWithoutToken() throws Exception {
        when(listSchedulesFacade.listRecruiting(any())).thenReturn(new ScheduleListResult(List.of(), null));

        mockMvc.perform(get("/api/v1/schedules"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("일정 상세는 토큰 없이 부를 수 있다(401이 아니라 컨트롤러까지 간다). 보는 사람은 null로 넘어간다")
    void scheduleDetailWithoutToken() throws Exception {
        UUID scheduleId = UUID.randomUUID();
        when(getScheduleDetailFacade.get(scheduleId, null))
                .thenThrow(new BusinessException(ScheduleErrorCode.SCHEDULE_NOT_FOUND));

        mockMvc.perform(get("/api/v1/schedules/{scheduleId}", scheduleId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCHEDULE_NOT_FOUND"));

        verify(getScheduleDetailFacade).get(eq(scheduleId), isNull());
    }

    @Test
    @DisplayName("참여한·개설한 일정 목록과 일정 개설은 토큰 없이 부를 때 401")
    void mySchedulesAndCreateRequireToken() throws Exception {
        mockMvc.perform(get("/api/v1/schedules/participating"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(get("/api/v1/schedules/hosted"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(post("/api/v1/schedules").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("참여자 명단은 토큰 없이 부르면 401. 상세의 공개 경로 패턴은 하위 경로에 걸리지 않는다")
    void participantsRequireToken() throws Exception {
        mockMvc.perform(get("/api/v1/schedules/{scheduleId}/participants", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }
}
