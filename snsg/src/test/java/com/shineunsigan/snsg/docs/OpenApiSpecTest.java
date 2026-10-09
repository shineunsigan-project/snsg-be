package com.shineunsigan.snsg.docs;

import static org.assertj.core.api.Assertions.assertThat;
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

/**
 * 커밋된 {@code docs/openapi.yaml}이 현재 코드와 같은지 확인한다.
 * <p>
 * FE는 이 저장소를 받아가 {@code docs/}만 보고 작업한다. 스펙이 코드보다 뒤처지면 FE는 없는 API를
 * 추측해 만들게 되고, 그 사실을 아무도 모른 채 시간이 지난다. 사람이 기억해서 갱신하는 대신 빌드가 막는다.
 * <p>
 * 스펙을 다시 만들려면 {@code OPENAPI_UPDATE=true ./gradlew test --tests '*OpenApiSpecTest'}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiSpecTest {

    private static final Path SPEC = Path.of("..", "docs", "openapi.yaml");

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("커밋된 스펙이 현재 코드와 같다")
    void specMatchesCode() throws Exception {
        String generated = normalize(mockMvc.perform(get("/v3/api-docs.yaml"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));

        if (Boolean.parseBoolean(System.getenv("OPENAPI_UPDATE"))) {
            Files.writeString(SPEC, generated, StandardCharsets.UTF_8);
            return;
        }

        assertThat(normalize(readSpec()))
                .describedAs("docs/openapi.yaml이 코드와 다르다. "
                        + "OPENAPI_UPDATE=true ./gradlew test --tests '*OpenApiSpecTest' 로 다시 만든다")
                .isEqualTo(generated);
    }

    private String readSpec() throws IOException {
        return Files.exists(SPEC) ? Files.readString(SPEC, StandardCharsets.UTF_8) : "";
    }

    /** autocrlf 때문에 체크아웃 환경마다 줄바꿈이 달라진다. 비교 전에 맞춘다. */
    private String normalize(String yaml) {
        return yaml.replace("\r\n", "\n").strip();
    }
}
