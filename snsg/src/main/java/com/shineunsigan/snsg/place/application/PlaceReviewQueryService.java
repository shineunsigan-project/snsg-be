package com.shineunsigan.snsg.place.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.place.application.dto.command.ListPlacesCommand;
import com.shineunsigan.snsg.place.application.dto.result.PlaceReviewPageResult;
import com.shineunsigan.snsg.place.application.dto.result.PlaceReviewResult;
import com.shineunsigan.snsg.place.domain.exception.PlaceErrorCode;
import com.shineunsigan.snsg.place.domain.model.Place;
import com.shineunsigan.snsg.place.domain.model.PlaceCursor;
import com.shineunsigan.snsg.place.domain.model.PlaceSearchCondition;
import com.shineunsigan.snsg.place.domain.repository.PlaceRepository;

import lombok.RequiredArgsConstructor;

/** 어드민 장소 목록·상세. 노출 여부와 상관없이 삭제되지 않은 모든 장소를 본다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceReviewQueryService {

    private final PlaceRepository placeRepository;
    private final PlaceProperties placeProperties;

    /**
     * 조건에 맞는 장소를 생성 시각, id 순으로 준다. 한 페이지보다 한 건 더 읽어 다음 페이지가 있는지 정한다.
     * 해석할 수 없는 커서는 INVALID_CURSOR. size는 생략하면 기본 개수, 최대를 넘으면 최대, 1보다 작으면 1이다.
     */
    public PlaceReviewPageResult list(ListPlacesCommand command) {
        PlaceCursor after = command.cursor() == null || command.cursor().isEmpty()
                ? null : PlaceCursor.decode(command.cursor());
        PlaceSearchCondition condition = PlaceSearchCondition.of(command.regionCode(), command.status(),
                command.placeType(), command.source(), command.closed(), command.keyword());
        int pageSize = pageSize(command.size());
        List<Place> places = placeRepository.findAllForReview(condition, after, pageSize + 1);
        boolean hasNext = places.size() > pageSize;
        List<Place> page = hasNext ? places.subList(0, pageSize) : places;
        return new PlaceReviewPageResult(page.stream().map(PlaceReviewResult::from).toList(),
                hasNext ? nextCursor(page.get(pageSize - 1)) : null);
    }

    public PlaceReviewResult get(UUID placeId) {
        return placeRepository.findById(placeId)
                .map(PlaceReviewResult::from)
                .orElseThrow(() -> new BusinessException(PlaceErrorCode.PLACE_NOT_FOUND));
    }

    private int pageSize(Integer requested) {
        int size = requested == null ? placeProperties.pageSizeDefault() : requested;
        return Math.max(1, Math.min(size, placeProperties.pageSizeMax()));
    }

    private static String nextCursor(Place last) {
        return new PlaceCursor(last.getCreatedAt(), last.getId()).encode();
    }
}
