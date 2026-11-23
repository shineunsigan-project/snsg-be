package com.shineunsigan.snsg.user.presentation;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.shineunsigan.snsg.user.application.UserQueryService;

/**
 * S3 모드에서 업로드 사진 경로가 CloudFront 주소로 302 리다이렉트되는지, 형식 밖이면 404인지 본다.
 * 형식 판정과 주소 조립은 {@code UserQueryServiceTest}가 본다. 여기서는 그 결과를 응답으로 옮기는 것만 본다.
 * <p>
 * auth를 import하지 않는다. 인증 필터는 auth 패키지를 스캔에서 빼서 올리지 않는다. 공개 여부는 SecurityConfig의
 * {@code /images/**} permitAll이 정한다.
 */
@WebMvcTest(controllers = ProfileImageRedirectController.class,
        properties = "file-storage.type=s3",
        excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.shineunsigan\\.snsg\\.auth\\..*"))
@AutoConfigureMockMvc(addFilters = false)
class ProfileImageRedirectControllerTest {

    private static final String FILE_NAME = "0a1b2c3d-4e5f-6071-8293-a4b5c6d7e8f9.png";
    private static final String CDN_URL = "https://cdn.example.test/images/profiles/" + FILE_NAME;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserQueryService userQueryService;

    @Test
    @DisplayName("업로드 경로 형식이면 302이고 Location은 CDN 주소, 하루 동안 재사용한다")
    void redirectsToCdn() throws Exception {
        when(userQueryService.resolveProfileImageCdnUrl(FILE_NAME)).thenReturn(Optional.of(CDN_URL));

        mockMvc.perform(get("/images/profiles/" + FILE_NAME))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, CDN_URL))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "public, max-age=86400"));
    }

    @Test
    @DisplayName("형식 밖 파일명이면 404이고 Location이 없다")
    void notFoundForOtherFileNames() throws Exception {
        when(userQueryService.resolveProfileImageCdnUrl("a.png")).thenReturn(Optional.empty());

        mockMvc.perform(get("/images/profiles/a.png"))
                .andExpect(status().isNotFound())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION));
    }
}
