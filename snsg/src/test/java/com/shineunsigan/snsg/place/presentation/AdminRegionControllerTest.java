package com.shineunsigan.snsg.place.presentation;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.shineunsigan.snsg.place.application.RegionQueryService;
import com.shineunsigan.snsg.place.application.dto.result.RegionResult;

/**
 * 비활성 지역까지 주는지와 FE 계약 키(isActive)를 본다. 인가 규칙은 {@code AdminSecurityConfigTest}가 본다.
 */
@WebMvcTest(controllers = AdminRegionController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.shineunsigan\\.snsg\\.auth\\..*"))
@AutoConfigureMockMvc(addFilters = false)
class AdminRegionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegionQueryService regionQueryService;

    @Test
    @DisplayName("비활성 지역도 주고, 활성 여부는 FE 계약 키 isActive로 나간다(active 아님)")
    void activeFlagKey() throws Exception {
        when(regionQueryService.findAll()).thenReturn(List.of(
                new RegionResult("11440", "서울특별시", "마포구", true),
                new RegionResult("11200", "서울특별시", "성동구", false)));

        mockMvc.perform(get("/api/admin/v1/regions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("11440"))
                .andExpect(jsonPath("$.data[0].isActive").value(true))
                .andExpect(jsonPath("$.data[1].isActive").value(false))
                .andExpect(jsonPath("$.data[0].active").doesNotExist());
    }
}
