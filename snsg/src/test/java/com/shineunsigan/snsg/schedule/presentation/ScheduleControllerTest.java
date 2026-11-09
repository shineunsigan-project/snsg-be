package com.shineunsigan.snsg.schedule.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.common.web.GlobalExceptionHandler;
import com.shineunsigan.snsg.schedule.application.CreateScheduleFacade;
import com.shineunsigan.snsg.schedule.application.GetScheduleDetailFacade;
import com.shineunsigan.snsg.schedule.application.JoinScheduleFacade;
import com.shineunsigan.snsg.schedule.application.ListSchedulesFacade;
import com.shineunsigan.snsg.schedule.application.ScheduleCommandService;
import com.shineunsigan.snsg.schedule.application.dto.command.CreateScheduleCommand;
import com.shineunsigan.snsg.schedule.application.dto.command.ListRecruitingSchedulesCommand;
import com.shineunsigan.snsg.schedule.application.dto.result.ParticipantSummaryResult;
import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleAgeGroupResult;
import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleCardResult;
import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleDetailQueryResult;
import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleDetailResult;
import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleHostResult;
import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleListItemResult;
import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleListResult;
import com.shineunsigan.snsg.schedule.domain.constants.AgePhase;
import com.shineunsigan.snsg.schedule.domain.constants.DisplayStatus;
import com.shineunsigan.snsg.schedule.domain.constants.DurationType;
import com.shineunsigan.snsg.schedule.domain.constants.ScheduleAction;
import com.shineunsigan.snsg.schedule.domain.constants.ViewerRole;
import com.shineunsigan.snsg.schedule.domain.exception.ScheduleErrorCode;

