package com.shineunsigan.snsg.schedule.presentation;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shineunsigan.snsg.common.web.ApiResponse;
import com.shineunsigan.snsg.schedule.application.CreateScheduleUseCase;
import com.shineunsigan.snsg.schedule.presentation.dto.request.CreateScheduleRequest;
import com.shineunsigan.snsg.schedule.presentation.dto.response.CreateScheduleResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

@Tag(name = "일정")
@RestController
@RequestMapping("/api/v1/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final CreateScheduleUseCase createScheduleUseCase;

    @PostMapping
    @Operation(summary = "일정 개설",
            description = """
                    일정을 연다. 일정 개설 화면의 [일정 열기]에서 부른다. 로그인이 필요하다.
                    개설자는 확정 참여자 1명으로 함께 들어가고, 일정은 모집 중 상태로 시작한다.
                    장소는 placeId만 보낸다. 서버가 장소의 이름·주소·좌표를 읽어 개설 시점 값으로 일정에 저장한다.
                    두 번 눌려 같은 요청이 두 번 와도 일정은 하나만 생긴다. 두 번째 요청은 409 SCHEDULE_TIME_CONFLICT다.
                    그래도 첫 응답을 받기 전까지 버튼을 막는다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                    description = "개설 완료. data.scheduleId가 만든 일정의 ID다. 내가 연 일정 목록(LIST-05)으로 이동하고 개설 완료 팝업을 띄운다"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = """
                            INVALID_REQUEST: 필수값 누락, 활동명·설명 길이 위반(errors에 필드별 문구), 없는 durationType 값.
                            INVALID_CATEGORY: 없거나 숨긴 활동. 활동 목록을 다시 불러 고르게 한다.
                            INVALID_PLACE: 없거나 노출이 끝난 장소, 활동과 유형이 다른 장소. 장소를 다시 고르게 한다.
                            INVALID_START_AT: 30분 단위가 아니거나 지금 + 1시간보다 이르거나 오늘 + 30일보다 늦은 시작 일시. 시작 일시를 다시 고르게 한다.
                            INVALID_CAPACITY: 개설자 포함 4~24명 밖의 인원.
                            INVALID_TITLE·INVALID_DESCRIPTION: 서버의 안전망. 요청 검증을 통과한 값이면 나지 않는다"""),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = """
                            UNAUTHENTICATED: 토큰이 없거나 만료·무효. 재발급 후 다시 부르거나 로그인 화면으로 간다.
                            USER_NOT_ACTIVE: 탈퇴한 사용자. 로그인 화면으로 간다"""),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = """
                            SCHEDULE_TIME_CONFLICT: 내가 개설했거나 참여 확정한 예정 일정과 시작 시각이 12시간 미만으로 떨어진다.
                            정확히 12시간 차이는 허용한다. 시작 일시를 바꾸게 안내한다""")})
    public ResponseEntity<ApiResponse<CreateScheduleResponse>> create(@AuthenticationPrincipal UUID userId,
            @Valid @RequestBody CreateScheduleRequest request) {
        UUID scheduleId = createScheduleUseCase.create(request.toCommand(userId));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(new CreateScheduleResponse(scheduleId)));
    }
}
