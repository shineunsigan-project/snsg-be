package com.shineunsigan.snsg.admin.presentation;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shineunsigan.snsg.admin.application.AdminCommandService;
import com.shineunsigan.snsg.admin.application.AdminQueryService;
import com.shineunsigan.snsg.admin.application.SuspendAdminFacade;
import com.shineunsigan.snsg.admin.domain.constants.AdminStatus;
import com.shineunsigan.snsg.admin.presentation.dto.response.AdminListResponse;
import com.shineunsigan.snsg.admin.presentation.dto.response.AdminMeResponse;
import com.shineunsigan.snsg.common.web.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 내 정보와 MASTER 전용 어드민 관리(목록, 가입 승인·거절, 이용 중지·해제).
 * MASTER 전용인지는 보안 체인({@code AdminSecurityConfig})이 역할로 가른다. 여기서는 역할을 보지 않는다.
 */
@Tag(name = "어드민", description = "어드민 계정. 목록·승인·거절·이용 중지·해제는 MASTER 전용이다")
@RestController
@RequestMapping("/api/admin/v1/admins")
@RequiredArgsConstructor
public class AdminController {

    private static final String UNAUTHENTICATED_401 = "UNAUTHENTICATED: 토큰이 없거나 만료·무효이거나, "
            + "어드민이 승인 상태가 아니다(이용 중지됨 포함). 재발급 후 다시 부르고, 재발급도 401이면 로그인 화면으로 간다";
    private static final String MASTER_ONLY_403 = "MASTER_ONLY: MASTER가 아닌 어드민(ADMIN)이 불렀다. "
            + "MASTER 메뉴는 내 정보의 role이 MASTER일 때만 그린다. \"MASTER만 할 수 있어요\"를 보여준다";
    private static final String TARGET_403 = MASTER_ONLY_403 + "\n"
            + "MASTER_NOT_MODIFIABLE: 대상이 MASTER다(자기 자신 포함). MASTER는 API로 승인·거절·이용 중지·해제할 수 없다. "
            + "MASTER 행에는 버튼을 그리지 않는다";
    private static final String NOT_FOUND_404 = "ADMIN_NOT_FOUND: 없거나 삭제된 어드민. 목록을 다시 불러 그린다. "
            + "없는 경로의 404(INVALID_REQUEST)와는 code로 구분한다";
    private static final String CONFLICT_409 = "ADMIN_STATUS_CONFLICT: 대상의 지금 상태에서 할 수 없는 동작이다"
            + "(이미 처리됨, 다른 MASTER가 먼저 처리함 포함). 아무것도 바뀌지 않았다. 목록을 다시 불러 그린다.";
    private static final String INVALID_ID_400 =
            "INVALID_REQUEST: adminId가 UUID 형식이 아니다. 정상 화면 흐름에서는 나지 않는다";

    private final AdminQueryService adminQueryService;
    private final AdminCommandService adminCommandService;
    private final SuspendAdminFacade suspendAdminFacade;

