package com.shineunsigan.snsg.docs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.shineunsigan.snsg.common.config.OpenApiConfig;

/**
 * 커밋된 {@code docs/openapi.yaml}(사용자)과 {@code docs/openapi-admin.yaml}(어드민)이 현재 코드와 같은지 확인한다.
 * <p>
 * FE는 이 저장소를 받아가 {@code docs/}만 보고 작업한다. 스펙이 코드보다 뒤처지면 FE는 없는 API를
 * 추측해 만들게 되고, 그 사실을 아무도 모른 채 시간이 지난다. 사람이 기억해서 갱신하는 대신 빌드가 막는다.
 * <p>
 * 스펙을 다시 만들려면 {@code OPENAPI_UPDATE=true ./gradlew test --tests '*OpenApiSpecTest'}. 두 파일을 모두 만든다.
 * <p>
 * 그룹별 YAML 경로({@code /v3/api-docs.yaml/{group}})는 공개 경로가 아니라서 인증된 요청으로 받는다.
 * 이 테스트의 대상은 스펙 내용이지 인가 규칙이 아니다.
 * <p>
 * 어드민 그룹은 서버가 내주지 않는다. 이 테스트만 {@code openapi.admin-group-enabled=true}로 켜서 파일을 만든다.
 * 서버 설정의 {@code springdoc.paths-to-exclude}(그룹 없는 스펙에서 어드민 경로를 거른다)는 그룹에도 적용되므로
 * 여기서는 비운다. 사용자 그룹은 자기 설정으로 어드민 경로를 뺀다. 서버가 내주는 문서는 {@code OpenApiConfigTest}가 본다.
 */
@SpringBootTest(properties = {"openapi.admin-group-enabled=true", "springdoc.paths-to-exclude="})
@AutoConfigureMockMvc
class OpenApiSpecTest {

    private static final Path SPEC = Path.of("..", "docs", "openapi.yaml");
    private static final Path ADMIN_SPEC = Path.of("..", "docs", "openapi-admin.yaml");

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("커밋된 사용자 스펙이 현재 코드와 같다")
    void specMatchesCode() throws Exception {
        assertSpecMatches(OpenApiConfig.USER_GROUP, SPEC);
    }

    @Test
    @DisplayName("커밋된 어드민 스펙이 현재 코드와 같다")
    void adminSpecMatchesCode() throws Exception {
        assertSpecMatches(OpenApiConfig.ADMIN_GROUP, ADMIN_SPEC);
    }

    private void assertSpecMatches(String group, Path spec) throws Exception {
        String generated = normalize(mockMvc.perform(get("/v3/api-docs.yaml/{group}", group)
                        .with(user("openapi-spec-test")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));

        if (Boolean.parseBoolean(System.getenv("OPENAPI_UPDATE"))) {
            Files.writeString(spec, generated, StandardCharsets.UTF_8);
            return;
        }

        assertThat(normalize(readSpec(spec)))
                .describedAs("docs/" + spec.getFileName() + "이 코드와 다르다. "
                        + "OPENAPI_UPDATE=true ./gradlew test --tests '*OpenApiSpecTest' 로 다시 만든다")
                .isEqualTo(generated);
    }

    private String readSpec(Path spec) throws IOException {
        return Files.exists(spec) ? Files.readString(spec, StandardCharsets.UTF_8) : "";
    }

    /** autocrlf 때문에 체크아웃 환경마다 줄바꿈이 달라진다. 비교 전에 맞춘다. */
    private String normalize(String yaml) {
        return yaml.replace("\r\n", "\n").strip();
    }
}
