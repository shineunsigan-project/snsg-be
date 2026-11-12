package com.shineunsigan.snsg.place.infrastructure.persistence;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import com.shineunsigan.snsg.common.entity.Auditor;
import com.shineunsigan.snsg.place.domain.constants.PlaceSource;
import com.shineunsigan.snsg.place.domain.constants.PlaceType;
import com.shineunsigan.snsg.place.domain.model.Place;
import com.shineunsigan.snsg.place.domain.model.PlaceCursor;
import com.shineunsigan.snsg.place.domain.model.PlaceSearchCondition;
import com.shineunsigan.snsg.place.domain.repository.PlaceRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class PlaceRepositoryImpl implements PlaceRepository {

    private final PlaceJpaRepository placeJpaRepository;
    private final EntityManager entityManager;

    @Override
    public Place save(Place place) {
        return placeJpaRepository.save(place);
    }

    @Override
    public Place saveAndFlush(Place place) {
        return placeJpaRepository.saveAndFlush(place);
    }

    @Override
    public Optional<Place> findById(UUID id) {
        return placeJpaRepository.findNotDeletedById(id);
    }

    @Override
    public Optional<Place> findByIdForUpdate(UUID id) {
        return placeJpaRepository.findByIdForUpdate(id);
    }

    /**
     * 조건이 모두 선택이라 JPQL을 조건이 있을 때만 이어 붙이고 값은 모두 바인딩한다. keyset 조건과 정렬은 DB에서 한다.
     * Java의 UUID 비교는 부호 있는 비교라 PostgreSQL uuid 순서와 다르다.
     */
    @Override
    public List<Place> findAllForReview(PlaceSearchCondition condition, PlaceCursor after, int limit) {
        StringBuilder jpql = new StringBuilder("select p from Place p where p.isDeleted = false");
        Map<String, Object> parameters = new HashMap<>();
        appendCondition(jpql, parameters, " and p.regionCode = :regionCode", "regionCode", condition.regionCode());
        appendCondition(jpql, parameters, " and p.status = :status", "status", condition.status());
        appendCondition(jpql, parameters, " and p.placeType = :placeType", "placeType", condition.placeType());
        appendCondition(jpql, parameters, " and p.source = :source", "source", condition.source());
        appendCondition(jpql, parameters, " and p.isClosed = :closed", "closed", condition.closed());
        if (condition.keyword() != null) {
            jpql.append(" and (lower(p.name) like :keyword escape '\\'"
                    + " or lower(p.roadAddress) like :keyword escape '\\'"
                    + " or lower(p.sourceName) like :keyword escape '\\'"
                    + " or lower(p.sourceRoadAddress) like :keyword escape '\\')");
            parameters.put("keyword", containsPattern(condition.keyword()));
        }
        if (after != null) {
            jpql.append(" and (p.createdAt > :cursorCreatedAt"
                    + " or (p.createdAt = :cursorCreatedAt and p.id > :cursorId))");
            parameters.put("cursorCreatedAt", after.createdAt());
            parameters.put("cursorId", after.id());
        }
        jpql.append(" order by p.createdAt, p.id");
        TypedQuery<Place> query = entityManager.createQuery(jpql.toString(), Place.class);
        parameters.forEach(query::setParameter);
        return query.setMaxResults(limit).getResultList();
    }

    private static void appendCondition(StringBuilder jpql, Map<String, Object> parameters, String fragment,
            String name, Object value) {
        if (value != null) {
            jpql.append(fragment);
            parameters.put(name, value);
        }
    }

    /** 소문자로 바꾸고 LIKE 특수문자({@code \ % _})를 이스케이프해 부분 일치 패턴으로 만든다. */
    private static String containsPattern(String keyword) {
        String escaped = keyword.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
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

    @Override
    public void markSynced(PlaceSource source, Collection<String> sourceIds, LocalDateTime syncedAt) {
        if (sourceIds.isEmpty()) {
            return;
        }
        placeJpaRepository.markSynced(source, sourceIds, syncedAt, Auditor.SYSTEM_SCHEDULER);
    }
}
