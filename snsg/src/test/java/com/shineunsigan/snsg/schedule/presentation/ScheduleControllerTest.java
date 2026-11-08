package com.shineunsigan.snsg.schedule.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.shineunsigan.snsg.common.web.GlobalExceptionHandler;
import com.shineunsigan.snsg.schedule.application.CreateScheduleFacade;
import com.shineunsigan.snsg.schedule.application.dto.command.CreateScheduleCommand;

/**
 * 요청 단계에서 정한 규칙(필수값, 앞뒤 공백을 뺀 제목·설명 길이, 예상 소요 값)과 성공 응답 형태만 본다.
 * <p>
 * auth를 import하지 않는다. 인증 필터는 auth 패키지를 스캔에서 빼서 올리지 않고,
 * 인증 주체는 SecurityContext에 직접 넣는다.
 */
@WebMvcTest(controllers = ScheduleController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.shineunsigan\\.snsg\\.auth\\..*"))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ScheduleControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String TITLE_MESSAGE = "활동명은 1~20자로 적어주세요.";
    private static final String DESCRIPTION_MESSAGE = "10자 이상 적어주세요.";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateScheduleFacade createScheduleFacade;

    @BeforeEach
    void authenticate() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(USER_ID, null, List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("개설하면 201과 일정 id를 준다. 요청한 사용자가 개설자로 넘어간다")
    void createsSchedule() throws Exception {
        UUID scheduleId = UUID.randomUUID();
        when(createScheduleFacade.create(any())).thenReturn(scheduleId);

        perform(body("\"보드게임 같이 해요\"", "\"NORMAL\"", "\"처음 오시는 분도 환영해요.\""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.scheduleId").value(scheduleId.toString()));

        ArgumentCaptor<CreateScheduleCommand> command = ArgumentCaptor.forClass(CreateScheduleCommand.class);
        verify(createScheduleFacade).create(command.capture());
        assertThat(command.getValue().userId()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("활동명이 없으면 400 INVALID_REQUEST와 title 필드 오류")
    void missingTitle() throws Exception {
        perform(body(null, "\"NORMAL\"", "\"처음 오시는 분도 환영해요.\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("title"))
                .andExpect(jsonPath("$.errors[0].message").value(TITLE_MESSAGE));
        verify(createScheduleFacade, never()).create(any());
    }

    @Test
    @DisplayName("공백을 뺀 활동명이 21자면 400 INVALID_REQUEST와 title 필드 오류")
    void titleTooLong() throws Exception {
        perform(body("\"" + "가".repeat(21) + "\"", "\"NORMAL\"", "\"처음 오시는 분도 환영해요.\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("title"));
        verify(createScheduleFacade, never()).create(any());
    }

    @Test
    @DisplayName("앞뒤 공백을 포함해 22자여도 공백을 뺀 활동명이 20자면 요청 단계를 통과한다")
    void titleWithSurroundingSpacesPasses() throws Exception {
        when(createScheduleFacade.create(any())).thenReturn(UUID.randomUUID());

        perform(body("\" " + "가".repeat(20) + " \"", "\"NORMAL\"", "\"처음 오시는 분도 환영해요.\""))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("공백을 뺀 설명이 9자면 400 INVALID_REQUEST와 description 필드 오류")
    void descriptionTooShort() throws Exception {
        perform(body("\"보드게임 같이 해요\"", "\"NORMAL\"", "\"  " + "가".repeat(9) + "  \""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("description"))
                .andExpect(jsonPath("$.errors[0].message").value(DESCRIPTION_MESSAGE));
        verify(createScheduleFacade, never()).create(any());
    }

    @Test
    @DisplayName("예상 소요가 SHORT·NORMAL·LONG 밖이면 400 INVALID_REQUEST")
    void unknownDurationType() throws Exception {
        perform(body("\"보드게임 같이 해요\"", "\"VERY_LONG\"", "\"처음 오시는 분도 환영해요.\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verify(createScheduleFacade, never()).create(any());
    }

    private ResultActions perform(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/schedules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    /** 값은 JSON 리터럴 그대로 넣는다. null이면 그 필드를 뺀다. */
    private static String body(String title, String durationType, String description) {
        StringBuilder json = new StringBuilder("""
                {"categoryId": "01a19cc6-3d80-7000-8000-000000000001",
                 "placeId": "01a1a2b3-0000-7000-8000-000000000001",
                 "startAt": "2026-11-11T19:30:00",
                 "capacity": 6""");
        if (title != null) {
            json.append(", \"title\": ").append(title);
        }
        json.append(", \"durationType\": ").append(durationType);
        json.append(", \"description\": ").append(description);
        return json.append('}').toString();
    }
}
