package com.shineunsigan.snsg.user.presentation;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.shineunsigan.snsg.common.web.GlobalExceptionHandler;
import com.shineunsigan.snsg.user.application.UserCommandService;
import com.shineunsigan.snsg.user.application.UserQueryService;
import com.shineunsigan.snsg.user.application.dto.result.NicknameAvailabilityResult;
import com.shineunsigan.snsg.user.domain.constants.NicknameUnavailableReason;

/**
 * 닉네임 사용 가능 여부가 판정 결과를 그대로 싣는지, 인증 주체를 그대로 넘기는지만 본다.
 * <p>
 * auth를 import하지 않는다. 인증 필터는 auth 패키지를 스캔에서 빼서 올리지 않고,
 * 인증 주체는 SecurityContext에 직접 넣는다. 공개 경로 설정은 auth의 {@code SecurityConfigTest}가 본다.
 */
@WebMvcTest(controllers = UserController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.shineunsigan\\.snsg\\.auth\\..*"))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class UserControllerTest {

    private static final String NICKNAME_AVAILABILITY = "/api/v1/users/nickname-availability";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserCommandService userCommandService;

    @MockitoBean
    private UserQueryService userQueryService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("쓸 수 있는 닉네임이면 200과 available=true, reason=null")
    void available() throws Exception {
        when(userQueryService.checkNicknameAvailability("민트초코", null))
                .thenReturn(new NicknameAvailabilityResult(true, null));

        mockMvc.perform(get(NICKNAME_AVAILABILITY).param("nickname", "민트초코"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.reason").value(nullValue()));
    }

    @Test
    @DisplayName("중복이면 200과 available=false, reason=DUPLICATED")
    void duplicated() throws Exception {
        when(userQueryService.checkNicknameAvailability("민트초코", null))
                .thenReturn(new NicknameAvailabilityResult(false, NicknameUnavailableReason.DUPLICATED));

        mockMvc.perform(get(NICKNAME_AVAILABILITY).param("nickname", "민트초코"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(false))
                .andExpect(jsonPath("$.data.reason").value("DUPLICATED"));
    }

    @Test
    @DisplayName("인증 주체가 있으면 그 userId로 판정을 요청한다")
    void passesAuthenticatedUserId() throws Exception {
        UUID userId = UUID.randomUUID();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()));
        when(userQueryService.checkNicknameAvailability("민트초코", userId))
                .thenReturn(new NicknameAvailabilityResult(true, null));

        mockMvc.perform(get(NICKNAME_AVAILABILITY).param("nickname", "민트초코"))
                .andExpect(status().isOk());

        verify(userQueryService).checkNicknameAvailability("민트초코", userId);
    }

    @Test
    @DisplayName("인증 주체가 없으면 userId null로 판정을 요청한다")
    void passesNullWithoutAuthentication() throws Exception {
        when(userQueryService.checkNicknameAvailability("민트초코", null))
                .thenReturn(new NicknameAvailabilityResult(true, null));

        mockMvc.perform(get(NICKNAME_AVAILABILITY).param("nickname", "민트초코"))
                .andExpect(status().isOk());

        verify(userQueryService).checkNicknameAvailability(eq("민트초코"), isNull());
    }
}
