package com.shineunsigan.snsg.schedule.presentation;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shineunsigan.snsg.common.web.ApiResponse;
import com.shineunsigan.snsg.schedule.application.CreateScheduleFacade;
import com.shineunsigan.snsg.schedule.application.JoinScheduleFacade;
import com.shineunsigan.snsg.schedule.application.ScheduleCommandService;
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

    private final CreateScheduleFacade createScheduleFacade;
    private final JoinScheduleFacade joinScheduleFacade;
    private final ScheduleCommandService scheduleCommandService;

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
        UUID scheduleId = createScheduleFacade.create(request.toCommand(userId));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(new CreateScheduleResponse(scheduleId)));
    }

    @PostMapping("/{scheduleId}/participants")
    @Operation(summary = "참여",
            description = """
                    일정에 멤버로 참여한다. 일정 상세 화면의 [참여하기]에서 부른다. 로그인이 필요하다. 요청 본문은 없다.
                    성공하면 200이고 data는 없다. 성공이든 실패든 일정 상세를 다시 불러 화면을 그린다(상세 API는 아직 없다).
                    마지막 자리가 차면 일정은 모집 마감이 된다. 마지막 자리에 동시에 둘이 들어오면 한 명만 성공하고 다른 한 명은 409 RECRUIT_CLOSED다.
                    참여를 취소한 뒤 다시 참여할 수 있다.
                    두 번 눌려 같은 요청이 두 번 와도 참여는 한 번이다. 두 번째 요청은 409 ALREADY_JOINED이고, 상태는 한 번 참여한 것과 같다.
                    여러 조건에 걸리면 USER_NOT_ACTIVE → SCHEDULE_NOT_FOUND → HOST_CANNOT_JOIN → ALREADY_JOINED → RECRUIT_CLOSED → SCHEDULE_TIME_CONFLICT 순서로 앞의 것이 온다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "참여 완료. data는 없다. 일정 상세를 다시 불러 그린다"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "INVALID_REQUEST: scheduleId가 UUID 형식이 아니다. 정상 화면 흐름에서는 나지 않는다"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = """
                            UNAUTHENTICATED: 토큰이 없거나 만료·무효. 재발급 후 다시 부르거나 로그인 화면으로 간다.
                            USER_NOT_ACTIVE: 탈퇴한 사용자. 로그인 화면으로 간다"""),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = """
                            SCHEDULE_NOT_FOUND: 없거나 삭제된 일정. "찾을 수 없는 일정이에요"를 보여주고 이전 화면으로 간다.
                            없는 경로의 404(INVALID_REQUEST)와는 code로 구분한다"""),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = """
                            HOST_CANNOT_JOIN: 내가 연 일정이다. 상세를 다시 불러 그린다.
                            ALREADY_JOINED: 이미 참여 확정한 일정이다(두 번 눌렸을 때 포함). 상세를 다시 불러 그린다.
                            RECRUIT_CLOSED: 모집이 끝났다. 정원이 찼거나, 시작 시각이 됐거나, 취소·완료된 일정이다. 상세를 다시 불러 그린다.
                            SCHEDULE_TIME_CONFLICT: 내가 개설했거나 참여 확정한 예정 일정과 시작 시각이 12시간 미만으로 떨어진다. 정확히 12시간 차이는 허용한다. message를 보여준다""")})
    public ResponseEntity<ApiResponse<Void>> join(@AuthenticationPrincipal UUID userId,
            @PathVariable UUID scheduleId) {
        joinScheduleFacade.join(scheduleId, userId);

        return ResponseEntity.ok(ApiResponse.success());
    }

    @DeleteMapping("/{scheduleId}/participants/me")
    @Operation(summary = "참여 취소",
            description = """
                    내 참여를 취소한다. 참여 취소 확인 팝업의 [확인]에서 부른다. 로그인이 필요하다. 요청 본문은 없다.
                    성공하면 200이고 data는 없다. 성공이든 실패든 일정 상세를 다시 불러 화면을 그린다(상세 API는 아직 없다).
                    시작 후에도 완료(시작 + 12시간) 전까지 취소할 수 있다. 시작 전에 취소해 자리가 나면 마감된 모집이 다시 열리고,
                    시작 후 취소로 자리가 나도 모집은 다시 열리지 않는다. 취소한 뒤 다시 참여할 수 있다.
                    두 번 눌려 같은 요청이 두 번 와도 취소는 한 번이다. 두 번째 요청은 409 NOT_PARTICIPANT이고, 재시도해도 안전하다.
                    여러 조건에 걸리면 SCHEDULE_NOT_FOUND → HOST_CANNOT_LEAVE → NOT_PARTICIPANT → PARTICIPATION_NOT_CANCELABLE 순서로 앞의 것이 온다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "참여 취소 완료. data는 없다. 일정 상세를 다시 불러 그린다"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "INVALID_REQUEST: scheduleId가 UUID 형식이 아니다. 정상 화면 흐름에서는 나지 않는다"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "UNAUTHENTICATED: 토큰이 없거나 만료·무효. 재발급 후 다시 부르거나 로그인 화면으로 간다"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = """
                            SCHEDULE_NOT_FOUND: 없거나 삭제된 일정. "찾을 수 없는 일정이에요"를 보여주고 이전 화면으로 간다.
                            없는 경로의 404(INVALID_REQUEST)와는 code로 구분한다"""),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = """
                            HOST_CANNOT_LEAVE: 내가 연 일정이다. 개설자는 참여를 취소할 수 없다. 상세를 다시 불러 그린다.
                            NOT_PARTICIPANT: 참여 확정 상태가 아니다(참여한 적 없음, 이미 취소함 포함). 재시도해도 안전하다. 상세를 다시 불러 그린다.
                            PARTICIPATION_NOT_CANCELABLE: 취소됐거나 완료된(시작 + 12시간이 지난) 일정이다. 상세를 다시 불러 그린다""")})
    public ResponseEntity<ApiResponse<Void>> leave(@AuthenticationPrincipal UUID userId,
            @PathVariable UUID scheduleId) {
        scheduleCommandService.leave(scheduleId, userId);

        return ResponseEntity.ok(ApiResponse.success());
    }
}
