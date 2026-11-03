package com.shineunsigan.snsg.schedule.presentation;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shineunsigan.snsg.common.web.ApiResponse;
import com.shineunsigan.snsg.schedule.application.ActivityCategoryQueryService;
import com.shineunsigan.snsg.schedule.presentation.dto.response.ActivityCategoryResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

@Tag(name = "활동")
@RestController
@RequestMapping("/api/v1/activity-categories")
@RequiredArgsConstructor
public class ActivityCategoryController {

    private final ActivityCategoryQueryService activityCategoryQueryService;

    @SecurityRequirements
    @GetMapping
    @Operation(summary = "활동 목록",
            description = """
                    고를 수 있는 활동(보드게임·방탈출·산책 등) 목록. 일정 개설의 활동 선택과 일정 목록의 활동 필터를 그릴 때 부른다.
                    토큰 없이 부를 수 있다. 숨긴(비활성) 활동은 빠지고, 서버가 정한 노출 순서(display_order)대로 온다.
                    조회만 하므로 같은 요청을 몇 번 다시 보내도 된다.""")
    @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
            description = "노출 순서대로 정렬된 활동 목록"))
    public ResponseEntity<ApiResponse<List<ActivityCategoryResponse>>> findActivityCategories() {
        return ResponseEntity.ok(ApiResponse.success(activityCategoryQueryService.findActive().stream()
                .map(ActivityCategoryResponse::from)
                .toList()));
    }
}
