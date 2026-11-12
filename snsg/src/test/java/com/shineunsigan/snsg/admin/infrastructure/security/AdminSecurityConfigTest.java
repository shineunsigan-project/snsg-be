package com.shineunsigan.snsg.admin.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.shineunsigan.snsg.admin.application.AdminCommandService;
import com.shineunsigan.snsg.admin.application.AdminLoginFacade;
import com.shineunsigan.snsg.admin.application.AdminQueryService;
import com.shineunsigan.snsg.admin.application.AdminReissueTokenFacade;
import com.shineunsigan.snsg.admin.application.SignUpAdminFacade;
import com.shineunsigan.snsg.admin.application.SuspendAdminFacade;
import com.shineunsigan.snsg.admin.application.dto.result.AdminPageResult;
import com.shineunsigan.snsg.admin.application.dto.result.AdminResult;
import com.shineunsigan.snsg.admin.application.dto.result.AdminTokenResult;
import com.shineunsigan.snsg.admin.application.port.out.AdminTokenPort;
import com.shineunsigan.snsg.admin.domain.constants.AdminRole;
import com.shineunsigan.snsg.admin.domain.constants.AdminTeam;
import com.shineunsigan.snsg.admin.domain.exception.AdminErrorCode;
import com.shineunsigan.snsg.admin.presentation.AdminAuthController;
import com.shineunsigan.snsg.admin.presentation.AdminController;
import com.shineunsigan.snsg.admin.presentation.AdminCookieFactory;
import com.shineunsigan.snsg.auth.application.port.out.TokenPort;
import com.shineunsigan.snsg.auth.domain.exception.AuthErrorCode;
import com.shineunsigan.snsg.auth.infrastructure.security.JwtAuthenticationEntryPoint;
import com.shineunsigan.snsg.auth.infrastructure.security.JwtAuthenticationFilter;
import com.shineunsigan.snsg.auth.infrastructure.security.SecurityConfig;
import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.place.application.PlaceReviewCommandService;
import com.shineunsigan.snsg.place.application.PlaceReviewQueryService;
import com.shineunsigan.snsg.place.application.RegionQueryService;
import com.shineunsigan.snsg.place.application.dto.command.CreateManualPlaceCommand;
import com.shineunsigan.snsg.place.application.dto.command.ReviewPlaceCommand;
import com.shineunsigan.snsg.place.application.dto.result.PlaceReviewPageResult;
import com.shineunsigan.snsg.place.application.dto.result.PlaceReviewResult;
import com.shineunsigan.snsg.place.domain.constants.PlaceSource;
import com.shineunsigan.snsg.place.domain.constants.PlaceStatus;
import com.shineunsigan.snsg.place.domain.constants.PlaceType;
import com.shineunsigan.snsg.place.presentation.AdminPlaceController;
import com.shineunsigan.snsg.place.presentation.AdminRegionController;
import com.shineunsigan.snsg.user.application.UploadProfileImageFacade;
import com.shineunsigan.snsg.user.application.UserProfileCommandService;
import com.shineunsigan.snsg.user.application.UserQueryService;
import com.shineunsigan.snsg.user.application.dto.result.MyProfileResult;
import com.shineunsigan.snsg.user.domain.constants.AgePhase;
import com.shineunsigan.snsg.user.domain.constants.Gender;
import com.shineunsigan.snsg.user.presentation.UserController;

import jakarta.servlet.http.Cookie;

/**
 * 사용자 인증과 어드민 인증의 경계를 본다. 실제 앱처럼 두 보안 설정과 두 토큰 필터·진입점을 모두 올리고,
 * 토큰 해석 포트와 어드민 상태 조회만 mock으로 바꾼다.
 * <p>
 * 어느 쪽 필터든 서블릿 필터로도 등록되면, 체인이 끝난 뒤 다른 키로 토큰을 해석하다 실패해 인증을 지운다.
 * 그러면 컨트롤러의 principal이 null이 되므로 "그 id로 불린다"는 단정이 그 회귀를 잡는다.
 * 테스트 코드는 ArchUnit 대상이 아니라 auth·user·place를 import한다.
 */
