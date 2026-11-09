package com.shineunsigan.snsg.schedule.application.dto.result;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.shineunsigan.snsg.schedule.domain.constants.DisplayStatus;
import com.shineunsigan.snsg.schedule.domain.constants.DurationType;
import com.shineunsigan.snsg.schedule.domain.constants.ScheduleAction;
import com.shineunsigan.snsg.schedule.domain.constants.ViewerRole;
import com.shineunsigan.snsg.schedule.domain.model.PlaceSnapshot;
import com.shineunsigan.snsg.schedule.domain.model.Schedule;

/**
 * schedule이 가진 데이터만으로 만든 일정 상세. 개설자 프로필·참여자 요약·지역 이름은 facade가 채운다.
 *
 * @param categoryName     활동 이름. 비활성 활동이어도 이름이다. 활동이 없거나 삭제됐으면 빈 문자열
 * @param confirmedUserIds 확정 참여자(개설자 포함)의 사용자 id
 */
public record ScheduleDetailQueryResult(UUID id, String title, String description, LocalDateTime startAt,
        LocalDateTime createdAt, DurationType durationType, UUID categoryId, String categoryName, UUID hostUserId,
        List<UUID> confirmedUserIds, int confirmedCount, int capacity, int remainingSeats, Place place,
        DisplayStatus displayStatus, ViewerRole viewerRole, List<ScheduleAction> availableActions) {

    public static ScheduleDetailQueryResult of(Schedule schedule, String categoryName, DisplayStatus displayStatus,
            ViewerRole viewerRole, List<ScheduleAction> availableActions) {
        return new ScheduleDetailQueryResult(schedule.getId(), schedule.getTitle(), schedule.getDescription(),
                schedule.getStartAt(), schedule.getCreatedAt(), schedule.getDurationType(), schedule.getCategoryId(),
                categoryName, schedule.getHostUserId(), schedule.confirmedUserIds(), schedule.getConfirmedCount(),
                schedule.getCapacity(), schedule.getCapacity() - schedule.getConfirmedCount(),
                Place.from(schedule.getPlace()), displayStatus, viewerRole, availableActions);
    }

    /** 개설 시점의 장소 사본. 상세주소가 없으면 빈 문자열이다. */
    public record Place(String name, String roadAddress, String addressDetail, BigDecimal latitude,
            BigDecimal longitude, String regionCode) {

        static Place from(PlaceSnapshot place) {
            return new Place(place.getPlaceName(), place.getPlaceRoadAddress(), place.getPlaceAddressDetail(),
                    place.getPlaceLatitude(), place.getPlaceLongitude(), place.getRegionCode());
        }
    }
}
