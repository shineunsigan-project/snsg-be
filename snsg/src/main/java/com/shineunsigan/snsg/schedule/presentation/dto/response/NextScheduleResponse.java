package com.shineunsigan.snsg.schedule.presentation.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.shineunsigan.snsg.schedule.application.dto.result.NextScheduleResult;
import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleCardResult;
import com.shineunsigan.snsg.schedule.domain.constants.DisplayStatus;
import com.shineunsigan.snsg.schedule.domain.constants.DurationType;

import io.swagger.v3.oas.annotations.media.Schema;

/** 목록 카드와 같은 필드에 장소 이름·소요 시간·참여자 사진을 더한다. */
public record NextScheduleResponse(

        @Schema(description = "일정 ID. 배너를 누르면 이 값으로 일정 상세로 간다", example = "01a1c3d4-0000-7000-8000-000000000001")
        UUID id,

        @Schema(description = "활동")
        ScheduleCardResponse.Category category,

        @Schema(description = "활동명(일정 제목)", example = "보드게임 같이 해요")
        String title,

        @Schema(description = "지역 이름(시군구). 지역 정보를 찾을 수 없으면 빈 문자열이고, 그때는 지역을 표시하지 않는다",
                example = "마포구")
        String regionName,

        @Schema(description = "시작 일시. 한국 시간, 오프셋 없는 ISO-8601", example = "2026-11-14T19:30:00")
        LocalDateTime startAt,

        @Schema(description = "확정 인원. 개설자를 포함한다. 사진보다 많으면 \"+N\"을 이 값으로 계산한다", example = "4")
        int confirmedCount,

        @Schema(description = "정원. 개설자를 포함한다", example = "6")
        int capacity,

        @Schema(description = "남은 자리 = capacity − confirmedCount. 0이면 정원이 찼다", example = "2")
        int remainingSeats,

        @Schema(description = """
                화면에 보여줄 상태. 서버가 일정 상태·모집 상태·시각을 합쳐 정한다. FE는 이 값만 보고 배지를 그린다.
                이 API는 시작 전 일정만 주므로 두 값만 온다.
                RECRUITING: 모집 중. RECRUITMENT_CLOSED: 모집 마감(정원이 찼다)""",
                example = "RECRUITING")
        DisplayStatus displayStatus,

        @Schema(description = "개설 일시. 한국 시간, 오프셋 없는 ISO-8601", example = "2026-11-10T09:12:34.567")
        LocalDateTime createdAt,

        @Schema(description = "장소 이름. 개설 시점의 값이다", example = "보드게임카페 홍대점")
        String placeName,

        @Schema(description = "예상 소요 시간. SHORT 1시간 내외(60분), NORMAL 2시간 내외(120분), LONG 3시간 이상(180분)",
                example = "NORMAL")
        DurationType durationType,

        @Schema(description = """
                참여자 프로필 사진 경로. 도메인 없이 경로만 온다. 참여자 명단과 같은 순서(개설자 먼저 → 참여 일시 순)의 앞 3명이고,
                개설자가 첫 번째다. 나도 빼지 않는다. 확정 인원이 3명보다 적으면 그 수만큼 온다(개설자가 있어 최소 1장).
                탈퇴한 사람은 기본 이미지 경로가 온다""",
                example = "[\"/images/profiles/a.png\", \"/images/default-profile.png\", \"/images/profiles/c.png\"]")
        List<String> participantProfileImageUrls) {

    public static NextScheduleResponse from(NextScheduleResult result) {
        ScheduleCardResult card = result.item().card();
        return new NextScheduleResponse(card.id(),
                new ScheduleCardResponse.Category(card.categoryId(), card.categoryName()), card.title(),
                result.item().regionName(), card.startAt(), card.confirmedCount(), card.capacity(),
                card.remainingSeats(), card.displayStatus(), card.createdAt(), result.placeName(),
                result.durationType(), result.participantProfileImageUrls());
    }
}
