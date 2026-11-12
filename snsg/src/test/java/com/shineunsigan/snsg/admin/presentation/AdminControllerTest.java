package com.shineunsigan.snsg.admin.presentation;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

import com.shineunsigan.snsg.admin.application.AdminQueryService;
import com.shineunsigan.snsg.admin.application.dto.result.AdminResult;
import com.shineunsigan.snsg.admin.domain.constants.AdminRole;
import com.shineunsigan.snsg.admin.domain.constants.AdminTeam;
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
}