@WebMvcTest(controllers = {AdminAuthController.class, AdminController.class, UserController.class,
        AdminPlaceController.class, AdminRegionController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class,
        AdminSecurityConfig.class, AdminAuthenticationEntryPoint.class, AdminAccessDeniedHandler.class,
        AdminCookieFactory.class, AdminSecurityConfigTest.TestConfig.class})
class AdminSecurityConfigTest {

    private static final String ADMIN_ME = "/api/admin/v1/admins/me";
    private static final String USER_ME = "/api/v1/users/me";
    private static final String USER_TOKEN = "user-access-token";
    private static final String ADMIN_TOKEN = "admin-access-token";
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID TARGET_ID = UUID.randomUUID();

    /** CORS 규칙은 이 테스트의 대상이 아니다. */
    @TestConfiguration
    static class TestConfig {

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
    private AdminTokenPort adminTokenPort;

    @MockitoBean
    private AdminQueryService adminQueryService;

    @MockitoBean
    private SignUpAdminFacade signUpAdminFacade;

    @MockitoBean
    private AdminLoginFacade adminLoginFacade;

    @MockitoBean
    private AdminReissueTokenFacade adminReissueTokenFacade;

    @MockitoBean
    private AdminCommandService adminCommandService;

    @MockitoBean
    private SuspendAdminFacade suspendAdminFacade;

    @MockitoBean
    private UserQueryService userQueryService;

    @MockitoBean
    private UserProfileCommandService userProfileCommandService;

    @MockitoBean
    private UploadProfileImageFacade uploadProfileImageFacade;

    @MockitoBean
    private PlaceReviewQueryService placeReviewQueryService;

    @MockitoBean
    private PlaceReviewCommandService placeReviewCommandService;

    @MockitoBean
    private RegionQueryService regionQueryService;

    /** 사용자 포트는 사용자 토큰만, 어드민 포트는 어드민 토큰만 받는다. 서로 다른 키로 서명된 것과 같다. */
    @BeforeEach
    void setUpTokens() {
        when(tokenPort.parseUserId(USER_TOKEN)).thenReturn(USER_ID);
        when(tokenPort.parseUserId(ADMIN_TOKEN)).thenThrow(new BusinessException(AuthErrorCode.INVALID_ACCESS_TOKEN));
        when(adminTokenPort.parseAdminId(ADMIN_TOKEN)).thenReturn(ADMIN_ID);
        when(adminTokenPort.parseAdminId(USER_TOKEN))
                .thenThrow(new BusinessException(AdminErrorCode.INVALID_ADMIN_ACCESS_TOKEN));
    }

