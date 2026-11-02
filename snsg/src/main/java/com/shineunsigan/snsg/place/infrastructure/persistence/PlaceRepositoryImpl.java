package com.shineunsigan.snsg.place.infrastructure.persistence;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import com.shineunsigan.snsg.place.domain.constants.PlaceSource;
import com.shineunsigan.snsg.place.domain.constants.PlaceType;
import com.shineunsigan.snsg.place.domain.model.Place;
import com.shineunsigan.snsg.place.domain.repository.PlaceRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class PlaceRepositoryImpl implements PlaceRepository {

    private final PlaceJpaRepository placeJpaRepository;

    @Override
    public Place save(Place place) {
        return placeJpaRepository.save(place);
    }

    @Override
    public Optional<Place> findExposed(UUID id) {
        return placeJpaRepository.findExposed(id);
    }

    @Override
    public List<Place> findAllExposed(PlaceType placeType, Collection<String> regionCodes) {
        if (regionCodes.isEmpty()) {
            return placeJpaRepository.findAllExposed(placeType);
        }
        return placeJpaRepository.findAllExposedInRegions(placeType, regionCodes);
    }

    @Override
    public List<Place> findAllBySourceIds(Collection<String> sourceIds) {
        return placeJpaRepository.findAllBySourceIds(sourceIds);
    }

    @Override
    public List<Place> findAllCloseTargets(PlaceSource source, Collection<String> regionCodes, LocalDateTime before) {
        return placeJpaRepository.findAllCloseTargets(source, regionCodes, before);
    }

    @Override
    public Map<String, Long> countOpenBySourceRegion(PlaceSource source, Collection<String> regionCodes) {
        return placeJpaRepository.countOpenBySourceRegion(source, regionCodes).stream()
                .collect(Collectors.toMap(row -> (String) row[0], row -> (Long) row[1]));
    }
}
