package com.shineunsigan.snsg.place.application;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shineunsigan.snsg.place.application.dto.result.PlaceResult;
import com.shineunsigan.snsg.place.application.port.in.FindPlaceUseCase;
import com.shineunsigan.snsg.place.domain.constants.PlaceType;
import com.shineunsigan.snsg.place.domain.repository.PlaceRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceQueryService implements FindPlaceUseCase {

    private final PlaceRepository placeRepository;

    @Override
    public Optional<PlaceResult> find(UUID placeId) {
        return placeRepository.findExposed(placeId).map(PlaceResult::from);
    }

    @Override
    public List<PlaceResult> findExposed(PlaceType placeType, Collection<String> regionCodes) {
        return placeRepository.findAllExposed(placeType, regionCodes).stream()
                .map(PlaceResult::from)
                .toList();
    }
}
