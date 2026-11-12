package com.shineunsigan.snsg.place.application;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.place.application.dto.command.CreateManualPlaceCommand;
import com.shineunsigan.snsg.place.application.dto.command.ReviewPlaceCommand;
import com.shineunsigan.snsg.place.application.dto.result.PlaceReviewResult;
import com.shineunsigan.snsg.place.domain.exception.PlaceErrorCode;
import com.shineunsigan.snsg.place.domain.model.Place;
import com.shineunsigan.snsg.place.domain.repository.PlaceRepository;
import com.shineunsigan.snsg.place.domain.repository.RegionRepository;

import lombok.RequiredArgsConstructor;

/**
 * 어드민의 장소 검수(수정)와 MANUAL 등록. 적재 반영은 {@link PlaceCommandService}가 맡는다.
 * <p>
 * 수정은 그 행을 잠가 처음 읽는다. 적재도 같은 행을 잠가 읽으므로, 둘이 겹치면 뒤에 온 쪽이 앞의 커밋을 기다렸다가
 * 그 결과 위에 자기 변경을 쓴다. 어드민끼리 겹치면 마지막 요청이 남는다.
 */
@Service
@RequiredArgsConstructor
public class PlaceReviewCommandService {

    private final PlaceRepository placeRepository;
    private final RegionRepository regionRepository;

    /**
     * 유형·검수 상태·화면 값을 모두 바꾼다. 원천 값·폐업·마지막 확인 시각은 그대로다.
     * 여러 조건에 걸리면 PLACE_NOT_FOUND → INVALID_REGION → 상태·유형·형식 오류 순서로 앞의 것이 난다.
     */
    @Transactional
    public PlaceReviewResult review(ReviewPlaceCommand command) {
        Place place = placeRepository.findByIdForUpdate(command.placeId())
                .orElseThrow(() -> new BusinessException(PlaceErrorCode.PLACE_NOT_FOUND));
        validateRegion(command.regionCode());
        place.review(command.placeType(), command.status(), command.name(), command.roadAddress(),
                command.addressDetail(), command.latitude(), command.longitude(), command.regionCode(),
                command.reviewerId().toString(), LocalDateTime.now());
        return PlaceReviewResult.from(placeRepository.saveAndFlush(place));
    }

    /** 원천에 없는 장소를 바로 승인 상태로 등록한다. */
    @Transactional
    public PlaceReviewResult createManual(CreateManualPlaceCommand command) {
        validateRegion(command.regionCode());
        Place place = Place.createManual(command.placeType(), command.name(), command.roadAddress(),
                command.addressDetail(), command.latitude(), command.longitude(), command.regionCode(),
                command.reviewerId().toString(), LocalDateTime.now());
        return PlaceReviewResult.from(placeRepository.save(place));
    }

    /** regions에 있는 코드여야 한다. 비활성 지역도 된다(노출만 되지 않는다). */
    private void validateRegion(String regionCode) {
        if (regionCode == null || regionRepository.findByCode(regionCode).isEmpty()) {
            throw new BusinessException(PlaceErrorCode.INVALID_REGION);
        }
    }
}
