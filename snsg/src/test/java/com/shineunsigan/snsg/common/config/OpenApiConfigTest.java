package com.shineunsigan.snsg.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 서버가 토큰 없이 내주는 문서에 어드민 정보가 나오지 않는지 본다. 일반 사용자는 어드민의 어떤 정보도 볼 수 없어야 한다.
 * <p>
 * 실제 앱 설정 그대로 띄운다(어드민 그룹을 켜지 않는다). dev 프로파일은 swagger-ui·스펙 경로를 여는 것만 다르고,
 * 테스트의 기본 프로파일(local)도 같은 경로를 연다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @ValueSource(strings = {"/v3/api-docs", "/v3/api-docs.yaml", "/v3/api-docs/user", "/v3/api-docs/swagger-config"})
    @DisplayName("그룹 없는 스펙(JSON·YAML), 사용자 그룹, swagger-ui 설정은 공개로 응답하고 어드민 경로·스키마·그룹이 없다")
    void publicDocsHaveNoAdmin(String path) throws Exception {
        var response = mockMvc.perform(get(path)).andReturn().getResponse();
        String body = response.getContentAsString(StandardCharsets.UTF_8);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(body).doesNotContain("/api/admin").doesNotContain("Admin").doesNotContain("\"admin\"");
    }

    @Test
    @DisplayName("어드민 그룹 스펙은 토큰 없이 응답하지 않는다(401·404)")
    void adminGroupIsNotPublic() throws Exception {
        assertThat(mockMvc.perform(get("/v3/api-docs/admin")).andReturn().getResponse().getStatus())
                .isIn(401, 404);
        assertThat(mockMvc.perform(get("/v3/api-docs.yaml/admin")).andReturn().getResponse().getStatus())
                .isIn(401, 404);
    }
}
