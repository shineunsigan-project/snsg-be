package com.shineunsigan.snsg.place.presentation;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shineunsigan.snsg.common.web.ApiResponse;
import com.shineunsigan.snsg.place.application.RegionQueryService;
import com.shineunsigan.snsg.place.presentation.dto.response.AdminRegionResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 어드민이 쓰는 지역 전체. 어드민 앱 출처는 CORS가 {@code /api/admin/**}만 허용해 사용자 지역 목록을 부를 수 없어 따로 둔다.
 */
@Tag(name = "어드민 지역", description = "장소 등록·수정과 목록 필터에 쓰는 지역. 승인된 어드민(ADMIN·MASTER) 누구나 쓴다")
@RestController
@RequestMapping("/api/admin/v1/regions")
@RequiredArgsConstructor
public class AdminRegionController {

    private final RegionQueryService regionQueryService;

    @GetMapping
    @Operation(summary = "지역 전체",
            description = """
                    서비스하지 않는(비활성) 지역까지 모든 지역을 노출 순서(display_order)대로 준다. isActive로 구분한다.
                    장소 목록의 지역 필터, 장소 수정·등록의 지역 선택을 그릴 때 부른다. 장소의 regionCode를 지역 이름으로 바꿀 때도 쓴다.
                    조회만 하므로 같은 요청을 몇 번 다시 보내도 된다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "노출 순서대로 정렬된 모든 지역"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "UNAUTHENTICATED: 토큰이 없거나 만료·무효이거나, 어드민이 승인 상태가 아니다. "
                            + "재발급 후 다시 부르고, 재발급도 401이면 로그인 화면으로 간다")})
    public ResponseEntity<ApiResponse<List<AdminRegionResponse>>> findRegions() {
        return ResponseEntity.ok(ApiResponse.success(regionQueryService.findAll().stream()
                .map(AdminRegionResponse::from)
                .toList()));
    }
}
