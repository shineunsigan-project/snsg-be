package com.shineunsigan.snsg.user.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 삭제가 업로드 경로 형식인 값만 받아들이는지 본다. 형식 밖의 값으로 디렉터리 안팎의 파일이 지워지면 안 된다.
 */
class LocalFileStorageAdapterTest {

    private static final String FILE_NAME = "0a1b2c3d-4e5f-6071-8293-a4b5c6d7e8f9.png";

    @TempDir
    private Path root;

    private Path directory;
    private Path stored;
    private Path outside;
    private Path sibling;
    private LocalFileStorageAdapter adapter;

    @BeforeEach
    void setUp() throws IOException {
        directory = Files.createDirectories(root.resolve("profiles"));
        stored = Files.writeString(directory.resolve(FILE_NAME), "image");
        sibling = Files.writeString(directory.resolve("a.png"), "other");
        outside = Files.writeString(root.resolve(FILE_NAME), "outside");
        adapter = new LocalFileStorageAdapter(new FileStorageProperties(directory.toString()));
    }

    @Test
    @DisplayName("업로드 경로 형식이면 그 파일을 지운다")
    void deletesUploadedPath() {
        adapter.delete("/images/profiles/" + FILE_NAME);

        assertThat(stored).doesNotExist();
        assertThat(sibling).exists();
        assertThat(outside).exists();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/images/profiles/..",
            "/images/profiles/",
            "/images/profiles/a.png",
            "/images/profiles/0a1b2c3d-4e5f-6071-8293-a4b5c6d7e8f9.gif",
            "/images/profiles/../" + FILE_NAME,
            "/images/other/" + FILE_NAME,
            "https://kakao.test/" + FILE_NAME})
    @DisplayName("업로드 경로 형식이 아니면 디렉터리 안팎의 어떤 파일도 지우지 않고 예외도 없다")
    void ignoresOtherPaths(String path) {
        assertThatCode(() -> adapter.delete(path)).doesNotThrowAnyException();

        assertThat(stored).exists();
        assertThat(sibling).exists();
        assertThat(outside).exists();
        assertThat(directory).exists();
    }
}
