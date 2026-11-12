package com.shineunsigan.snsg.place.infrastructure.persistence;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shineunsigan.snsg.place.domain.constants.PlaceSource;
import com.shineunsigan.snsg.place.domain.constants.PlaceType;
import com.shineunsigan.snsg.place.domain.model.Place;

interface PlaceJpaRepository extends JpaRepository<Place, UUID> {

    /** 노출 조건. 지역 활성 여부를 같은 쿼리에서 거르려고 regions와 조인한다. */
    String EXPOSED = """
            from Place p join Region r on r.code = p.regionCode
            where p.status = com.shineunsigan.snsg.place.domain.constants.PlaceStatus.APPROVED
              and p.isClosed = false
              and p.placeType <> com.shineunsigan.snsg.place.domain.constants.PlaceType.UNCLASSIFIED
              and p.isDeleted = false
              and r.isActive = true
            """;

    String EXPOSED_ORDER = " order by r.displayOrder, p.name, p.id";

    /** 폐업 처리의 분모·분자가 함께 쓰는 조건: 그 원천·원천 지역의 열려 있는 CANDIDATE·APPROVED. */
    String OPEN_IN_SOURCE_REGIONS = """
            from Place p
            where p.source = :source
              and p.source <> com.shineunsigan.snsg.place.domain.constants.PlaceSource.MANUAL
              and p.status in (com.shineunsigan.snsg.place.domain.constants.PlaceStatus.CANDIDATE,
                               com.shineunsigan.snsg.place.domain.constants.PlaceStatus.APPROVED)
              and p.isClosed = false
              and p.isDeleted = false
              and p.sourceRegionCode in :regionCodes
            """;

    @Query("select p " + EXPOSED + " and p.id = :id")
    Optional<Place> findExposed(@Param("id") UUID id);

    @Query("select p " + EXPOSED + " and p.placeType = :placeType" + EXPOSED_ORDER)
    List<Place> findAllExposed(@Param("placeType") PlaceType placeType);

    @Query("select p " + EXPOSED + " and p.placeType = :placeType and p.regionCode in :regionCodes" + EXPOSED_ORDER)
    List<Place> findAllExposedInRegions(@Param("placeType") PlaceType placeType,
            @Param("regionCodes") Collection<String> regionCodes);

    @Query("select p from Place p where p.sourceId in :sourceIds and p.isDeleted = false")
    List<Place> findAllBySourceIds(@Param("sourceIds") Collection<String> sourceIds);

    @Query("select p " + OPEN_IN_SOURCE_REGIONS + " and p.lastSyncedAt < :before")
    List<Place> findAllCloseTargets(@Param("source") PlaceSource source,
            @Param("regionCodes") Collection<String> regionCodes, @Param("before") LocalDateTime before);

    @Query("select p.sourceRegionCode, count(p) " + OPEN_IN_SOURCE_REGIONS + " group by p.sourceRegionCode")
    List<Object[]> countOpenBySourceRegion(@Param("source") PlaceSource source,
            @Param("regionCodes") Collection<String> regionCodes);

    /** JPA Auditing을 거치지 않으므로 updatedAt을 직접 넣는다. places에는 감사 주체 컬럼이 없다. */
    @Modifying
    @Query("""
            update Place p set p.lastSyncedAt = :syncedAt, p.updatedAt = :syncedAt
            where p.source = :source and p.sourceId in :sourceIds and p.isDeleted = false
            """)
    void markSynced(@Param("source") PlaceSource source, @Param("sourceIds") Collection<String> sourceIds,
            @Param("syncedAt") LocalDateTime syncedAt);
}
