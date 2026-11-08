package com.shineunsigan.snsg.schedule.presentation;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shineunsigan.snsg.common.web.ApiResponse;
import com.shineunsigan.snsg.schedule.application.ListPlaceCandidatesFacade;
import com.shineunsigan.snsg.schedule.presentation.dto.response.PlaceCandidateResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

@Tag(name = "장소 후보")
@RestController
@RequestMapping("/api/v1/place-candidates")
@RequiredArgsConstructor
public class PlaceCandidateController {

    private final ListPlaceCandidatesFacade listPlaceCandidatesFacade;

    /** regionCode는 생략(null)과 빈 값("")을 구분한다. 기본값을 주면 빈 값이 기본값으로 바뀌므로 주지 않는다. */
    @SecurityRequirements
    @GetMapping
    @Operation(summary = "장소 후보",
            description = """
                    활동이 고를 수 있는 유형의 장소 목록(보드게임 → 보드게임카페, 방탈출 → 방탈출 매장, 산책 → 공원).
                    장소 선택 화면에 들어올 때와 지역 칩을 바꿀 때 부른다. 목록과 지도 핀을 이 결과로 그리고, 고른 장소의 id를 일정 개설의 placeId로 보낸다.
                    토큰 없이 부를 수 있다. 검수를 통과하고 영업 중인, 서비스 중인 지역의 장소만 온다.
                    정렬은 지역 노출 순서(display_order), 그 안에서 장소명 순이다. 페이지를 나누지 않는다.
                    조회만 하므로 같은 요청을 몇 번 다시 보내도 된다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "정렬된 장소 후보 목록. 조건에 맞는 장소가 없으면 빈 배열이고 화면에 결과 없음을 보여준다"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = """
                            INVALID_REQUEST: categoryId가 없거나 UUID 형식이 아니다.
                            INVALID_CATEGORY: 없거나 숨긴 활동. 활동 목록을 다시 불러 고르게 한다. 지역도 틀렸어도 이 코드가 온다.
                            INVALID_REGION: 없거나 서비스하지 않는 지역, 빈 값으로 보낸 regionCode. 지역 목록을 다시 불러 고르게 한다""")})
    public ResponseEntity<ApiResponse<List<PlaceCandidateResponse>>> listPlaceCandidates(
            @Parameter(description = "활동 ID. 활동 목록 API의 id", example = "01a19cc6-3d80-7000-8000-000000000001")
            @RequestParam UUID categoryId,
            @Parameter(description = "지역 코드. 지역 목록 API의 code. 생략하면 서비스 중인 지역 전체다. 빈 값으로 보내지 않는다",
                    example = "11440")
            @RequestParam(required = false) String regionCode) {
        return ResponseEntity.ok(ApiResponse.success(listPlaceCandidatesFacade.list(categoryId, regionCode).stream()
                .map(PlaceCandidateResponse::from)
                .toList()));
    }
}
