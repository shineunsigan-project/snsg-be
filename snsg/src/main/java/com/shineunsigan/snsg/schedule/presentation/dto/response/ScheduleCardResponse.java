package com.shineunsigan.snsg.schedule.presentation.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleCardResult;
import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleListItemResult;
import com.shineunsigan.snsg.schedule.domain.constants.DisplayStatus;

import io.swagger.v3.oas.annotations.media.Schema;

public record ScheduleCardResponse(

        @Schema(description = "일정 ID. 일정 상세로 갈 때 쓴다", example = "01a1c3d4-0000-7000-8000-000000000001")
        UUID id,

        @Schema(description = "활동")
        Category category,

        @Schema(description = "활동명(일정 제목)", example = "보드게임 같이 해요")
        String title,

        @Schema(description = "지역 이름(시군구). 지역 정보를 찾을 수 없으면 빈 문자열이고, 그때는 지역을 표시하지 않는다",
                example = "마포구")
        String regionName,

        @Schema(description = "시작 일시. 한국 시간, 오프셋 없는 ISO-8601", example = "2026-11-14T19:30:00")
        LocalDateTime startAt,

        @Schema(description = "확정 인원. 개설자를 포함한다", example = "3")
        int confirmedCount,

        @Schema(description = "정원. 개설자를 포함한다", example = "6")
        int capacity,

        @Schema(description = "남은 자리 = capacity − confirmedCount. 0이면 정원이 찼다", example = "3")
        int remainingSeats,

        @Schema(description = """
                화면에 보여줄 상태. 서버가 일정 상태·모집 상태·시각을 합쳐 정한다. FE는 이 값만 보고 배지를 그린다.
                RECRUITING: 모집 중. RECRUITMENT_CLOSED: 모집 마감(정원이 찼거나 시작 시각이 지났다).
                CANCELED: 일정 취소됨(개설자가 취소했다. 참여한·개설한 목록에만 나온다).
                COMPLETED: 완료. 목록에는 나오지 않는다""",
                example = "RECRUITING")
        DisplayStatus displayStatus,

        @Schema(description = "개설 일시. 한국 시간, 오프셋 없는 ISO-8601. NEW 배지를 붙일지 정할 때 쓴다",
                example = "2026-11-10T09:12:34.567")
        LocalDateTime createdAt) {

    public static ScheduleCardResponse from(ScheduleListItemResult item) {
        ScheduleCardResult card = item.card();
        return new ScheduleCardResponse(card.id(), new Category(card.categoryId(), card.categoryName()), card.title(),
                item.regionName(), card.startAt(), card.confirmedCount(), card.capacity(), card.remainingSeats(),
                card.displayStatus(), card.createdAt());
    }

    @Schema(name = "ScheduleCardCategory")
    public record Category(

            @Schema(description = "활동 ID", example = "01a19cc6-3d80-7000-8000-000000000001")
            UUID id,

            @Schema(description = "활동 이름. 지금은 고를 수 없는(숨긴) 활동이어도 이름이 온다. 활동 정보를 찾을 수 없으면 빈 문자열",
                    example = "보드게임")
            String name) {
    }
}
