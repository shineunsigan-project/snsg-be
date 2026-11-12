package com.shineunsigan.snsg.place.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.shineunsigan.snsg.common.web.GlobalExceptionHandler;
import com.shineunsigan.snsg.place.application.PlaceReviewCommandService;
import com.shineunsigan.snsg.place.application.PlaceReviewQueryService;
import com.shineunsigan.snsg.place.application.dto.result.PlaceReviewResult;
import com.shineunsigan.snsg.place.domain.constants.PlaceSource;
import com.shineunsigan.snsg.place.domain.constants.PlaceStatus;
import com.shineunsigan.snsg.place.domain.constants.PlaceType;

/**
 * 수정·등록 요청의 형식 규칙(앞뒤 공백을 뺀 길이, 상세주소 빈 값, 좌표 경계)과 에러 형식을 본다.
 * 인가 규칙은 {@code AdminSecurityConfigTest}가 본다. 필터를 끄고 어드민 principal을 SecurityContext에 직접 넣는다.
 */
@WebMvcTest(controllers = AdminPlaceController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.shineunsigan\\.snsg\\.auth\\..*"))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AdminPlaceControllerTest {

    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID PLACE_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlaceReviewQueryService placeReviewQueryService;

    @MockitoBean
    private PlaceReviewCommandService placeReviewCommandService;

    @BeforeEach
    void authenticate() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(ADMIN_ID, null, List.of()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static PlaceReviewResult result() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 13, 14, 20);
        return new PlaceReviewResult(PLACE_ID, PlaceType.BOARD_GAME_CAFE, PlaceStatus.APPROVED, "레드버튼",
                "서울특별시 서대문구 연세로 1", "", new BigDecimal("37.5"), new BigDecimal("126.9"), "11410", false,
                PlaceSource.MANUAL, "", "", "", "", BigDecimal.ZERO, BigDecimal.ZERO, "", now, now,
                ADMIN_ID.toString(), now, ADMIN_ID.toString(), ADMIN_ID.toString(), now);
    }

    /** 수정 본문. 지정한 필드만 바꾸고 나머지는 올바른 값이다. 값은 JSON 조각 그대로 넣는다. */
    private static String updateBody(String field, String json) {
        String name = "\"레드버튼\"";
        String addressDetail = "\"2층\"";
        String latitude = "37.5";
        String regionCode = "\"11410\"";
        switch (field) {
            case "name" -> name = json;
            case "addressDetail" -> addressDetail = json;
            case "latitude" -> latitude = json;
            case "regionCode" -> regionCode = json;
            default -> {
            }
        }
        return """
                {"placeType": "BOARD_GAME_CAFE", "status": "APPROVED", "name": %s,
                 "roadAddress": "서울특별시 서대문구 연세로 1", "addressDetail": %s,
                 "latitude": %s, "longitude": 126.9, "regionCode": %s}""".formatted(name, addressDetail, latitude,
                regionCode);
    }

    private static String quoted(String value) {
        return "\"" + value + "\"";
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "앞뒤 공백을 빼면 100자인 장소명, name, PADDED_100",
            "빈 상세주소, addressDetail, EMPTY",
            "공백뿐인 상세주소, addressDetail, BLANK",
            "위도 경계 39, latitude, 39",
            "위도 경계 33, latitude, 33"})
    @DisplayName("형식에 맞으면 서비스로 넘어가 200이다")
    void validUpdate(String caseName, String field, String value) throws Exception {
        when(placeReviewCommandService.review(any())).thenReturn(result());

        mockMvc.perform(put("/api/admin/v1/places/{placeId}", PLACE_ID).contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(field, json(value))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(PLACE_ID.toString()));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "101자 장소명, name, LONG_101, 장소명은 1~100자로 적어주세요.",
            "공백뿐인 장소명, name, BLANK, 장소명은 1~100자로 적어주세요.",
            "null 상세주소, addressDetail, null, 상세주소는 100자 이내로 적어주세요.",
            "위도 39 초과, latitude, 39.0000001, 위도는 33~39 사이로 적어주세요.",
            "빈 지역, regionCode, EMPTY, 선택할 수 없는 지역이에요. 지역을 다시 골라주세요."})
    @DisplayName("형식이 틀리면 400 INVALID_REQUEST와 필드별 errors이고 서비스가 불리지 않는다")
    void invalidUpdate(String caseName, String field, String value, String message) throws Exception {
        mockMvc.perform(put("/api/admin/v1/places/{placeId}", PLACE_ID).contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(field, json(value))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value(field))
                .andExpect(jsonPath("$.errors[0].message").value(message));

        verifyNoInteractions(placeReviewCommandService);
    }

    private static String json(String value) {
        return switch (value) {
            case "PADDED_100" -> quoted("  " + "가".repeat(100) + "  ");
            case "LONG_101" -> quoted("가".repeat(101));
            case "EMPTY" -> quoted("");
            case "BLANK" -> quoted("   ");
            default -> value;
        };
    }

    @Test
    @DisplayName("검수 상태 CANDIDATE는 형식 오류가 아니라 서비스로 넘어간다(도메인이 INVALID_REVIEW_STATUS로 막는다)")
    void candidateStatusReachesService() throws Exception {
        when(placeReviewCommandService.review(any())).thenReturn(result());

        mockMvc.perform(put("/api/admin/v1/places/{placeId}", PLACE_ID).contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("", "").replace("\"APPROVED\"", "\"CANDIDATE\"")))
                .andExpect(status().isOk());

        verify(placeReviewCommandService).review(any());
    }

    @Test
    @DisplayName("목록 밖 enum 값은 400 INVALID_REQUEST이고 errors가 없다")
    void unknownEnumIsInvalidRequest() throws Exception {
        mockMvc.perform(put("/api/admin/v1/places/{placeId}", PLACE_ID).contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("", "").replace("\"APPROVED\"", "\"PENDING\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors").doesNotExist());
        mockMvc.perform(get("/api/admin/v1/places").param("status", "PENDING"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(placeReviewCommandService, placeReviewQueryService);
    }

    @Test
    @DisplayName("등록은 201이고 그 adminId를 검수자로 넘긴다. 폐업 여부는 FE 계약 키 isClosed로 나간다(closed 아님)")
    void createReturnsCreated() throws Exception {
        when(placeReviewCommandService.createManual(any())).thenReturn(result());

        mockMvc.perform(post("/api/admin/v1/places").contentType(MediaType.APPLICATION_JSON).content("""
                        {"placeType": "BOARD_GAME_CAFE", "name": "레드버튼", "roadAddress": "서울특별시 서대문구 연세로 1",
                         "addressDetail": "", "latitude": 37.5, "longitude": 126.9, "regionCode": "11410"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.source").value("MANUAL"))
                .andExpect(jsonPath("$.data.isClosed").value(false))
                .andExpect(jsonPath("$.data.closed").doesNotExist());

        verify(placeReviewCommandService).createManual(argThat(
                command -> ADMIN_ID.equals(command.reviewerId())));
    }
}