    @Test
    @DisplayName("토큰 없이 가입 신청·로그인·재발급은 보안에 막히지 않는다")
    void publicAdminEndpoints() throws Exception {
        Instant sessionExpiresAt = Instant.now().plus(Duration.ofHours(12));
        when(adminLoginFacade.login(any())).thenReturn(new AdminTokenResult("access", "refresh", sessionExpiresAt));
        when(adminReissueTokenFacade.reissue("refresh"))
                .thenReturn(new AdminTokenResult("access", "refresh2", sessionExpiresAt));

        mockMvc.perform(post("/api/admin/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                        {"loginId": "mintadmin", "password": "mint1234!", "name": "김민트",
                         "team": "BACKEND", "birthDate": "1996-04-12"}"""))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/admin/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\": \"mintadmin\", \"password\": \"mint1234!\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/v1/auth/tokens")
                        .cookie(new Cookie(AdminCookieFactory.REFRESH_TOKEN, "refresh")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("토큰 없이 내 정보·로그아웃·없는 어드민 경로는 401 UNAUTHENTICATED")
    void protectedAdminEndpointsWithoutToken() throws Exception {
        mockMvc.perform(get(ADMIN_ME))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(delete("/api/admin/v1/auth/tokens"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(get("/api/admin/v1/anything"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("사용자 access 토큰으로 어드민 내 정보를 부르면 401이고 어드민 조회가 불리지 않는다")
    void userTokenOnAdminPath() throws Exception {
        mockMvc.perform(get(ADMIN_ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + USER_TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verify(adminQueryService, never()).findApprovedRole(any());
        verify(adminQueryService, never()).getMe(any());
    }

    @Test
    @DisplayName("승인된 어드민의 토큰이면 내 정보가 200이고 그 adminId로 조회한다")
    void approvedAdmin() throws Exception {
        when(adminQueryService.findApprovedRole(ADMIN_ID)).thenReturn(Optional.of(AdminRole.ADMIN));
        when(adminQueryService.getMe(ADMIN_ID)).thenReturn(new AdminResult(ADMIN_ID, "mintadmin", "김민트",
                AdminTeam.BACKEND, LocalDate.of(1996, 4, 12), AdminRole.ADMIN));

        mockMvc.perform(get(ADMIN_ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(ADMIN_ID.toString()));

        verify(adminQueryService).getMe(ADMIN_ID);
    }

    @Test
    @DisplayName("어드민 토큰이어도 승인 대기이거나 없는 어드민이면 401 UNAUTHENTICATED")
    void notApprovedAdmin() throws Exception {
        when(adminQueryService.findApprovedRole(ADMIN_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get(ADMIN_ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN_TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verify(adminQueryService, never()).getMe(any());
    }

    @Test
    @DisplayName("어드민 토큰으로 사용자 내 프로필을 부르면 401 UNAUTHENTICATED")
    void adminTokenOnUserPath() throws Exception {
        mockMvc.perform(get(USER_ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN_TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verify(userQueryService, never()).getMyProfile(any());
    }

    @Test
    @DisplayName("사용자 토큰으로 사용자 내 프로필을 부르면 200이고 그 userId로 조회한다(어드민 필터가 사용자 인증을 지우지 않는다)")
    void userTokenOnUserPath() throws Exception {
        when(userQueryService.getMyProfile(USER_ID)).thenReturn(new MyProfileResult(USER_ID, "김민트", "민트초코",
                Gender.FEMALE, LocalDate.of(1996, 4, 12), "", "/images/default.png", 20, AgePhase.LATE, null));

        mockMvc.perform(get(USER_ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + USER_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(USER_ID.toString()));

        verify(userQueryService).getMyProfile(USER_ID);
    }

    /** MASTER 전용 경로 다섯. 승인·거절·중지·해제는 TARGET_ID를 대상으로 한다. */
    private static List<MockHttpServletRequestBuilder> masterOnlyRequests() {
        return List.of(get("/api/admin/v1/admins"),
                post("/api/admin/v1/admins/{adminId}/approval", TARGET_ID),
                post("/api/admin/v1/admins/{adminId}/rejection", TARGET_ID),
                post("/api/admin/v1/admins/{adminId}/suspension", TARGET_ID),
                delete("/api/admin/v1/admins/{adminId}/suspension", TARGET_ID));
    }

    @Test
    @DisplayName("토큰 없이 MASTER 전용 경로를 부르면 401 UNAUTHENTICATED")
    void masterOnlyWithoutToken() throws Exception {
        for (MockHttpServletRequestBuilder request : masterOnlyRequests()) {
            mockMvc.perform(request)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        }

        verifyNoInteractions(adminCommandService, suspendAdminFacade);
        verify(adminQueryService, never()).list(any(), any(), any());
    }

    @Test
    @DisplayName("사용자 access 토큰으로 MASTER 전용 경로를 부르면 401 UNAUTHENTICATED")
    void masterOnlyWithUserToken() throws Exception {
        for (MockHttpServletRequestBuilder request : masterOnlyRequests()) {
            mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + USER_TOKEN))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        }

        verifyNoInteractions(adminCommandService, suspendAdminFacade);
        verify(adminQueryService, never()).list(any(), any(), any());
    }

    @Test
    @DisplayName("승인된 ADMIN 역할이 MASTER 전용 경로를 부르면 403 MASTER_ONLY이고 서비스·facade가 불리지 않는다. 내 정보는 200이다")
    void masterOnlyWithAdminRole() throws Exception {
        when(adminQueryService.findApprovedRole(ADMIN_ID)).thenReturn(Optional.of(AdminRole.ADMIN));
        when(adminQueryService.getMe(ADMIN_ID)).thenReturn(new AdminResult(ADMIN_ID, "mintadmin", "김민트",
                AdminTeam.BACKEND, LocalDate.of(1996, 4, 12), AdminRole.ADMIN));

        for (MockHttpServletRequestBuilder request : masterOnlyRequests()) {
            mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN_TOKEN))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.isSuccess").value(false))
                    .andExpect(jsonPath("$.code").value("MASTER_ONLY"));
        }
        mockMvc.perform(get(ADMIN_ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN_TOKEN))
                .andExpect(status().isOk());

        verifyNoInteractions(adminCommandService, suspendAdminFacade);
        verify(adminQueryService, never()).list(any(), any(), any());
    }

    @Test
    @DisplayName("승인된 MASTER가 MASTER 전용 경로를 부르면 2xx이고 서비스·facade가 경로의 adminId로 불린다")
    void masterOnlyWithMasterRole() throws Exception {
        when(adminQueryService.findApprovedRole(ADMIN_ID)).thenReturn(Optional.of(AdminRole.MASTER));
        when(adminQueryService.list(any(), any(), any())).thenReturn(new AdminPageResult(List.of(), null));

        for (MockHttpServletRequestBuilder request : masterOnlyRequests()) {
            mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN_TOKEN))
                    .andExpect(status().is2xxSuccessful());
        }

        verify(adminQueryService).list(null, null, null);
        verify(adminCommandService).approve(TARGET_ID);
        verify(adminCommandService).reject(TARGET_ID);
        verify(suspendAdminFacade).suspend(TARGET_ID);
        verify(adminCommandService).unsuspend(TARGET_ID);
    }

    @Test
    @DisplayName("어드민 토큰이어도 승인 상태가 아니면(거절·이용 중지·승인 대기·없음) MASTER 전용 경로는 401 UNAUTHENTICATED")
    void masterOnlyWithNotApprovedAdmin() throws Exception {
        when(adminQueryService.findApprovedRole(ADMIN_ID)).thenReturn(Optional.empty());

        for (MockHttpServletRequestBuilder request : masterOnlyRequests()) {
            mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN_TOKEN))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        }

        verifyNoInteractions(adminCommandService, suspendAdminFacade);
        verify(adminQueryService, never()).list(any(), any(), any());
    }

    // 장소 검수·지역 경로: 승인된 어드민(ADMIN·MASTER) 누구나 쓴다

    private static final UUID PLACE_ID = UUID.randomUUID();
    private static final String PLACE_UPDATE_BODY = """
            {"placeType": "ESCAPE_ROOM", "status": "APPROVED", "name": "키이스케이프 홍대점",
             "roadAddress": "서울특별시 마포구 와우산로 1", "addressDetail": "3층",
             "latitude": 37.5512345, "longitude": 126.9212345, "regionCode": "11440"}""";
    private static final String PLACE_CREATE_BODY = """
            {"placeType": "BOARD_GAME_CAFE", "name": "레드버튼 신촌점",
             "roadAddress": "서울특별시 서대문구 연세로 1", "addressDetail": "",
             "latitude": 37.5571234, "longitude": 126.9361234, "regionCode": "11410"}""";

    /** 장소 목록·상세·수정·등록과 지역 전체. */
    private static List<MockHttpServletRequestBuilder> placeRequests() {
        return List.of(get("/api/admin/v1/places"),
                get("/api/admin/v1/places/{placeId}", PLACE_ID),
                put("/api/admin/v1/places/{placeId}", PLACE_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(PLACE_UPDATE_BODY),
                post("/api/admin/v1/places").contentType(MediaType.APPLICATION_JSON).content(PLACE_CREATE_BODY),
                get("/api/admin/v1/regions"));
    }

    private static PlaceReviewResult placeResult() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 13, 14, 20);
        return new PlaceReviewResult(PLACE_ID, PlaceType.ESCAPE_ROOM, PlaceStatus.APPROVED, "키이스케이프 홍대점",
                "서울특별시 마포구 와우산로 1", "3층", new BigDecimal("37.5512345"), new BigDecimal("126.9212345"), "11440",
                false, PlaceSource.SEMAS, "MA0101", "키이스케이프 홍대점", "서울특별시 마포구 와우산로 1", "3층",
                new BigDecimal("37.5512345"), new BigDecimal("126.9212345"), "11440", now, now, "SYSTEM_SCHEDULER",
                now, ADMIN_ID.toString(), ADMIN_ID.toString(), now);
    }

    @Test
    @DisplayName("토큰 없이 장소·지역 경로를 부르면 401 UNAUTHENTICATED이고 서비스가 불리지 않는다")
    void placeEndpointsWithoutToken() throws Exception {
        for (MockHttpServletRequestBuilder request : placeRequests()) {
            mockMvc.perform(request)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        }

        verifyNoInteractions(placeReviewQueryService, placeReviewCommandService, regionQueryService);
    }

    @Test
    @DisplayName("사용자 access 토큰으로 장소·지역 경로를 부르면 401 UNAUTHENTICATED이고 서비스가 불리지 않는다")
    void placeEndpointsWithUserToken() throws Exception {
        for (MockHttpServletRequestBuilder request : placeRequests()) {
            mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + USER_TOKEN))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        }

        verifyNoInteractions(placeReviewQueryService, placeReviewCommandService, regionQueryService);
    }

    @Test
    @DisplayName("승인된 ADMIN 역할이면 장소·지역 경로가 2xx이고, 수정·등록은 그 adminId를 검수자로 서비스에 넘긴다")
    void placeEndpointsWithAdminRole() throws Exception {
        assertPlaceEndpointsPass(AdminRole.ADMIN);
    }

    @Test
    @DisplayName("승인된 MASTER 역할도 장소·지역 경로가 2xx이고, 수정·등록은 그 adminId를 검수자로 서비스에 넘긴다")
    void placeEndpointsWithMasterRole() throws Exception {
        assertPlaceEndpointsPass(AdminRole.MASTER);
    }

    private void assertPlaceEndpointsPass(AdminRole role) throws Exception {
        when(adminQueryService.findApprovedRole(ADMIN_ID)).thenReturn(Optional.of(role));
        when(placeReviewQueryService.list(any())).thenReturn(new PlaceReviewPageResult(List.of(), null));
        when(placeReviewQueryService.get(PLACE_ID)).thenReturn(placeResult());
        when(placeReviewCommandService.review(any())).thenReturn(placeResult());
        when(placeReviewCommandService.createManual(any())).thenReturn(placeResult());
        when(regionQueryService.findAll()).thenReturn(List.of());

        for (MockHttpServletRequestBuilder request : placeRequests()) {
            mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN_TOKEN))
                    .andExpect(status().is2xxSuccessful());
        }

        ArgumentCaptor<ReviewPlaceCommand> review = ArgumentCaptor.forClass(ReviewPlaceCommand.class);
        ArgumentCaptor<CreateManualPlaceCommand> create = ArgumentCaptor.forClass(CreateManualPlaceCommand.class);
        verify(placeReviewQueryService).list(any());
        verify(placeReviewQueryService).get(PLACE_ID);
        verify(placeReviewCommandService).review(review.capture());
        verify(placeReviewCommandService).createManual(create.capture());
        verify(regionQueryService).findAll();
        assertThat(review.getValue().placeId()).isEqualTo(PLACE_ID);
        assertThat(review.getValue().reviewerId()).isEqualTo(ADMIN_ID);
        assertThat(create.getValue().reviewerId()).isEqualTo(ADMIN_ID);
    }
}
