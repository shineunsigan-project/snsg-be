package com.shineunsigan.snsg.admin.presentation;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.shineunsigan.snsg.admin.application.AdminCommandService;
import com.shineunsigan.snsg.admin.application.AdminQueryService;
import com.shineunsigan.snsg.admin.application.SuspendAdminFacade;
import com.shineunsigan.snsg.admin.application.dto.result.AdminPageResult;
import com.shineunsigan.snsg.admin.application.dto.result.AdminResult;
import com.shineunsigan.snsg.admin.application.dto.result.AdminSummaryResult;
import com.shineunsigan.snsg.admin.domain.constants.AdminRole;
import com.shineunsigan.snsg.admin.domain.constants.AdminStatus;
import com.shineunsigan.snsg.admin.domain.constants.AdminTeam;
import com.shineunsigan.snsg.admin.domain.exception.AdminErrorCode;
import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.common.web.GlobalExceptionHandler;

/**
 * 내 정보 응답에 비밀번호 해시·상태가 나가지 않는지 본다.
 */
@WebMvcTest(controllers = AdminController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.shineunsigan\\.snsg\\.auth\\..*"))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AdminControllerTest {

    private static final UUID ADMIN_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminQueryService adminQueryService;

    @MockitoBean
    private AdminCommandService adminCommandService;

    @MockitoBean
    private SuspendAdminFacade suspendAdminFacade;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("내 정보는 id·loginId·name·team·birthDate·role만 준다")
    void me() throws Exception {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(ADMIN_ID, null, List.of()));
        when(adminQueryService.getMe(ADMIN_ID)).thenReturn(new AdminResult(ADMIN_ID, "mintadmin", "김민트",
                AdminTeam.BACKEND, LocalDate.of(1996, 4, 12), AdminRole.MASTER));

        mockMvc.perform(get("/api/admin/v1/admins/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(ADMIN_ID.toString()))
                .andExpect(jsonPath("$.data.loginId").value("mintadmin"))
                .andExpect(jsonPath("$.data.name").value("김민트"))
                .andExpect(jsonPath("$.data.team").value("BACKEND"))
                .andExpect(jsonPath("$.data.birthDate").value("1996-04-12"))
                .andExpect(jsonPath("$.data.role").value("MASTER"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.status").doesNotExist())
                .andExpect(jsonPath("$.data.length()").value(6));
    }

    @Nested
    @DisplayName("상태 변경")
    class StatusChange {

        private static final UUID TARGET_ID = UUID.randomUUID();

        /** 동작 이름과 요청. */
        static Stream<Arguments> actions() {
            return Stream.of(
                    Arguments.of("승인", post("/api/admin/v1/admins/{id}/approval", TARGET_ID)),
                    Arguments.of("거절", post("/api/admin/v1/admins/{id}/rejection", TARGET_ID)),
                    Arguments.of("이용 중지", post("/api/admin/v1/admins/{id}/suspension", TARGET_ID)),
                    Arguments.of("해제", delete("/api/admin/v1/admins/{id}/suspension", TARGET_ID)));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("actions")
        @DisplayName("성공하면 200 {\"isSuccess\":true}이고 data가 없다. 경로의 adminId로 부른다")
        void success(String name, MockHttpServletRequestBuilder request) throws Exception {
            mockMvc.perform(request)
                    .andExpect(status().isOk())
                    .andExpect(content().json("{\"isSuccess\":true}", JsonCompareMode.STRICT));

            verifyCalled(name, TARGET_ID);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("actions")
        @DisplayName("ADMIN_NOT_FOUND 404, MASTER_NOT_MODIFIABLE 403, ADMIN_STATUS_CONFLICT 409를 그대로 전한다")
        void errors(String name, MockHttpServletRequestBuilder request) throws Exception {
            for (AdminErrorCode errorCode : List.of(AdminErrorCode.ADMIN_NOT_FOUND,
                    AdminErrorCode.MASTER_NOT_MODIFIABLE, AdminErrorCode.ADMIN_STATUS_CONFLICT)) {
                Mockito.reset(adminCommandService, suspendAdminFacade);
                doThrow(new BusinessException(errorCode)).when(adminCommandService).approve(TARGET_ID);
                doThrow(new BusinessException(errorCode)).when(adminCommandService).reject(TARGET_ID);
                doThrow(new BusinessException(errorCode)).when(adminCommandService).unsuspend(TARGET_ID);
                doThrow(new BusinessException(errorCode)).when(suspendAdminFacade).suspend(TARGET_ID);
                int expectedStatus = switch (errorCode) {
                    case ADMIN_NOT_FOUND -> 404;
                    case MASTER_NOT_MODIFIABLE -> 403;
                    default -> 409;
                };

                mockMvc.perform(request)
                        .andExpect(status().is(expectedStatus))
                        .andExpect(jsonPath("$.isSuccess").value(false))
                        .andExpect(jsonPath("$.code").value(errorCode.name()));
            }
        }

        @Test
        @DisplayName("경로의 adminId가 UUID가 아니면 400 INVALID_REQUEST이고 서비스·facade를 부르지 않는다")
        void invalidAdminId() throws Exception {
            for (MockHttpServletRequestBuilder request : List.of(post("/api/admin/v1/admins/not-uuid/approval"),
                    post("/api/admin/v1/admins/not-uuid/rejection"), post("/api/admin/v1/admins/not-uuid/suspension"),
                    delete("/api/admin/v1/admins/not-uuid/suspension"))) {
                mockMvc.perform(request)
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
            }

            verifyNoInteractions(adminCommandService, suspendAdminFacade);
        }

        private void verifyCalled(String name, UUID id) {
            switch (name) {
                case "승인" -> verify(adminCommandService).approve(id);
                case "거절" -> verify(adminCommandService).reject(id);
                case "이용 중지" -> verify(suspendAdminFacade).suspend(id);
                case "해제" -> verify(adminCommandService).unsuspend(id);
                default -> throw new IllegalArgumentException(name);
            }
        }
    }

    @Nested
    @DisplayName("목록")
    class ListAdmins {

        @Test
        @DisplayName("항목에 id·loginId·name·team·birthDate·role·status·createdAt만 있고 비밀번호 해시·감사 컬럼은 없다. 마지막 페이지의 nextCursor는 null")
        void items() throws Exception {
            UUID id = UUID.randomUUID();
            when(adminQueryService.list(null, null, null)).thenReturn(new AdminPageResult(List.of(
                    new AdminSummaryResult(id, "mintadmin", "김민트", AdminTeam.BACKEND, LocalDate.of(1996, 4, 12),
                            AdminRole.ADMIN, AdminStatus.PENDING, LocalDateTime.of(2026, 11, 13, 10, 15, 30))),
                    null));

            mockMvc.perform(get("/api/admin/v1/admins"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items[0].id").value(id.toString()))
                    .andExpect(jsonPath("$.data.items[0].loginId").value("mintadmin"))
                    .andExpect(jsonPath("$.data.items[0].name").value("김민트"))
                    .andExpect(jsonPath("$.data.items[0].team").value("BACKEND"))
                    .andExpect(jsonPath("$.data.items[0].birthDate").value("1996-04-12"))
                    .andExpect(jsonPath("$.data.items[0].role").value("ADMIN"))
                    .andExpect(jsonPath("$.data.items[0].status").value("PENDING"))
                    .andExpect(jsonPath("$.data.items[0].createdAt").value("2026-11-13T10:15:30"))
                    .andExpect(jsonPath("$.data.items[0].passwordHash").doesNotExist())
                    .andExpect(jsonPath("$.data.items[0].createdBy").doesNotExist())
                    .andExpect(jsonPath("$.data.items[0].updatedBy").doesNotExist())
                    .andExpect(jsonPath("$.data.items[0].length()").value(8))
                    .andExpect(jsonPath("$.data.nextCursor").value(nullValue()));
        }

        @Test
        @DisplayName("status·cursor·size를 그대로 넘긴다. status를 생략하면 null(필터 없음)이다")
        void passesParameters() throws Exception {
            when(adminQueryService.list(any(), any(), any())).thenReturn(new AdminPageResult(List.of(), null));

            mockMvc.perform(get("/api/admin/v1/admins").param("status", "SUSPENDED").param("cursor", "abc")
                    .param("size", "10")).andExpect(status().isOk());
            mockMvc.perform(get("/api/admin/v1/admins")).andExpect(status().isOk());

            verify(adminQueryService).list(AdminStatus.SUSPENDED, "abc", 10);
            verify(adminQueryService).list(null, null, null);
        }

        @Test
        @DisplayName("status가 네 값 밖이면 400 INVALID_REQUEST이고 조회하지 않는다")
        void invalidStatus() throws Exception {
            mockMvc.perform(get("/api/admin/v1/admins").param("status", "DELETED"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

            verifyNoInteractions(adminQueryService);
        }

        @Test
        @DisplayName("해석할 수 없는 커서면 400 INVALID_CURSOR를 전한다")
        void invalidCursor() throws Exception {
            when(adminQueryService.list(null, "broken", null))
                    .thenThrow(new BusinessException(AdminErrorCode.INVALID_CURSOR));

            mockMvc.perform(get("/api/admin/v1/admins").param("cursor", "broken"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
        }
    }
}
