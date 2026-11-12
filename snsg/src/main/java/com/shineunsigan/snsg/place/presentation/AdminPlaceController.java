package com.shineunsigan.snsg.place.presentation;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shineunsigan.snsg.common.web.ApiResponse;
import com.shineunsigan.snsg.place.application.PlaceReviewCommandService;
import com.shineunsigan.snsg.place.application.PlaceReviewQueryService;
import com.shineunsigan.snsg.place.application.dto.command.ListPlacesCommand;
import com.shineunsigan.snsg.place.domain.constants.PlaceSource;
import com.shineunsigan.snsg.place.domain.constants.PlaceStatus;
import com.shineunsigan.snsg.place.domain.constants.PlaceType;
import com.shineunsigan.snsg.place.presentation.dto.request.AdminPlaceCreateRequest;
import com.shineunsigan.snsg.place.presentation.dto.request.AdminPlaceUpdateRequest;
import com.shineunsigan.snsg.place.presentation.dto.response.AdminPlaceListResponse;
import com.shineunsigan.snsg.place.presentation.dto.response.AdminPlaceResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 어드민 장소 검수. 승인된 어드민(ADMIN·MASTER) 누구나 쓴다. 인증은 어드민 보안 체인이 맡고, place는 admin을 모른다.
 * 감사 값(created_by·updated_by)은 JPA Auditing이 principal(adminId)에서 채우고,
 * 검수자(reviewed_by)는 여기서 받은 adminId를 서비스에 넘겨 남긴다.
 */
@Tag(name = "어드민 장소", description = "장소 검수·수정·MANUAL 등록. 승인된 어드민(ADMIN·MASTER) 누구나 쓴다")
@RestController
@RequestMapping("/api/admin/v1/places")
@RequiredArgsConstructor
public class AdminPlaceController {

    private static final String UNAUTHENTICATED_401 = "UNAUTHENTICATED: 토큰이 없거나 만료·무효이거나, "
            + "어드민이 승인 상태가 아니다(이용 중지됨 포함). 재발급 후 다시 부르고, 재발급도 401이면 로그인 화면으로 간다";
    private static final String NOT_FOUND_404 = "PLACE_NOT_FOUND: 없는 장소다. 목록을 다시 불러 그린다. "
            + "없는 경로의 404(INVALID_REQUEST)와는 code로 구분한다";
    private static final String WRITE_400 = """
            INVALID_REQUEST(errors 있음): 필드 형식 오류. errors[].message를 그 입력칸 아래에 보여준다.
            INVALID_REQUEST(errors 없음): 본문 없음·잘못된 JSON·목록 밖 enum 값·숫자가 아닌 좌표. 정상 화면 흐름에서는 나지 않는다. message를 보여준다.
            INVALID_REGION: regionCode가 지역 목록에 없다. 지역을 다시 고르게 한다.
            UNCLASSIFIED_NOT_APPROVABLE: 유형 미정(UNCLASSIFIED)으로 승인하려 했다. 유형을 고르게 한다""";

    private final PlaceReviewQueryService placeReviewQueryService;
    private final PlaceReviewCommandService placeReviewCommandService;