    @GetMapping("/me")
    @Operation(summary = "내 정보",
            description = """
                    로그인한 어드민의 정보를 돌려준다. 로그인·재발급 직후 불러 화면 상단의 이름과 역할별 메뉴(MASTER 전용 메뉴)를 그린다.
                    조회만 하므로 같은 요청을 몇 번 다시 보내도 된다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "내 정보"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "UNAUTHENTICATED: 토큰이 없거나 만료·무효이거나, 어드민이 승인 상태가 아니다. 재발급 후 다시 부르고, 재발급도 401이면 로그인 화면으로 간다")})
    public ResponseEntity<ApiResponse<AdminMeResponse>> getMe(@AuthenticationPrincipal UUID adminId) {
        return ResponseEntity.ok(ApiResponse.success(AdminMeResponse.from(adminQueryService.getMe(adminId))));
    }

    @GetMapping
    @Operation(summary = "어드민 목록 (MASTER 전용)",
            description = """
                    가입 승인 관리 화면에서 부른다. 삭제되지 않은 모든 어드민(MASTER와 나 자신 포함)을 준다. status로 한 상태만 고를 수 있다.
                    정렬은 가입 신청 일시가 빠른 순(같으면 id 순)이다. 한 번에 size개(기본 20, 최대 50)를 주고, 더 있으면 nextCursor를 준다.
                    다음 페이지는 같은 status에 cursor = nextCursor를 붙여 부른다. status를 바꾸면 cursor 없이 처음부터 부른다.
                    맞는 어드민이 없으면 items는 빈 배열이고 nextCursor는 null이다.
                    승인·거절·이용 중지·해제 뒤(성공이든 실패든) 이 목록을 다시 불러 그린다. 조회만 하므로 같은 요청을 몇 번 다시 보내도 된다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "어드민 한 페이지. nextCursor가 null이면 마지막 페이지다"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = """
                            INVALID_REQUEST: status가 PENDING·APPROVED·REJECTED·SUSPENDED가 아니거나 size가 숫자가 아니다. 정상 화면 흐름에서는 나지 않는다.
                            INVALID_CURSOR: 해석할 수 없는 cursor. cursor 없이 처음부터 다시 부른다"""),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = UNAUTHENTICATED_401),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = MASTER_ONLY_403)})
    public ResponseEntity<ApiResponse<AdminListResponse>> list(
            @Parameter(description = """
                    이 상태의 어드민만 준다. PENDING(승인 대기), APPROVED(승인됨), REJECTED(거절됨), SUSPENDED(이용 중지).
                    생략하면 모든 상태다. 그 밖의 값은 400 INVALID_REQUEST""", example = "PENDING")
            @RequestParam(required = false) AdminStatus status,
            @Parameter(description = "이전 응답의 nextCursor를 그대로 보낸다. 생략하면 첫 페이지다. 해석할 수 없으면 400 INVALID_CURSOR")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "한 페이지 개수. 생략하면 20, 50보다 크면 50, 1보다 작으면 1로 맞춘다", example = "20")
            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(
                ApiResponse.success(AdminListResponse.from(adminQueryService.list(status, cursor, size))));
    }

    @PostMapping("/{adminId}/approval")
    @Operation(summary = "가입 승인 (MASTER 전용)",
            description = """
                    승인 대기(PENDING) 어드민의 가입을 승인한다. 목록의 [승인]에서 부른다. 요청 본문은 없다.
                    성공하면 200이고 data는 없다. 승인된 어드민은 그때부터 로그인할 수 있다. 성공이든 실패든 목록을 다시 불러 그린다.
                    두 번 눌려 같은 요청이 두 번 와도 승인은 한 번이다. 두 번째 요청은 409 ADMIN_STATUS_CONFLICT이니 응답이 올 때까지 버튼을 막는다.
                    여러 조건에 걸리면 ADMIN_NOT_FOUND → MASTER_NOT_MODIFIABLE → ADMIN_STATUS_CONFLICT 순서로 앞의 것이 온다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "승인 완료. data는 없다"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = INVALID_ID_400),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = UNAUTHENTICATED_401),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = TARGET_403),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = NOT_FOUND_404),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = CONFLICT_409 + " 승인은 PENDING일 때만 된다")})
    public ResponseEntity<ApiResponse<Void>> approve(@PathVariable UUID adminId) {
        adminCommandService.approve(adminId);

        return ResponseEntity.ok(ApiResponse.success());
    }

    @PostMapping("/{adminId}/rejection")
    @Operation(summary = "가입 거절 (MASTER 전용)",
            description = """
                    승인 대기(PENDING) 어드민의 가입을 거절한다. 목록의 [거절]에서 부른다. 요청 본문은 없다.
                    성공하면 200이고 data는 없다. 성공이든 실패든 목록을 다시 불러 그린다.
                    거절은 되돌릴 수 없다. 거절된 아이디는 다시 쓸 수 없어, 같은 사람이 다시 신청하려면 다른 아이디로 가입한다.
                    거절된 사람이 로그인하면 비밀번호가 맞을 때만 403 ADMIN_REJECTED를 받는다.
                    두 번 눌려 같은 요청이 두 번 와도 거절은 한 번이다. 두 번째 요청은 409 ADMIN_STATUS_CONFLICT이니 응답이 올 때까지 버튼을 막는다.
                    여러 조건에 걸리면 ADMIN_NOT_FOUND → MASTER_NOT_MODIFIABLE → ADMIN_STATUS_CONFLICT 순서로 앞의 것이 온다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "거절 완료. data는 없다"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = INVALID_ID_400),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = UNAUTHENTICATED_401),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = TARGET_403),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = NOT_FOUND_404),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = CONFLICT_409 + " 거절은 PENDING일 때만 된다")})
    public ResponseEntity<ApiResponse<Void>> reject(@PathVariable UUID adminId) {
        adminCommandService.reject(adminId);

        return ResponseEntity.ok(ApiResponse.success());
    }

    @PostMapping("/{adminId}/suspension")
    @Operation(summary = "이용 중지 (MASTER 전용)",
            description = """
                    승인된(APPROVED) 어드민의 이용을 중지한다. 목록의 [이용 중지]에서 부른다. 요청 본문은 없다.
                    성공하면 200이고 data는 없다. 성공이든 실패든 목록을 다시 불러 그린다.
                    중지된 어드민은 다음 요청부터 401 UNAUTHENTICATED이고, 그 어드민의 로그인 세션을 모두 지워 재발급도 되지 않는다.
                    중지된 사람이 로그인하면 비밀번호가 맞을 때만 403 ADMIN_SUSPENDED를 받는다. 해제(같은 경로의 DELETE)해도 다시 로그인해야 한다.
                    500이 나면 중지는 반영됐을 수 있다. 목록을 다시 불러 상태를 확인한다.
                    두 번 눌려 같은 요청이 두 번 와도 중지는 한 번이다. 두 번째 요청은 409 ADMIN_STATUS_CONFLICT이니 응답이 올 때까지 버튼을 막는다.
                    여러 조건에 걸리면 ADMIN_NOT_FOUND → MASTER_NOT_MODIFIABLE → ADMIN_STATUS_CONFLICT 순서로 앞의 것이 온다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "이용 중지 완료. data는 없다"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = INVALID_ID_400),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = UNAUTHENTICATED_401),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = TARGET_403),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = NOT_FOUND_404),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = CONFLICT_409 + " 이용 중지는 APPROVED일 때만 된다")})
    public ResponseEntity<ApiResponse<Void>> suspend(@PathVariable UUID adminId) {
        suspendAdminFacade.suspend(adminId);

        return ResponseEntity.ok(ApiResponse.success());
    }

    @DeleteMapping("/{adminId}/suspension")
    @Operation(summary = "이용 중지 해제 (MASTER 전용)",
            description = """
                    이용 중지(SUSPENDED)된 어드민을 다시 승인 상태(APPROVED)로 돌린다. 목록의 [이용 중지 해제]에서 부른다. 요청 본문은 없다.
                    성공하면 200이고 data는 없다. 성공이든 실패든 목록을 다시 불러 그린다.
                    중지할 때 세션을 모두 지웠으므로 해제된 어드민은 다시 로그인한다.
                    두 번 눌려 같은 요청이 두 번 와도 해제는 한 번이다. 두 번째 요청은 409 ADMIN_STATUS_CONFLICT이니 응답이 올 때까지 버튼을 막는다.
                    여러 조건에 걸리면 ADMIN_NOT_FOUND → MASTER_NOT_MODIFIABLE → ADMIN_STATUS_CONFLICT 순서로 앞의 것이 온다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "이용 중지 해제 완료. data는 없다"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = INVALID_ID_400),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = UNAUTHENTICATED_401),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = TARGET_403),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = NOT_FOUND_404),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = CONFLICT_409 + " 해제는 SUSPENDED일 때만 된다")})
    public ResponseEntity<ApiResponse<Void>> unsuspend(@PathVariable UUID adminId) {
        adminCommandService.unsuspend(adminId);

        return ResponseEntity.ok(ApiResponse.success());
    }
}