/**
 * 요청 단계에서 정한 규칙(필수값, 앞뒤 공백을 뺀 제목·설명 길이, 예상 소요 값, 목록 쿼리 파라미터)과 성공 응답 형태만 본다.
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
    private static final UUID CATEGORY_ID = UUID.randomUUID();
    private static final String TITLE_MESSAGE = "활동명은 1~20자로 적어주세요.";
    private static final String DESCRIPTION_MESSAGE = "10자 이상 적어주세요.";

    @Autowired
    private MockMvc mockMvc;

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

    @Test
    @DisplayName("참여하면 200이고 data가 없다. 경로의 일정 id와 요청한 사용자로 참여를 부른다")
    void joinsSchedule() throws Exception {
        UUID scheduleId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/schedules/{scheduleId}/participants", scheduleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(joinScheduleFacade).join(scheduleId, USER_ID);
    }

    @Test
    @DisplayName("참여를 취소하면 200이고 data가 없다. 경로의 일정 id와 요청한 사용자로 취소를 부른다")
    void leavesSchedule() throws Exception {
        UUID scheduleId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/schedules/{scheduleId}/participants/me", scheduleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(scheduleCommandService).leave(scheduleId, USER_ID);
    }

    // 목록 3종

    @Test
    @DisplayName("모집 중 목록은 200과 카드 필드, nextCursor를 준다. 장소 필드는 없다")
    void listsRecruiting() throws Exception {
        UUID scheduleId = UUID.randomUUID();
        when(listSchedulesFacade.listRecruiting(any())).thenReturn(page(scheduleId, "next-cursor"));

        mockMvc.perform(get("/api/v1/schedules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.items[0].id").value(scheduleId.toString()))
                .andExpect(jsonPath("$.data.items[0].category.id").value(CATEGORY_ID.toString()))
                .andExpect(jsonPath("$.data.items[0].category.name").value("보드게임"))
                .andExpect(jsonPath("$.data.items[0].title").value("보드게임 같이 해요"))
                .andExpect(jsonPath("$.data.items[0].regionName").value("마포구"))
                .andExpect(jsonPath("$.data.items[0].startAt").value("2026-11-14T19:30:00"))
                .andExpect(jsonPath("$.data.items[0].confirmedCount").value(2))
                .andExpect(jsonPath("$.data.items[0].capacity").value(6))
                .andExpect(jsonPath("$.data.items[0].remainingSeats").value(4))
                .andExpect(jsonPath("$.data.items[0].displayStatus").value("RECRUITING"))
                .andExpect(jsonPath("$.data.items[0].createdAt").value("2026-11-10T09:00:00"))
                .andExpect(jsonPath("$.data.items[0].place").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].placeName").doesNotExist())
                .andExpect(jsonPath("$.data.nextCursor").value("next-cursor"));
    }

    @Test
    @DisplayName("참여한 목록은 200이고 SecurityContext의 사용자로 facade를 부른다. 마지막 페이지면 nextCursor는 null")
    void listsParticipating() throws Exception {
        when(listSchedulesFacade.listParticipating(USER_ID, "c", 10)).thenReturn(page(UUID.randomUUID(), null));

        mockMvc.perform(get("/api/v1/schedules/participating").param("cursor", "c").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].regionName").value("마포구"))
                .andExpect(jsonPath("$.data.items[0].place").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].placeName").doesNotExist())
                .andExpect(jsonPath("$.data.nextCursor").value(nullValue()));

        verify(listSchedulesFacade).listParticipating(USER_ID, "c", 10);
    }

    @Test
    @DisplayName("개설한 목록은 200이고 SecurityContext의 사용자로 facade를 부른다")
    void listsHosted() throws Exception {
        when(listSchedulesFacade.listHosted(USER_ID, null, null)).thenReturn(page(UUID.randomUUID(), "next"));

        mockMvc.perform(get("/api/v1/schedules/hosted"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].displayStatus").value("RECRUITING"))
                .andExpect(jsonPath("$.data.items[0].place").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].placeName").doesNotExist())
                .andExpect(jsonPath("$.data.nextCursor").value("next"));

        verify(listSchedulesFacade).listHosted(USER_ID, null, null);
    }

    @Test
    @DisplayName("반복한 categoryIds·regionCodes는 목록으로, 날짜·커서·size는 그대로 facade에 넘어간다")
    void repeatedParametersBecomeLists() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(listSchedulesFacade.listRecruiting(any())).thenReturn(new ScheduleListResult(List.of(), null));

        mockMvc.perform(get("/api/v1/schedules")
                        .param("categoryIds", first.toString(), second.toString())
                        .param("regionCodes", "11440", "11410")
                        .param("startDate", "2026-11-14")
                        .param("endDate", "2026-11-15")
                        .param("cursor", "abc")
                        .param("size", "30"))
                .andExpect(status().isOk());

        ArgumentCaptor<ListRecruitingSchedulesCommand> command =
                ArgumentCaptor.forClass(ListRecruitingSchedulesCommand.class);
        verify(listSchedulesFacade).listRecruiting(command.capture());
        assertThat(command.getValue().categoryIds()).containsExactly(first, second);
        assertThat(command.getValue().regionCodes()).containsExactly("11440", "11410");
        assertThat(command.getValue().startDate()).hasToString("2026-11-14");
        assertThat(command.getValue().endDate()).hasToString("2026-11-15");
        assertThat(command.getValue().cursor()).isEqualTo("abc");
        assertThat(command.getValue().size()).isEqualTo(30);
    }

    @Test
    @DisplayName("빈 값으로 보낸 categoryIds·regionCodes는 400이 아니라 200이다")
    void blankListParametersAreAccepted() throws Exception {
        when(listSchedulesFacade.listRecruiting(any())).thenReturn(new ScheduleListResult(List.of(), null));

        mockMvc.perform(get("/api/v1/schedules").param("categoryIds", "").param("regionCodes", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"startDate=2026-13-01", "size=abc", "categoryIds=abc"})
    @DisplayName("없는 날짜, 숫자가 아닌 size, UUID가 아닌 categoryIds는 400 INVALID_REQUEST")
    void malformedParameters(String query) throws Exception {
        mockMvc.perform(get("/api/v1/schedules?" + query))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verify(listSchedulesFacade, never()).listRecruiting(any());
    }

    @Test
    @DisplayName("facade가 INVALID_DATE_FILTER를 던지면 400과 그 code")
    void invalidDateFilter() throws Exception {
        when(listSchedulesFacade.listRecruiting(any()))
                .thenThrow(new BusinessException(ScheduleErrorCode.INVALID_DATE_FILTER));

        mockMvc.perform(get("/api/v1/schedules").param("startDate", "2026-11-15").param("endDate", "2026-11-14"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_FILTER"));
    }

    @Test
    @DisplayName("facade가 INVALID_CURSOR를 던지면 400과 그 code")
    void invalidCursor() throws Exception {
        when(listSchedulesFacade.listHosted(USER_ID, "broken", null))
                .thenThrow(new BusinessException(ScheduleErrorCode.INVALID_CURSOR));

        mockMvc.perform(get("/api/v1/schedules/hosted").param("cursor", "broken"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
    }

    // 상세

    @Test
    @DisplayName("상세는 인증 없이 부르면(principal null) 보는 사람 null로 facade를 부르고 200이다")
    void detailWithoutAuthentication() throws Exception {
        SecurityContextHolder.clearContext();
        UUID scheduleId = UUID.randomUUID();
        when(getScheduleDetailFacade.get(scheduleId, null)).thenReturn(detail(scheduleId, ViewerRole.GUEST));

        mockMvc.perform(get("/api/v1/schedules/{scheduleId}", scheduleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.viewerRole").value("GUEST"));

        verify(getScheduleDetailFacade).get(scheduleId, null);
    }

    @Test
    @DisplayName("상세는 인증했으면 그 사용자로 facade를 부른다. 응답 필드를 펼쳐 주고 cancellation은 없다")
    void detailWithAuthentication() throws Exception {
        UUID scheduleId = UUID.randomUUID();
        when(getScheduleDetailFacade.get(scheduleId, USER_ID))
                .thenReturn(detail(scheduleId, ViewerRole.NON_PARTICIPANT));

        mockMvc.perform(get("/api/v1/schedules/{scheduleId}", scheduleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(scheduleId.toString()))
                .andExpect(jsonPath("$.data.category.name").value("보드게임"))
                .andExpect(jsonPath("$.data.host.nickname").value("민트초코"))
                .andExpect(jsonPath("$.data.host.withdrawn").value(false))
                .andExpect(jsonPath("$.data.remainingSeats").value(4))
                .andExpect(jsonPath("$.data.participantSummary.count").value(2))
                .andExpect(jsonPath("$.data.participantSummary.minAgeGroup.decade").value(20))
                .andExpect(jsonPath("$.data.participantSummary.minAgeGroup.phase").value("LATE"))
                .andExpect(jsonPath("$.data.participantSummary.maxAgeGroup").value(nullValue()))
                .andExpect(jsonPath("$.data.place.name").value("보드게임카페"))
                .andExpect(jsonPath("$.data.place.regionName").value("마포구"))
                .andExpect(jsonPath("$.data.displayStatus").value("RECRUITING"))
                .andExpect(jsonPath("$.data.viewerRole").value("NON_PARTICIPANT"))
                .andExpect(jsonPath("$.data.availableActions[0]").value("JOIN"))
                .andExpect(jsonPath("$.data.cancellation").doesNotExist());

        verify(getScheduleDetailFacade).get(scheduleId, USER_ID);
    }

    @Test
    @DisplayName("facade가 SCHEDULE_NOT_FOUND를 던지면 404와 그 code")
    void detailNotFound() throws Exception {
        UUID scheduleId = UUID.randomUUID();
        when(getScheduleDetailFacade.get(scheduleId, USER_ID))
                .thenThrow(new BusinessException(ScheduleErrorCode.SCHEDULE_NOT_FOUND));

        mockMvc.perform(get("/api/v1/schedules/{scheduleId}", scheduleId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCHEDULE_NOT_FOUND"));
    }

    private static ScheduleDetailResult detail(UUID scheduleId, ViewerRole viewerRole) {
        ScheduleDetailQueryResult schedule = new ScheduleDetailQueryResult(scheduleId, "보드게임 같이 해요",
                "처음 오시는 분도 환영해요.", LocalDateTime.of(2026, 11, 14, 19, 30), LocalDateTime.of(2026, 11, 10, 9, 0),
                DurationType.NORMAL, CATEGORY_ID, "보드게임", UUID.randomUUID(), List.of(), 2, 6, 4,
                new ScheduleDetailQueryResult.Place("보드게임카페", "서울특별시 마포구 와우산로 1", "2층",
                        new BigDecimal("37.5500000"), new BigDecimal("126.9200000"), "11440"),
                DisplayStatus.RECRUITING, viewerRole, List.of(ScheduleAction.JOIN));
        return new ScheduleDetailResult(schedule, new ScheduleHostResult("민트초코", "/images/default-profile.png", false),
                new ParticipantSummaryResult(2, 1, 1, new ScheduleAgeGroupResult(20, AgePhase.LATE), null), "마포구");
    }

    private static ScheduleListResult page(UUID scheduleId, String nextCursor) {
        ScheduleCardResult card = new ScheduleCardResult(scheduleId, CATEGORY_ID, "보드게임", "보드게임 같이 해요", "11440",
                LocalDateTime.of(2026, 11, 14, 19, 30), 2, 6, 4, DisplayStatus.RECRUITING,
                LocalDateTime.of(2026, 11, 10, 9, 0));
        return new ScheduleListResult(List.of(new ScheduleListItemResult(card, "마포구")), nextCursor);
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
