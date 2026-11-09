package com.shineunsigan.snsg.schedule.presentation;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shineunsigan.snsg.common.web.ApiResponse;
import com.shineunsigan.snsg.schedule.application.CreateScheduleFacade;
import com.shineunsigan.snsg.schedule.application.GetScheduleDetailFacade;
import com.shineunsigan.snsg.schedule.application.JoinScheduleFacade;
import com.shineunsigan.snsg.schedule.application.ListSchedulesFacade;
import com.shineunsigan.snsg.schedule.application.ScheduleCommandService;
import com.shineunsigan.snsg.schedule.application.dto.command.ListRecruitingSchedulesCommand;
import com.shineunsigan.snsg.schedule.presentation.dto.request.CreateScheduleRequest;
import com.shineunsigan.snsg.schedule.presentation.dto.response.CreateScheduleResponse;
import com.shineunsigan.snsg.schedule.presentation.dto.response.ScheduleCardPageResponse;
import com.shineunsigan.snsg.schedule.presentation.dto.response.ScheduleDetailResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
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
    private final ListSchedulesFacade listSchedulesFacade;
    private final GetScheduleDetailFacade getScheduleDetailFacade;

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
                    성공하면 200이고 data는 없다. 성공이든 실패든 일정 상세(GET /api/v1/schedules/{scheduleId})를 다시 불러 화면을 그린다.
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
                    성공하면 200이고 data는 없다. 성공이든 실패든 일정 상세(GET /api/v1/schedules/{scheduleId})를 다시 불러 화면을 그린다.
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

    /** 파라미터는 모두 선택이다. 기본값을 주지 않는다. 생략은 null이고 size의 기본값은 서버 설정이 정한다. */
    @SecurityRequirements
    @GetMapping
    @Operation(summary = "모집 중 일정 목록",
            description = """
                    참여할 수 있는 일정을 카드로 보여준다. 목록 화면의 모집 중 탭에 들어올 때, 필터(활동·지역·날짜)를 바꿀 때,
                    끝까지 스크롤해 다음 페이지가 필요할 때 부른다. 토큰 없이 부를 수 있고, 토큰을 보내도 응답은 같다.
                    취소되지 않았고 시작 전인 일정이 모두 온다. 정원이 찬 일정도 displayStatus = RECRUITMENT_CLOSED(모집 마감)로 함께 온다.
                    정렬은 시작 일시가 빠른 순(같으면 id 순)이다. 한 번에 size개(기본 20, 최대 50)를 주고, 더 있으면 nextCursor를 준다.
                    다음 페이지는 같은 필터에 cursor = nextCursor를 붙여 부른다. 필터를 바꾸면 cursor 없이 처음부터 부른다.
                    맞는 일정이 없으면 items는 빈 배열이고 nextCursor는 null이다.
                    조회만 하므로 같은 요청을 몇 번 다시 보내도 된다.
                    날짜와 커서가 둘 다 틀리면 INVALID_DATE_FILTER가 온다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "일정 카드 한 페이지. nextCursor가 null이면 마지막 페이지다"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = """
                            INVALID_REQUEST: categoryIds가 UUID 형식이 아니거나, categoryIds·regionCodes가 중복을 빼고 20개를 넘거나, 날짜가 yyyy-MM-dd 형식이 아니거나 없는 날짜이거나, size가 숫자가 아니다. 정상 화면 흐름에서는 나지 않는다.
                            INVALID_DATE_FILTER: endDate가 startDate보다 앞선다. 날짜를 다시 고르게 한다.
                            INVALID_CURSOR: 해석할 수 없는 cursor. cursor 없이 처음부터 다시 부른다""")})
    public ResponseEntity<ApiResponse<ScheduleCardPageResponse>> listRecruiting(
            @Parameter(description = """
                    활동 ID. 활동 목록 API의 id. 여러 개면 categoryIds=a&categoryIds=b처럼 반복해 보내고, 그중 하나에 맞으면 온다.
                    생략하면 모든 활동이다. 모르는 ID만 보내면 빈 결과다. 빈 값으로 보내지 않는다(맞는 일정이 없어 빈 결과가 된다).
                    중복을 빼고 최대 20개. 넘으면 400 INVALID_REQUEST""",
                    example = "01a19cc6-3d80-7000-8000-000000000001")
            @RequestParam(required = false) List<UUID> categoryIds,
            @Parameter(description = """
                    지역 코드. 지역 목록 API의 code. 여러 개면 regionCodes=11440&regionCodes=11410처럼 반복해 보내고, 그중 하나에 맞으면 온다.
                    활동과 함께 보내면 둘 다 맞는 일정만 온다. 생략하면 모든 지역이다. 모르는 코드만 보내면 빈 결과다.
                    빈 값으로 보내지 않는다(맞는 일정이 없어 빈 결과가 된다). 중복을 빼고 최대 20개. 넘으면 400 INVALID_REQUEST""",
                    example = "11440")
            @RequestParam(required = false) List<String> regionCodes,
            @Parameter(description = """
                    시작 날짜 범위의 첫날. yyyy-MM-dd(한국 날짜). 그날 00:00부터 포함한다.
                    endDate 없이 보내면 그날 하루다. 생략하면(endDate도 없으면) 날짜 제한이 없다. 지난 날짜도 오류가 아니다(빈 결과)""",
                    example = "2026-11-14")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = """
                    시작 날짜 범위의 마지막 날. yyyy-MM-dd(한국 날짜). 그날 23:59까지 포함한다.
                    startDate 없이 보내면 그날 하루다. startDate와 같으면 그날 하루, startDate보다 앞서면 400 INVALID_DATE_FILTER""",
                    example = "2026-11-15")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "이전 응답의 nextCursor를 그대로 보낸다. 생략하면 첫 페이지다. 해석할 수 없으면 400 INVALID_CURSOR")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "한 페이지 개수. 생략하면 20, 50보다 크면 50, 1보다 작으면 1로 맞춘다", example = "20")
            @RequestParam(required = false) Integer size) {
        ListRecruitingSchedulesCommand command = new ListRecruitingSchedulesCommand(categoryIds, regionCodes,
                startDate, endDate, cursor, size);

        return ResponseEntity.ok(ApiResponse.success(
                ScheduleCardPageResponse.from(listSchedulesFacade.listRecruiting(command))));
    }

    @GetMapping("/participating")
    @Operation(summary = "참여한 일정 목록",
            description = """
                    내가 멤버로 참여한 일정을 카드로 보여준다. 목록 화면의 참여한 일정 탭에서 부른다. 로그인이 필요하다.
                    완료 전(시작 + 12시간 전) 일정만 온다. 시작한 일정도 완료 전이면 온다.
                    개설자가 취소한 일정도 완료 시각 전까지 displayStatus = CANCELED(일정 취소됨)로 남는다.
                    내가 참여를 취소한 일정과 내가 연 일정은 오지 않는다(내가 연 일정은 개설한 일정 목록에 있다).
                    정렬·페이지(cursor, size, nextCursor)는 모집 중 목록과 같다. 필터는 없다.
                    없으면 items는 빈 배열이고 nextCursor는 null이다. 조회만 하므로 같은 요청을 몇 번 다시 보내도 된다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "일정 카드 한 페이지. nextCursor가 null이면 마지막 페이지다"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = """
                            INVALID_REQUEST: size가 숫자가 아니다. 정상 화면 흐름에서는 나지 않는다.
                            INVALID_CURSOR: 해석할 수 없는 cursor. cursor 없이 처음부터 다시 부른다"""),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "UNAUTHENTICATED: 토큰이 없거나 만료·무효. 재발급 후 다시 부르거나 로그인 화면으로 간다")})
    public ResponseEntity<ApiResponse<ScheduleCardPageResponse>> listParticipating(
            @AuthenticationPrincipal UUID userId,
            @Parameter(description = "이전 응답의 nextCursor를 그대로 보낸다. 생략하면 첫 페이지다. 해석할 수 없으면 400 INVALID_CURSOR")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "한 페이지 개수. 생략하면 20, 50보다 크면 50, 1보다 작으면 1로 맞춘다", example = "20")
            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(ApiResponse.success(
                ScheduleCardPageResponse.from(listSchedulesFacade.listParticipating(userId, cursor, size))));
    }

    @GetMapping("/hosted")
    @Operation(summary = "개설한 일정 목록",
            description = """
                    내가 연 일정을 카드로 보여준다. 목록 화면의 개설한 일정 탭에서 부르고, 일정 개설에 성공한 뒤 이 화면(내가 연 일정)으로 와서 부른다.
                    로그인이 필요하다. 완료 전(시작 + 12시간 전) 일정만 온다. 방금 연 일정도 온다.
                    내가 취소한 일정도 완료 시각 전까지 displayStatus = CANCELED(일정 취소됨)로 남는다. 멤버로 참여만 한 일정은 오지 않는다.
                    정렬·페이지(cursor, size, nextCursor)는 모집 중 목록과 같다. 필터는 없다.
                    없으면 items는 빈 배열이고 nextCursor는 null이다. 조회만 하므로 같은 요청을 몇 번 다시 보내도 된다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "일정 카드 한 페이지. nextCursor가 null이면 마지막 페이지다"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = """
                            INVALID_REQUEST: size가 숫자가 아니다. 정상 화면 흐름에서는 나지 않는다.
                            INVALID_CURSOR: 해석할 수 없는 cursor. cursor 없이 처음부터 다시 부른다"""),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "UNAUTHENTICATED: 토큰이 없거나 만료·무효. 재발급 후 다시 부르거나 로그인 화면으로 간다")})
    public ResponseEntity<ApiResponse<ScheduleCardPageResponse>> listHosted(
            @AuthenticationPrincipal UUID userId,
            @Parameter(description = "이전 응답의 nextCursor를 그대로 보낸다. 생략하면 첫 페이지다. 해석할 수 없으면 400 INVALID_CURSOR")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "한 페이지 개수. 생략하면 20, 50보다 크면 50, 1보다 작으면 1로 맞춘다", example = "20")
            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(ApiResponse.success(
                ScheduleCardPageResponse.from(listSchedulesFacade.listHosted(userId, cursor, size))));
    }

    /** 선택 인증 API다. 토큰이 없거나 틀리면 userId는 null이고 GUEST로 본다. 그래서 @SecurityRequirements를 달지 않는다. */
    @GetMapping("/{scheduleId}")
    @Operation(summary = "일정 상세",
            description = """
                    일정 하나의 내용과 보는 사람이 누를 수 있는 버튼을 준다. 일정 상세 화면(DTL)에 들어올 때,
                    그리고 참여·참여 취소·일정 취소를 부른 뒤(성공이든 실패든) 다시 불러 화면을 그린다.
                    토큰은 선택이다. 토큰 없이 부르면 viewerRole = GUEST이고, 토큰이 있으면 그 사용자의 역할(HOST·PARTICIPANT·NON_PARTICIPANT)과
                    그에 맞는 availableActions가 온다. 틀리거나 만료된 토큰은 401이 아니라 토큰이 없는 것(GUEST)으로 처리한다.
                    버튼은 availableActions에 든 동작만 활성으로 그린다. 비활성 버튼의 문구(모집 마감·완료·취소됨 등)는 displayStatus를 보고 FE가 정한다.
                    시작 시각이 지났는데 아직 모집 중으로 저장된 일정(상태 전환 전)도 displayStatus = RECRUITMENT_CLOSED로 온다.
                    장소는 개설 시점의 값이다. 일정 취소 사유는 아직 응답에 없다(일정 전체 취소 API와 함께 생긴다).
                    조회만 하므로 같은 요청을 몇 번 다시 보내도 된다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "일정 상세. availableActions의 버튼만 활성으로 그린다"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "INVALID_REQUEST: scheduleId가 UUID 형식이 아니다. 정상 화면 흐름에서는 나지 않는다"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = """
                            SCHEDULE_NOT_FOUND: 없거나 삭제된 일정. "찾을 수 없는 일정이에요." 토스트를 보여주고 목록 화면으로 간다.
                            없는 경로의 404(INVALID_REQUEST)와는 code로 구분한다""")})
    public ResponseEntity<ApiResponse<ScheduleDetailResponse>> getDetail(@AuthenticationPrincipal UUID userId,
            @PathVariable UUID scheduleId) {
        return ResponseEntity.ok(ApiResponse.success(
                ScheduleDetailResponse.from(getScheduleDetailFacade.get(scheduleId, userId))));
    }
}
