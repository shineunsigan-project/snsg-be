package com.shineunsigan.snsg.schedule.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.common.web.GlobalExceptionHandler;
import com.shineunsigan.snsg.schedule.application.ListPlaceCandidatesFacade;
import com.shineunsigan.snsg.schedule.application.dto.result.ExposedPlaceResult;
import com.shineunsigan.snsg.schedule.domain.constants.ActivityPlaceType;
import com.shineunsigan.snsg.schedule.domain.exception.ScheduleErrorCode;

/**
 * 응답 필드(장소 유형을 내보내지 않는다)와, regionCode의 생략과 빈 값을 구분해 넘기는지만 본다.
 * <p>
 * auth를 import하지 않는다. 인증 필터는 auth 패키지를 스캔에서 빼서 올리지 않는다.
 */
@WebMvcTest(controllers = PlaceCandidateController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.shineunsigan\\.snsg\\.auth\\..*"))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class PlaceCandidateControllerTest {

    private static final String PATH = "/api/v1/place-candidates";
    private static final UUID CATEGORY_ID = UUID.fromString("01a19cc6-3d80-7000-8000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListPlaceCandidatesFacade listPlaceCandidatesFacade;

    @Test
    @DisplayName("200이고 원소에 일곱 필드가 있으며 장소 유형은 없다")
    void listsCandidates() throws Exception {
        UUID placeId = UUID.randomUUID();
        when(listPlaceCandidatesFacade.list(any(), any())).thenReturn(List.of(new ExposedPlaceResult(placeId,
                ActivityPlaceType.BOARD_GAME_CAFE, "보드게임카페", "서울특별시 마포구 와우산로 1", "2층",
                new BigDecimal("37.5500000"), new BigDecimal("126.9200000"), "11440")));

        mockMvc.perform(get(PATH).param("categoryId", CATEGORY_ID.toString()).param("regionCode", "11440"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data[0].id").value(placeId.toString()))
                .andExpect(jsonPath("$.data[0].name").value("보드게임카페"))
                .andExpect(jsonPath("$.data[0].roadAddress").value("서울특별시 마포구 와우산로 1"))
                .andExpect(jsonPath("$.data[0].addressDetail").value("2층"))
                .andExpect(jsonPath("$.data[0].latitude").value(37.55))
                .andExpect(jsonPath("$.data[0].longitude").value(126.92))
                .andExpect(jsonPath("$.data[0].regionCode").value("11440"))
                .andExpect(jsonPath("$.data[0].placeType").doesNotExist())
                .andExpect(jsonPath("$.data[0].regionName").doesNotExist());
        verify(listPlaceCandidatesFacade).list(CATEGORY_ID, "11440");
    }

    @Test
    @DisplayName("regionCode를 보내지 않으면 지역 생략(null)으로 넘긴다")
    void omittedRegion() throws Exception {
        when(listPlaceCandidatesFacade.list(any(), any())).thenReturn(List.of());

        mockMvc.perform(get(PATH).param("categoryId", CATEGORY_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
        verify(listPlaceCandidatesFacade).list(eq(CATEGORY_ID), isNull());
    }

    @Test
    @DisplayName("regionCode를 빈 값으로 보내면 생략이 아니라 빈 값으로 넘기고, INVALID_REGION이면 400")
    void emptyRegion() throws Exception {
        when(listPlaceCandidatesFacade.list(CATEGORY_ID, ""))
                .thenThrow(new BusinessException(ScheduleErrorCode.INVALID_REGION));

        mockMvc.perform(get(PATH + "?categoryId=" + CATEGORY_ID + "&regionCode="))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REGION"));
        verify(listPlaceCandidatesFacade).list(CATEGORY_ID, "");
    }
}