    @GetMapping
    @Operation(summary = "장소 목록",
            description = """
                    장소 검수 화면에서 부른다. 노출 여부와 상관없이 모든 장소(검수 대기·승인·거절, 폐업 포함, MANUAL 포함)를 준다.
                    필터는 모두 선택이고 각각 값 하나만 받는다. 여러 필터를 주면 모두 맞는 장소만 준다.
                    정렬은 만든 일시(createdAt)가 빠른 순(같으면 id 순)이다. 한 번에 size개(기본 20, 최대 50)를 주고, 더 있으면 nextCursor를 준다.
                    다음 페이지는 같은 필터에 cursor = nextCursor를 붙여 부른다. 필터를 바꾸면 cursor 없이 처음부터 부른다.
                    맞는 장소가 없으면(없는 지역 코드 포함) items는 빈 배열이고 nextCursor는 null이다.
                    조회만 하므로 같은 요청을 몇 번 다시 보내도 된다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "장소 한 페이지. nextCursor가 null이면 마지막 페이지다"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = """
                            INVALID_REQUEST: status·placeType·source가 목록 밖 값이거나 closed가 true·false가 아니거나 size가 숫자가 아니다. 정상 화면 흐름에서는 나지 않는다.
                            INVALID_CURSOR: 해석할 수 없는 cursor. cursor 없이 처음부터 다시 부른다"""),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = UNAUTHENTICATED_401)})
    public ResponseEntity<ApiResponse<AdminPlaceListResponse>> listPlaces(
            @Parameter(description = "이 화면 지역 코드(regionCode)의 장소만 준다. 생략하거나 비우면 모든 지역. "
                    + "없는 코드면 빈 결과(오류 아님)", example = "11440")
            @RequestParam(required = false) String regionCode,
            @Parameter(description = "이 검수 상태만 준다. CANDIDATE·APPROVED·REJECTED. 생략하면 모든 상태. 그 밖의 값은 400 INVALID_REQUEST",
                    example = "CANDIDATE")
            @RequestParam(required = false) PlaceStatus status,
            @Parameter(description = "이 유형만 준다. BOARD_GAME_CAFE·ESCAPE_ROOM·PARK·UNCLASSIFIED. 생략하면 모든 유형. "
                    + "그 밖의 값은 400 INVALID_REQUEST", example = "UNCLASSIFIED")
            @RequestParam(required = false) PlaceType placeType,
            @Parameter(description = "이 원천만 준다. SEMAS·PARK_STANDARD·MANUAL. 생략하면 모든 원천. 그 밖의 값은 400 INVALID_REQUEST",
                    example = "SEMAS")
            @RequestParam(required = false) PlaceSource source,
            @Parameter(description = "true면 폐업 장소만, false면 폐업이 아닌 장소만 준다. 생략하면 둘 다", example = "true")
            @RequestParam(required = false) Boolean closed,
            @Parameter(description = """
                    장소명·도로명 주소·원천 이름·원천 도로명 주소 중 하나에 이 글자가 들어 있는 장소만 준다. 영문 대소문자는 가리지 않는다.
                    %와 _도 글자 그대로 찾는다. 앞뒤 공백은 뺀다. 생략하거나 비었거나 공백뿐이면 걸지 않는다""",
                    example = "와우산로")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "이전 응답의 nextCursor를 그대로 보낸다. 생략하면 첫 페이지다. 해석할 수 없으면 400 INVALID_CURSOR")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "한 페이지 개수. 생략하면 20, 50보다 크면 50, 1보다 작으면 1로 맞춘다", example = "20")
            @RequestParam(required = false) Integer size) {
        ListPlacesCommand command = new ListPlacesCommand(regionCode, status, placeType, source, closed, keyword,
                cursor, size);
        return ResponseEntity.ok(ApiResponse.success(AdminPlaceListResponse.from(placeReviewQueryService.list(command))));
    }

    @GetMapping("/{placeId}")
    @Operation(summary = "장소 상세",
            description = """
                    장소 하나를 준다. 목록 항목과 같은 필드다. 수정 화면을 열 때 지금 값을 다시 받아 채운다.
                    조회만 하므로 같은 요청을 몇 번 다시 보내도 된다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "장소"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "INVALID_REQUEST: placeId가 UUID 형식이 아니다. 정상 화면 흐름에서는 나지 않는다"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = UNAUTHENTICATED_401),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = NOT_FOUND_404)})
    public ResponseEntity<ApiResponse<AdminPlaceResponse>> getPlace(@PathVariable UUID placeId) {
        return ResponseEntity.ok(ApiResponse.success(AdminPlaceResponse.from(placeReviewQueryService.get(placeId))));
    }

    @PutMapping("/{placeId}")
    @Operation(summary = "장소 수정(검수)",
            description = """
                    유형·검수 상태·화면 값(이름·도로명 주소·상세주소·좌표·지역)을 모두 보낸 값으로 바꾼다. 부분 수정은 없다.
                    수정 화면은 상세의 지금 값으로 채운 뒤 바꾼 칸만 고쳐 전체를 보낸다.
                    검수 상태는 APPROVED(승인) 또는 REJECTED(거절)만 보낼 수 있다. 유형 미정(UNCLASSIFIED)은 거절만 된다.
                    원천 값(source*), 폐업 여부(isClosed), 마지막 확인 일시(lastSyncedAt), 원천(source)은 바꿀 수 없다. 폐업한 장소도 고칠 수 있다.
                    성공하면 200이고 data는 수정된 장소다. reviewedBy가 요청한 어드민 id, reviewedAt이 지금이 된다.
                    같은 요청을 다시 보내도 결과는 같다(reviewedAt만 바뀐다). 다른 어드민과 동시에 고치면 나중에 처리된 요청이 남는다.
                    여러 조건에 걸리면 PLACE_NOT_FOUND → INVALID_REGION → INVALID_REVIEW_STATUS → UNCLASSIFIED_NOT_APPROVABLE 순서로 앞의 것이 온다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정된 장소"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = WRITE_400 + """

                            INVALID_REVIEW_STATUS: status가 CANDIDATE다. 승인·거절 중에서 고르게 한다.
                            INVALID_REQUEST(errors 없음): placeId가 UUID 형식이 아니다"""),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = UNAUTHENTICATED_401),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = NOT_FOUND_404)})
    public ResponseEntity<ApiResponse<AdminPlaceResponse>> updatePlace(@AuthenticationPrincipal UUID adminId,
            @PathVariable UUID placeId, @Valid @RequestBody AdminPlaceUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(AdminPlaceResponse.from(
                placeReviewCommandService.review(request.toCommand(placeId, adminId)))));
    }

    @PostMapping
    @Operation(summary = "MANUAL 장소 등록",
            description = """
                    공공데이터(적재)에 없는 장소를 직접 등록한다. 등록하면 바로 승인(APPROVED)이고 원천은 MANUAL이다.
                    지역이 활성이면 바로 사용자에게 노출된다. 적재는 MANUAL 장소를 바꾸거나 폐업 처리하지 않는다.
                    원천 값은 빈 문자열과 0, sourceId는 빈 문자열, lastSyncedAt은 등록 일시다.
                    성공하면 201이고 data는 등록된 장소다. createdBy·updatedBy·reviewedBy가 요청한 어드민 id다.
                    같은 요청을 두 번 보내면 장소가 두 개 생긴다. 응답이 올 때까지 버튼을 막는다.
                    여러 조건에 걸리면 INVALID_REGION → UNCLASSIFIED_NOT_APPROVABLE 순서로 앞의 것이 온다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "등록된 장소"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = WRITE_400),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = UNAUTHENTICATED_401)})
    public ResponseEntity<ApiResponse<AdminPlaceResponse>> createPlace(@AuthenticationPrincipal UUID adminId,
            @Valid @RequestBody AdminPlaceCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(AdminPlaceResponse.from(
                placeReviewCommandService.createManual(request.toCommand(adminId)))));
    }
}
