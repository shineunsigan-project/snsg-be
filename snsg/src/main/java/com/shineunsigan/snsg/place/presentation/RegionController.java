package com.shineunsigan.snsg.place.presentation;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shineunsigan.snsg.common.web.ApiResponse;
import com.shineunsigan.snsg.place.application.RegionQueryService;
import com.shineunsigan.snsg.place.presentation.dto.response.RegionResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

@Tag(name = "지역")
@RestController
@RequestMapping("/api/v1/regions")
@RequiredArgsConstructor
public class RegionController {

    private final RegionQueryService regionQueryService;

    @SecurityRequirements
    @GetMapping
    @Operation(summary = "지역 목록",
            description = """
                    서비스 중인 지역 목록. 일정 목록의 지역 필터와 장소 선택의 지역 칩을 그릴 때 부른다.
                    토큰 없이 부를 수 있다. 서비스하지 않는(비활성) 지역은 빠지고, 서버가 정한 노출 순서(display_order)대로 온다.
                    조회만 하므로 같은 요청을 몇 번 다시 보내도 된다.""")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
            description = "노출 순서대로 정렬된 지역 목록"))
    public ResponseEntity<ApiResponse<List<RegionResponse>>> findRegions() {
        return ResponseEntity.ok(ApiResponse.success(regionQueryService.findActive().stream()
                .map(RegionResponse::from)
                .toList()));
    }
}
