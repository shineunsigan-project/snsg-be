package com.shineunsigan.snsg.place.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.shineunsigan.snsg.common.config.JpaAuditingConfig;
import com.shineunsigan.snsg.place.application.dto.command.UpsertPlaceCommand;
import com.shineunsigan.snsg.place.application.dto.result.PlaceSourcePageResult;
import com.shineunsigan.snsg.place.application.dto.result.PlaceSourceRow;
import com.shineunsigan.snsg.place.application.dto.result.RegionResult;
import com.shineunsigan.snsg.place.application.port.out.PlaceImportLockPort;
import com.shineunsigan.snsg.place.application.port.out.PublicPlaceSourcePort;
import com.shineunsigan.snsg.place.domain.constants.PlaceSource;
import com.shineunsigan.snsg.place.domain.constants.PlaceStatus;
import com.shineunsigan.snsg.place.domain.constants.PlaceType;
import com.shineunsigan.snsg.place.infrastructure.persistence.PlaceRepositoryImpl;

import jakarta.persistence.EntityManager;

/**
 * 장소 적재 흐름: 지역마다 페이지를 끝까지 받는지, 완주한 지역만 폐업 처리에 넘기는지, 재시도와 잠금을 본다(Mockito).
 * 걸러진 기존 행·다른 구로 옮긴 가게·같은 응답의 재실행 결과는 실제 PostgreSQL로 본다.
 * <p>
 * 공유 DB의 실제 장소(지역 11200·11410·11440)와 섞이지 않도록 가짜 지역 코드(99xxx)와 무작위 상가업소번호를 쓴다.
 */
class ImportPlacesFacadeTest {

    /** application.yaml과 같은 적재 정책. */
    private static final PlaceProperties PROPERTIES = new PlaceProperties(new BigDecimal("0.3"), Duration.ofSeconds(10),
            3, List.of("R10405", "R10499"), List.of("보드게임", "보드카페", "방탈출"),
            Map.of(PlaceType.BOARD_GAME_CAFE, List.of("보드"),
                    PlaceType.ESCAPE_ROOM, List.of("방탈출", "이스케이프", "escape")));

    private static final String TARGET_CATEGORY = "R10405";
    private static final String OTHER_CATEGORY = "I21201";
    private static final String INCOMPLETE_WARNING = "장소 적재 미완주";
    private static final String REVIEW_WARNING = "승인된 장소의 원천 값이 바뀌었다";

    /** 지정 업종의 방탈출. 적재 대상이다. */
    private static PlaceSourceRow targetRow(String sourceId) {
        return new PlaceSourceRow(sourceId, "테스트방탈출", "", TARGET_CATEGORY, "서울특별시 테스트로 1", "", "2",
                "126.9", "37.5");
    }

    /** 그 밖 업종, 키워드 없는 상호. 적재 대상이 아니다. */
    private static PlaceSourceRow otherCategoryRow(String sourceId) {
        return new PlaceSourceRow(sourceId, "보드람치킨", "", OTHER_CATEGORY, "서울특별시 테스트로 1", "", "1",
                "126.9", "37.5");
    }

    /** 지정 업종이지만 좌표가 비어 적재하지 않는다. */
    private static PlaceSourceRow emptyCoordinateRow(String sourceId) {
        return new PlaceSourceRow(sourceId, "테스트방탈출", "", TARGET_CATEGORY, "서울특별시 테스트로 1", "", "2", "", "");
    }

    private static PlaceSourcePageResult page(int totalCount, List<PlaceSourceRow> rows) {
        return PlaceSourcePageResult.success(totalCount, rows);
    }

