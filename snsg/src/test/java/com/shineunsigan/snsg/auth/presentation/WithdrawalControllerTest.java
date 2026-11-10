package com.shineunsigan.snsg.auth.presentation;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.shineunsigan.snsg.auth.application.AuthProperties;
import com.shineunsigan.snsg.auth.application.CheckWithdrawalFacade;
import com.shineunsigan.snsg.auth.application.WithdrawFacade;
import com.shineunsigan.snsg.auth.application.dto.result.WithdrawalEligibilityResult;
import com.shineunsigan.snsg.auth.application.port.out.TokenPort;
import com.shineunsigan.snsg.auth.domain.exception.AuthErrorCode;
import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.common.web.GlobalExceptionHandler;

/**
 * 탈퇴 가능 여부의 응답 형태(가능하면 availableAt null), 탈퇴 성공의 refresh 쿠키 만료, 탈퇴 불가의 409와 쿠키 유지를 본다.
 * 인증 경계(토큰 없으면 401)는 SecurityConfigTest가 본다.
 */
@WebMvcTest(controllers = WithdrawalController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({AuthCookieFactory.class, GlobalExceptionHandler.class})
class WithdrawalControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CheckWithdrawalFacade checkWithdrawalFacade;

    @MockitoBean
    private WithdrawFacade withdrawFacade;

    @MockitoBean
    private AuthProperties authProperties;

    /** 인증 필터가 빈으로 올라오면서 필요하다. 이 테스트에서는 필터를 끄고 쓰지 않는다. */
    @MockitoBean
    private TokenPort tokenPort;

    @BeforeEach
    void authenticate() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(USER_ID, null, List.of()));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("탈퇴할 수 있으면 withdrawable true와 availableAt null을 준다(필드는 JSON에 null로 나간다)")
    void withdrawable() throws Exception {
        when(checkWithdrawalFacade.check(USER_ID)).thenReturn(new WithdrawalEligibilityResult(true, null));

        mockMvc.perform(get("/api/v1/users/me/withdrawal-eligibility"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.withdrawable").value(true))
                .andExpect(jsonPath("$.data.availableAt").value(nullValue()))
                .andExpect(content().string(containsString("\"availableAt\":null")));
    }

    @Test
    @DisplayName("탈퇴할 수 없으면 withdrawable false와 탈퇴 가능 시각을 준다")
    void notWithdrawable() throws Exception {
        when(checkWithdrawalFacade.check(USER_ID))
                .thenReturn(new WithdrawalEligibilityResult(false, LocalDateTime.of(2027, 3, 13, 19, 0)));

        mockMvc.perform(get("/api/v1/users/me/withdrawal-eligibility"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.withdrawable").value(false))
                .andExpect(jsonPath("$.data.availableAt").value("2027-03-13T19:00:00"));
    }

    @Test
    @DisplayName("탈퇴하면 200과 함께 refresh 쿠키를 같은 이름·경로의 수명 0 쿠키로 만료시킨다")
    void withdraw() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(cookie().exists(AuthCookieFactory.REFRESH_TOKEN))
                .andExpect(cookie().path(AuthCookieFactory.REFRESH_TOKEN, "/api/v1/auth"))
                .andExpect(cookie().maxAge(AuthCookieFactory.REFRESH_TOKEN, 0));

        verify(withdrawFacade).withdraw(USER_ID);
    }

    @Test
    @DisplayName("남은 일정으로 탈퇴할 수 없으면 409 WITHDRAWAL_BLOCKED이고 쿠키를 건드리지 않는다")
    void withdrawBlocked() throws Exception {
        doThrow(new BusinessException(AuthErrorCode.WITHDRAWAL_BLOCKED)).when(withdrawFacade).withdraw(USER_ID);

        mockMvc.perform(delete("/api/v1/users/me"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WITHDRAWAL_BLOCKED"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }
}
