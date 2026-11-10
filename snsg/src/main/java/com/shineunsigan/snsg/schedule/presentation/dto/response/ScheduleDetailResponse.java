package com.shineunsigan.snsg.schedule.presentation.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.shineunsigan.snsg.schedule.application.dto.result.ParticipantSummaryResult;
import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleAgeGroupResult;
import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleDetailQueryResult;
import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleDetailResult;
import com.shineunsigan.snsg.schedule.application.dto.result.ScheduleHostResult;
import com.shineunsigan.snsg.schedule.domain.constants.AgePhase;
import com.shineunsigan.snsg.schedule.domain.constants.DisplayStatus;
import com.shineunsigan.snsg.schedule.domain.constants.DurationType;
import com.shineunsigan.snsg.schedule.domain.constants.ScheduleAction;
import com.shineunsigan.snsg.schedule.domain.constants.ViewerRole;

import io.swagger.v3.oas.annotations.media.Schema;

public record ScheduleDetailResponse(

        @Schema(description = "일정 ID", example = "01a1c3d4-0000-7000-8000-000000000001")
        UUID id,

        @Schema(description = "활동명(일정 제목)", example = "보드게임 같이 해요")
        String title,

        @Schema(description = "간략 설명. 줄바꿈이 들어 있을 수 있다", example = "처음 오시는 분도 환영해요. 룰은 같이 배우면서 해요.")
        String description,

        @Schema(description = "시작 일시. 한국 시간, 오프셋 없는 ISO-8601", example = "2026-11-14T19:30:00")
        LocalDateTime startAt,

        @Schema(description = "개설 일시. 한국 시간, 오프셋 없는 ISO-8601", example = "2026-11-10T09:12:34.567")
        LocalDateTime createdAt,

        @Schema(description = "예상 소요 시간. SHORT 1시간 내외(60분), NORMAL 2시간 내외(120분), LONG 3시간 이상(180분)",
                example = "NORMAL")
        DurationType durationType,

        @Schema(description = "활동")
        Category category,

        @Schema(description = "개설자")
        Host host,

        @Schema(description = "확정 인원. 개설자를 포함한다", example = "3")
        int confirmedCount,

        @Schema(description = "정원. 개설자를 포함한다", example = "6")
        int capacity,

        @Schema(description = "남은 자리 = capacity − confirmedCount. 0이면 정원이 찼다", example = "3")
        int remainingSeats,

        @Schema(description = "확정 참여자(개설자 포함) 요약")
        ParticipantSummary participantSummary,

        @Schema(description = "장소. 개설 시점의 값이다. 장소 정보가 나중에 바뀌어도 이 일정에는 개설 때 값이 그대로 온다")
        Place place,

        @Schema(description = """
                화면에 보여줄 상태. 서버가 일정 상태·모집 상태·시각을 합쳐 정한다. FE는 이 값만 보고 배지와 비활성 버튼 문구를 정한다.
                RECRUITING: 모집 중. RECRUITMENT_CLOSED: 모집 마감(정원이 찼거나 시작 시각이 지났다).
                COMPLETED: 완료(시작 + 12시간이 지났다). CANCELED: 일정 취소됨(개설자가 취소했다)""",
                example = "RECRUITING")
        DisplayStatus displayStatus,

        @Schema(description = """
                보는 사람의 역할. GUEST: 로그인하지 않았다(토큰이 없거나 틀렸다). HOST: 일정을 연 사람.
                PARTICIPANT: 멤버로 참여 확정했다. NON_PARTICIPANT: 로그인했지만 참여하지 않았다(참여를 취소한 사람 포함)""",
                example = "NON_PARTICIPANT")
        ViewerRole viewerRole,

        @Schema(description = """
                지금 누를 수 있는 동작. 들어 있는 동작의 버튼만 활성으로 그린다. 비어 있으면 누를 수 있는 버튼이 없다.
                LOGIN_TO_JOIN: [참여하기]를 누르면 로그인 화면으로 보낸다(GUEST, 모집 중).
                JOIN: [참여하기](NON_PARTICIPANT, 모집 중). CANCEL_PARTICIPATION: [참여 취소](PARTICIPANT, 모집 중·모집 마감).
                CANCEL_SCHEDULE: [일정 취소](HOST, 취소·완료되지 않았고 시작 전)""",
                example = "[\"JOIN\"]")
        List<ScheduleAction> availableActions,

        @Schema(description = """
                일정 취소 정보. 개설자가 일정을 취소했으면(displayStatus = CANCELED) 온다. 보는 사람의 역할과 관계없이(GUEST 포함) 같다.
                취소되지 않은 일정이면 null""", nullable = true)
        Cancellation cancellation) {

    public static ScheduleDetailResponse from(ScheduleDetailResult result) {
        ScheduleDetailQueryResult schedule = result.schedule();
        return new ScheduleDetailResponse(schedule.id(), schedule.title(), schedule.description(),
                schedule.startAt(), schedule.createdAt(), schedule.durationType(),
                new Category(schedule.categoryId(), schedule.categoryName()), Host.from(result.host()),
                schedule.confirmedCount(), schedule.capacity(), schedule.remainingSeats(),
                ParticipantSummary.from(result.participantSummary()),
                Place.from(schedule.place(), result.regionName()), schedule.displayStatus(), schedule.viewerRole(),
                schedule.availableActions(), Cancellation.from(schedule.cancellation()));
    }

    @Schema(name = "ScheduleDetailCancellation")
    public record Cancellation(

            @Schema(description = "개설자가 적은 취소 사유. 1~100자이고 줄바꿈이 들어 있을 수 있다",
                    example = "개인 사정으로 일정을 취소하게 됐어요. 죄송해요.")
            String reason,

            @Schema(description = "취소 일시. 한국 시간, 오프셋 없는 ISO-8601", example = "2026-11-12T08:30:12.345")
            LocalDateTime canceledAt) {

        static Cancellation from(ScheduleDetailQueryResult.Cancellation cancellation) {
            return cancellation == null ? null : new Cancellation(cancellation.reason(), cancellation.canceledAt());
        }
    }

    @Schema(name = "ScheduleDetailCategory")
    public record Category(

            @Schema(description = "활동 ID", example = "01a19cc6-3d80-7000-8000-000000000001")
            UUID id,

            @Schema(description = "활동 이름. 지금은 고를 수 없는(숨긴) 활동이어도 이름이 온다. 활동 정보를 찾을 수 없으면 빈 문자열",
                    example = "보드게임")
            String name) {
    }

    @Schema(name = "ScheduleDetailHost")
    public record Host(

            @Schema(description = "닉네임. 탈퇴했으면 \"탈퇴한 사용자\"", example = "민트초코")
            String nickname,

            @Schema(description = "프로필 사진 경로. 도메인 없이 경로만 온다. 탈퇴했으면 기본 이미지 경로",
                    example = "/images/profiles/0a1b2c3d-4e5f-6071-8293-a4b5c6d7e8f9.png")
            String profileImageUrl,

            @Schema(description = "탈퇴한 개설자면 true. 화면에 탈퇴 표시를 붙일 때 쓴다", example = "false")
            boolean withdrawn) {

        static Host from(ScheduleHostResult host) {
            return new Host(host.nickname(), host.profileImageUrl(), host.withdrawn());
        }
    }

    @Schema(name = "ScheduleDetailParticipantSummary")
    public record ParticipantSummary(

            @Schema(description = "확정 참여자 수. 개설자를 포함한다", example = "3")
            int count,

            @Schema(description = "남자 수. 탈퇴한 참여자도 센다. 성별을 알 수 없는 참여자는 남자·여자 어느 쪽에도 세지 않는다",
                    example = "1")
            int maleCount,

            @Schema(description = "여자 수. 세는 기준은 maleCount와 같다", example = "2")
            int femaleCount,

            @Schema(description = "가장 낮은 연령대. 탈퇴한 참여자는 빼고 고른다. 연령대를 아는 참여자가 없으면(전원 탈퇴) null",
                    nullable = true)
            AgeGroup minAgeGroup,

            @Schema(description = "가장 높은 연령대. 고르는 기준은 minAgeGroup과 같다. 연령대를 아는 참여자가 없으면 null",
                    nullable = true)
            AgeGroup maxAgeGroup) {

        static ParticipantSummary from(ParticipantSummaryResult summary) {
            return new ParticipantSummary(summary.count(), summary.maleCount(), summary.femaleCount(),
                    AgeGroup.from(summary.minAgeGroup()), AgeGroup.from(summary.maxAgeGroup()));
        }
    }

    @Schema(name = "ScheduleDetailAgeGroup", description = "연령대. 십의 자리를 먼저 비교하고, 같으면 초반 < 중반 < 후반이다")
    public record AgeGroup(

            @Schema(description = "십의 자리 × 10. 20이면 20대다. 만 18·19세는 10이다", example = "20")
            int decade,

            @Schema(description = "EARLY 초반(일의 자리 0~3), MID 중반(4~6), LATE 후반(7~9). 18·19세는 10대 후반(LATE)이다",
                    example = "LATE")
            AgePhase phase) {

        static AgeGroup from(ScheduleAgeGroupResult ageGroup) {
            return ageGroup == null ? null : new AgeGroup(ageGroup.decade(), ageGroup.phase());
        }
    }

    @Schema(name = "ScheduleDetailPlace")
    public record Place(

            @Schema(description = "장소 이름", example = "레드버튼 홍대점")
            String name,

            @Schema(description = "도로명 주소", example = "서울특별시 마포구 와우산로 1")
            String roadAddress,

            @Schema(description = "상세주소. 없으면 빈 문자열", example = "2층")
            String addressDetail,

            @Schema(description = "위도(WGS84)", example = "37.5500000")
            BigDecimal latitude,

            @Schema(description = "경도(WGS84)", example = "126.9200000")
            BigDecimal longitude,

            @Schema(description = "지역 이름(시군구). 지역 정보를 찾을 수 없으면 빈 문자열이고, 그때는 지역을 표시하지 않는다",
                    example = "마포구")
            String regionName) {

        static Place from(ScheduleDetailQueryResult.Place place, String regionName) {
            return new Place(place.name(), place.roadAddress(), place.addressDetail(), place.latitude(),
                    place.longitude(), regionName);
        }
    }
}