    /** 대상 행 size개로 채운 페이지. 상가업소번호는 prefix + 순번이다. */
    private static PlaceSourcePageResult page(int totalCount, int size, String prefix) {
        List<PlaceSourceRow> rows = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            rows.add(targetRow(prefix + i));
        }
        return page(totalCount, rows);
    }

    private static List<RegionResult> regions(String... codes) {
        return Arrays.stream(codes).map(code -> new RegionResult(code, "테스트시", "구" + code, true)).toList();
    }

    @Nested
    @DisplayName("흐름")
    @ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
    class Flow {

        private static final String REGION_A = "99001";
        private static final String REGION_B = "99002";

        @Mock
        private PlaceImportLockPort placeImportLockPort;

        @Mock
        private PublicPlaceSourcePort publicPlaceSourcePort;

        @Mock
        private RegionQueryService regionQueryService;

        @Mock
        private PlaceCommandService placeCommandService;

        private ImportPlacesFacade facade;

        @BeforeEach
        void setUp() {
            facade = new ImportPlacesFacade(placeImportLockPort, publicPlaceSourcePort, regionQueryService,
                    placeCommandService, PROPERTIES);
        }

        private void lockAcquired() {
            when(placeImportLockPort.runExclusively(any())).thenAnswer(invocation -> {
                invocation.<Runnable>getArgument(0).run();
                return true;
            });
        }

        private void activeRegions(String... codes) {
            lockAcquired();
            when(regionQueryService.findActive()).thenReturn(regions(codes));
        }

        private void respond(String regionCode, int pageNo, PlaceSourcePageResult result) {
            when(publicPlaceSourcePort.fetchPage(regionCode, pageNo)).thenReturn(result);
        }

        private void verifyCompleted(String... regionCodes) {
            verify(placeCommandService).closeMissing(eq(PlaceSource.SEMAS), eq(List.of(regionCodes)), any());
        }

        @SuppressWarnings("unchecked")
        private List<List<UpsertPlaceCommand>> capturedUpserts(int times) {
            ArgumentCaptor<List<UpsertPlaceCommand>> captor = ArgumentCaptor.forClass(List.class);
            verify(placeCommandService, times(times)).reflectPage(eq(PlaceSource.SEMAS), captor.capture(), anyList());
            return captor.getAllValues();
        }

        @Test
        @DisplayName("2,500건 지역은 3페이지를 모두 받아 반영하고 완주로 넘긴다")
        void threePagesCompleted() {
            activeRegions(REGION_A);
            respond(REGION_A, 1, page(2500, 1000, "A1-"));
            respond(REGION_A, 2, page(2500, 1000, "A2-"));
            respond(REGION_A, 3, page(2500, 500, "A3-"));

            facade.importPlaces();

            verify(publicPlaceSourcePort).fetchPage(REGION_A, 1);
            verify(publicPlaceSourcePort).fetchPage(REGION_A, 2);
            verify(publicPlaceSourcePort).fetchPage(REGION_A, 3);
            assertThat(capturedUpserts(3)).extracting(List::size).containsExactly(1000, 1000, 500);
            verifyCompleted(REGION_A);
        }

        @Test
        @DisplayName("첫 지역의 2페이지가 실패하면 1페이지 반영은 남고 그 지역은 폐업 처리에서 빠지며, 다음 지역은 받아서 완주한다")
        void middlePageFailure() {
            activeRegions(REGION_A, REGION_B);
            respond(REGION_A, 1, page(2000, 1000, "A1-"));
            respond(REGION_A, 2, PlaceSourcePageResult.failure("HTTP 500"));
            respond(REGION_B, 1, page(1, 1, "B1-"));

            facade.importPlaces();

            List<List<UpsertPlaceCommand>> upserts = capturedUpserts(2);
            assertThat(upserts.get(0)).hasSize(1000).allMatch(row -> row.sourceId().startsWith("A1-")
                    && row.regionCode().equals(REGION_A));
            assertThat(upserts.get(1)).extracting(UpsertPlaceCommand::sourceId).containsExactly("B1-0");
            verifyCompleted(REGION_B);
        }

        @Test
        @DisplayName("결과 코드 오류면 그 지역은 폐업 처리에서 빠지고, 경고 로그에 지역 코드와 이유가 남는다")
        void resultCodeError(CapturedOutput output) {
            activeRegions(REGION_A);
            respond(REGION_A, 1, PlaceSourcePageResult.failure("결과 코드 22 LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR"));

            facade.importPlaces();

            verifyCompleted();
            assertThat(output).contains("WARN").contains(INCOMPLETE_WARNING).contains("regionCode=" + REGION_A)
                    .contains("결과 코드 22");
        }

        @Test
        @DisplayName("데이터 없음(결과 코드 03)이면 다시 부르지 않고 그 지역은 0건 미완주로 폐업 처리에서 빠지며, 다음 지역은 진행된다")
        void noDataIsNotRetried(CapturedOutput output) {
            activeRegions(REGION_A, REGION_B);
            respond(REGION_A, 1, PlaceSourcePageResult.noData("결과 코드 03 NODATA_ERROR"));
            respond(REGION_B, 1, page(1, 1, "B1-"));

            facade.importPlaces();

            verify(publicPlaceSourcePort, times(1)).fetchPage(REGION_A, 1);
            verifyCompleted(REGION_B);
            assertThat(output).contains(INCOMPLETE_WARNING).contains("regionCode=" + REGION_A).contains("0건");
        }

        @Test
        @DisplayName("받은 건수 합계가 totalCount와 다르면 그 지역은 폐업 처리에서 빠지고, 경고 로그에 이유가 남는다")
        void countMismatch(CapturedOutput output) {
            activeRegions(REGION_A);
            respond(REGION_A, 1, page(5, 3, "A1-"));

            facade.importPlaces();

            verify(placeCommandService).reflectPage(eq(PlaceSource.SEMAS), anyList(), anyList());
            verifyCompleted();
            assertThat(output).contains(INCOMPLETE_WARNING).contains("regionCode=" + REGION_A)
                    .contains("받은 건수가 totalCount와 다름");
        }

        @Test
        @DisplayName("중간 페이지가 0건이면 더 부르지 않고 그 지역은 폐업 처리에서 빠진다")
        void emptyMiddlePage() {
            activeRegions(REGION_A);
            respond(REGION_A, 1, page(3000, 1000, "A1-"));
            respond(REGION_A, 2, page(3000, List.of()));

            facade.importPlaces();

            verify(publicPlaceSourcePort, times(0)).fetchPage(REGION_A, 3);
            verifyCompleted();
        }

        @Test
        @DisplayName("0건이면 그 지역은 폐업 처리에서 빠지고, 경고 로그에 이유가 남는다")
        void zeroRows(CapturedOutput output) {
            activeRegions(REGION_A);
            respond(REGION_A, 1, page(0, List.of()));

            facade.importPlaces();

            verify(placeCommandService, times(0)).reflectPage(any(), anyList(), anyList());
            verifyCompleted();
            assertThat(output).contains(INCOMPLETE_WARNING).contains("regionCode=" + REGION_A).contains("0건");
        }

        @Test
        @DisplayName("반영이 예외로 끝나면 그 지역은 폐업 처리에서 빠지고 다음 지역은 진행된다")
        void reflectFailure(CapturedOutput output) {
            activeRegions(REGION_A, REGION_B);
            respond(REGION_A, 1, page(1, 1, "A1-"));
            respond(REGION_B, 1, page(1, 1, "B1-"));
            doThrow(new IllegalStateException("DB 오류")).doNothing()
                    .when(placeCommandService).reflectPage(eq(PlaceSource.SEMAS), anyList(), anyList());

            facade.importPlaces();

            verify(publicPlaceSourcePort).fetchPage(REGION_B, 1);
            verifyCompleted(REGION_B);
            assertThat(output).contains(INCOMPLETE_WARNING).contains("regionCode=" + REGION_A).contains("반영 실패");
        }

        @Test
        @DisplayName("폐업 처리는 한 번이고, 넘긴 시작 시각은 모든 반영보다 이르거나 같다")
        void startedAtBeforeAllReflections() {
            activeRegions(REGION_A, REGION_B);
            respond(REGION_A, 1, page(1500, 1000, "A1-"));
            respond(REGION_A, 2, page(1500, 500, "A2-"));
            respond(REGION_B, 1, page(1, 1, "B1-"));
            List<LocalDateTime> reflectedAt = new ArrayList<>();
            doAnswer(invocation -> reflectedAt.add(LocalDateTime.now()))
                    .when(placeCommandService).reflectPage(eq(PlaceSource.SEMAS), anyList(), anyList());

            facade.importPlaces();

            ArgumentCaptor<LocalDateTime> startedAt = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(placeCommandService, times(1)).closeMissing(eq(PlaceSource.SEMAS), anyCollection(),
                    startedAt.capture());
            assertThat(reflectedAt).hasSize(3).allMatch(time -> !time.isBefore(startedAt.getValue()));
        }

        @Test
        @DisplayName("페이지 받기가 한 번 실패한 뒤 성공하면 같은 페이지를 다시 불러 그 지역은 완주한다")
        void retryThenSuccess() {
            activeRegions(REGION_A);
            when(publicPlaceSourcePort.fetchPage(REGION_A, 1))
                    .thenReturn(PlaceSourcePageResult.failure("I/O 오류(SocketTimeoutException)"))
                    .thenReturn(page(1, 1, "A1-"));

            facade.importPlaces();

            verify(publicPlaceSourcePort, times(2)).fetchPage(REGION_A, 1);
            verifyCompleted(REGION_A);
        }

        @Test
        @DisplayName("최대 시도 횟수만큼 모두 실패하면 그 페이지를 그만큼 부르고 그 지역은 미완주, 다음 지역은 진행된다")
        void retryExhausted(CapturedOutput output) {
            activeRegions(REGION_A, REGION_B);
            respond(REGION_A, 1, PlaceSourcePageResult.failure("HTTP 502"));
            respond(REGION_B, 1, page(1, 1, "B1-"));

            facade.importPlaces();

            verify(publicPlaceSourcePort, times(PROPERTIES.importMaxAttempts())).fetchPage(REGION_A, 1);
            verify(publicPlaceSourcePort).fetchPage(REGION_B, 1);
            verifyCompleted(REGION_B);
            assertThat(output).contains(INCOMPLETE_WARNING).contains("regionCode=" + REGION_A).contains("HTTP 502");
        }

        @Test
        @DisplayName("포트가 예외를 던져도 받기 실패로 보고 다시 부른다")
        void portExceptionIsRetried() {
            activeRegions(REGION_A);
            when(publicPlaceSourcePort.fetchPage(REGION_A, 1))
                    .thenThrow(new IllegalStateException("예상하지 못한 오류"))
                    .thenReturn(page(1, 1, "A1-"));

            facade.importPlaces();

            verifyCompleted(REGION_A);
        }

        @Test
        @DisplayName("활성 지역만 받는다. 활성 지역 목록에 없는 지역으로는 원천을 부르지 않는다")
        void onlyActiveRegions() {
            activeRegions(REGION_A);
            respond(REGION_A, 1, page(1, 1, "A1-"));

            facade.importPlaces();

            verify(publicPlaceSourcePort).fetchPage(REGION_A, 1);
            verifyNoMoreInteractions(publicPlaceSourcePort);
            verify(regionQueryService).findActive();
            verifyNoMoreInteractions(regionQueryService);
        }

        @Test
        @DisplayName("잠금을 얻지 못하면 지역 조회·원천 호출·반영·폐업 처리를 하나도 하지 않고 건너뛴다는 로그를 남긴다")
        void skippedWhenLocked(CapturedOutput output) {
            when(placeImportLockPort.runExclusively(any())).thenReturn(false);

            facade.importPlaces();

            verifyNoInteractions(regionQueryService, publicPlaceSourcePort, placeCommandService);
            assertThat(output).contains("건너뛴다");
        }

        @Test
        @DisplayName("적재 대상은 반영하고, 걸러진 행은 확인 시각만 갱신하도록 넘기며, 상가업소번호가 없는 행은 버린다")
        void splitsTargetsAndFilteredRows() {
            activeRegions(REGION_A);
            respond(REGION_A, 1, page(4, List.of(targetRow("T1"), otherCategoryRow("F1"), emptyCoordinateRow("F2"),
                    targetRow(" "))));

            facade.importPlaces();

            ArgumentCaptor<List<UpsertPlaceCommand>> upserts = listCaptor();
            ArgumentCaptor<Collection<String>> confirmedOnly = listCaptor();
            verify(placeCommandService).reflectPage(eq(PlaceSource.SEMAS), upserts.capture(), confirmedOnly.capture());
            assertThat(upserts.getValue()).singleElement().satisfies(row -> {
                assertThat(row.sourceId()).isEqualTo("T1");
                assertThat(row.regionCode()).isEqualTo(REGION_A);
                assertThat(row.placeType()).isEqualTo(PlaceType.ESCAPE_ROOM);
            });
            assertThat(confirmedOnly.getValue()).containsExactly("F1", "F2");
            verifyCompleted(REGION_A);
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private <T> ArgumentCaptor<T> listCaptor() {
            return (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
        }
    }

    @Nested
    @DisplayName("저장 결과")
    @DataJpaTest
    @AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
    @Import({ImportPlacesFacade.class, PlaceCommandService.class, PlaceRepositoryImpl.class, JpaAuditingConfig.class,
            StoredConfig.class})
    @ExtendWith(OutputCaptureExtension.class)
    class Stored {

        private static final String REGION_A = "99101";
        private static final String REGION_B = "99102";
        private static final LocalDateTime OLD_SYNC = LocalDateTime.of(2020, 1, 1, 12, 0);

        @Autowired
        private ImportPlacesFacade facade;

        @Autowired
        private EntityManager entityManager;

        @MockitoBean
        private PublicPlaceSourcePort publicPlaceSourcePort;

        @MockitoBean
        private RegionQueryService regionQueryService;

        @MockitoSpyBean
        private PlaceCommandService placeCommandService;

        private static String randomSourceId() {
            return "TEST-" + UUID.randomUUID();
        }

        /** 실행하고 폐업 처리에 넘긴 시작 시각을 돌려준다. 확인 쿼리가 DB 값을 보도록 반영을 내보내고 비운다. */
        private LocalDateTime run() {
            facade.importPlaces();
            entityManager.flush();
            entityManager.clear();
            ArgumentCaptor<LocalDateTime> startedAt = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(placeCommandService, atLeastOnce())
                    .closeMissing(eq(PlaceSource.SEMAS), anyCollection(), startedAt.capture());
            return startedAt.getValue();
        }

        /** 걸러진 기존 행 하나와, 폐업 비율 상한 안에 들도록 적재 대상 4건을 함께 준다. */
        private void respondWithFilteredExisting(PlaceSourceRow filtered) {
            List<PlaceSourceRow> rows = new ArrayList<>(List.of(filtered));
            for (int i = 0; i < 4; i++) {
                rows.add(targetRow(randomSourceId()));
            }
            when(regionQueryService.findActive()).thenReturn(regions(REGION_A));
            when(publicPlaceSourcePort.fetchPage(REGION_A, 1)).thenReturn(page(rows.size(), rows));
        }

        @Test
        @DisplayName("업종 필터에서 걸러진 기존 승인 행은 확인 시각만 갱신되어 폐업 처리되지 않고, 다른 값은 그대로다")
        void filteredByCategoryIsNotClosed() {
            String sourceId = randomSourceId();
            insert(sourceId, PlaceStatus.APPROVED, REGION_A);
            respondWithFilteredExisting(otherCategoryRow(sourceId));

            LocalDateTime startedAt = run();

            assertUntouchedExceptSync(sourceId, PlaceStatus.APPROVED, startedAt);
        }

        @Test
        @DisplayName("좌표가 빈 원천 행으로 들어온 기존 후보 행도 확인 시각만 갱신되어 폐업 처리되지 않고, 다른 값은 그대로다")
        void filteredByEmptyCoordinateIsNotClosed() {
            String sourceId = randomSourceId();
            insert(sourceId, PlaceStatus.CANDIDATE, REGION_A);
            respondWithFilteredExisting(emptyCoordinateRow(sourceId));

            LocalDateTime startedAt = run();

            assertUntouchedExceptSync(sourceId, PlaceStatus.CANDIDATE, startedAt);
        }

        private void assertUntouchedExceptSync(String sourceId, PlaceStatus status, LocalDateTime startedAt) {
            Object[] row = (Object[]) entityManager.createNativeQuery("""
                            SELECT is_closed, last_synced_at, status, place_type, name, road_address, address_detail,
                                   source_name, source_road_address, source_address_detail, source_region_code
                            FROM places WHERE source = 'SEMAS' AND source_id = ?""")
                    .setParameter(1, sourceId)
                    .getSingleResult();
            assertThat(row[0]).isEqualTo(false);
            // DB 시각은 마이크로초까지라, 같은 마이크로초 안에서 시작 시각보다 늦게 잰 값도 저장되면 더 작아 보일 수 있다.
            assertThat(toLocalDateTime(row[1])).isAfterOrEqualTo(startedAt.truncatedTo(ChronoUnit.MICROS));
            assertThat(Arrays.copyOfRange(row, 2, row.length)).containsExactly(status.name(), "ESCAPE_ROOM",
                    "검수한이름", "서울특별시 검수로 1", "3층", "원천이름", "서울특별시 원천로 1", "2층", REGION_A);
        }

        private LocalDateTime toLocalDateTime(Object value) {
            return value instanceof Timestamp timestamp ? timestamp.toLocalDateTime() : (LocalDateTime) value;
        }

        @ParameterizedTest(name = "지역 순서 {0}")
        @ValueSource(strings = {"99101,99102", "99102,99101"})
        @DisplayName("다른 구로 옮긴 가게는 옮긴 지역의 응답에서 확인되어 폐업 처리되지 않고 원천 지역이 바뀐다. 지역 순서와 무관하다")
        void movedStoreIsNotClosed(String order) {
            String moved = randomSourceId();
            insert(moved, PlaceStatus.APPROVED, REGION_A);
            when(regionQueryService.findActive()).thenReturn(regions(order.split(",")));
            when(publicPlaceSourcePort.fetchPage(REGION_A, 1)).thenReturn(page(3, List.of(
                    targetRow(randomSourceId()), targetRow(randomSourceId()), targetRow(randomSourceId()))));
            when(publicPlaceSourcePort.fetchPage(REGION_B, 1)).thenReturn(page(1, List.of(targetRow(moved))));

            run();

            ArgumentCaptor<Collection<String>> completed = ArgumentCaptor.forClass(Collection.class);
            verify(placeCommandService).closeMissing(eq(PlaceSource.SEMAS), completed.capture(), any());
            assertThat(completed.getValue()).containsExactlyInAnyOrder(REGION_A, REGION_B);
            Object[] row = (Object[]) entityManager.createNativeQuery(
                            "SELECT is_closed, source_region_code FROM places WHERE source = 'SEMAS' AND source_id = ?")
                    .setParameter(1, moved)
                    .getSingleResult();
            assertThat(row).containsExactly(false, REGION_B);
        }

        @Test
        @DisplayName("같은 응답으로 두 번 돌려도 행 수와 값이 같고, 소수 8자리 이상 좌표여도 두 번째에 원천 변경 경고가 나지 않는다")
        void idempotent(CapturedOutput output) {
            String approved = randomSourceId();
            insert(approved, PlaceStatus.APPROVED, REGION_A);
            List<PlaceSourceRow> rows = List.of(
                    longCoordinateRow(approved, "키이스케이프", "홍대점"),
                    longCoordinateRow(randomSourceId(), "레드버튼보드게임카페", ""),
                    longCoordinateRow(randomSourceId(), "즐거운오락실", ""),
                    otherCategoryRow(randomSourceId()));
            when(regionQueryService.findActive()).thenReturn(regions(REGION_A));
            when(publicPlaceSourcePort.fetchPage(REGION_A, 1)).thenReturn(page(rows.size(), rows));

            run();
            List<List<Object>> first = snapshot();
            int outputBeforeSecondRun = output.getAll().length();
            run();
            List<List<Object>> second = snapshot();

            assertThat(first).hasSize(3);
            assertThat(second).isEqualTo(first);
            assertThat(output.getAll().substring(outputBeforeSecondRun)).doesNotContain(REVIEW_WARNING);
        }

        private PlaceSourceRow longCoordinateRow(String sourceId, String storeName, String branchName) {
            return new PlaceSourceRow(sourceId, storeName, branchName, TARGET_CATEGORY, "서울특별시 마포구 와우산로 1",
                    "", "B1", "126.912345678", "37.556789012345");
        }

        /** 그 지역 SEMAS 행의 유형·검수 상태·폐업·화면 값·원천 값. 확인 시각과 수정 시각은 뺀다. */
        @SuppressWarnings("unchecked")
        private List<List<Object>> snapshot() {
            List<Object[]> rows = entityManager.createNativeQuery("""
                            SELECT source_id, place_type, status, is_closed, name, road_address, address_detail,
                                   latitude, longitude, region_code, source_name, source_road_address,
                                   source_address_detail, source_latitude, source_longitude, source_region_code
                            FROM places
                            WHERE source = 'SEMAS' AND source_region_code = ? AND is_deleted = FALSE
                            ORDER BY source_id""")
                    .setParameter(1, REGION_A)
                    .getResultList();
            return rows.stream().map(Arrays::asList).toList();
        }

        /** 원천 값과 다른 화면 값(검수에서 고친 값)을 가진 열린 SEMAS 행. 마지막 확인은 오래전이다. */
        private void insert(String sourceId, PlaceStatus status, String regionCode) {
            entityManager.createNativeQuery("""
                            INSERT INTO places (id, place_type, name, road_address, address_detail, latitude, longitude,
                                                region_code, status, is_closed, source, source_id, source_name,
                                                source_road_address, source_address_detail, source_latitude,
                                                source_longitude, source_region_code, last_synced_at,
                                                created_at, updated_at, is_deleted)
                            VALUES (?, 'ESCAPE_ROOM', '검수한이름', '서울특별시 검수로 1', '3층', 37.5, 126.9, ?, ?,
                                    FALSE, 'SEMAS', ?, '원천이름', '서울특별시 원천로 1', '2층', 37.5, 126.9, ?, ?,
                                    NOW(), NOW(), FALSE)""")
                    .setParameter(1, UUID.randomUUID())
                    .setParameter(2, regionCode)
                    .setParameter(3, status.name())
                    .setParameter(4, sourceId)
                    .setParameter(5, regionCode)
                    .setParameter(6, OLD_SYNC)
                    .executeUpdate();
        }
    }

    @TestConfiguration
    static class StoredConfig {

        @Bean
        PlaceProperties placeProperties() {
            return PROPERTIES;
        }

        /** 잠금 경쟁은 PostgresPlaceImportLockAdapterTest가 본다. 여기서는 바로 돌린다. */
        @Bean
        PlaceImportLockPort placeImportLockPort() {
            return task -> {
                task.run();
                return true;
            };
        }
    }
}
