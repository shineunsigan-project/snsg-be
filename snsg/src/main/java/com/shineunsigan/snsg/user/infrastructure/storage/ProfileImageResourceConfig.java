package com.shineunsigan.snsg.user.infrastructure.storage;

import java.nio.file.Path;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import lombok.RequiredArgsConstructor;

/**
 * 업로드한 프로필 사진을 {@code /images/profiles/**}로 내려준다.
 * 기본 아바타는 jar 안의 정적 리소스이고, 업로드 파일은 디스크에 있어 경로를 따로 연결해야 한다.
 */
@Configuration
// WebMvcConfigurer라 @WebMvcTest 슬라이스에도 올라온다. 슬라이스에는 @ConfigurationPropertiesScan이 적용되지 않으므로 직접 등록한다.
@EnableConfigurationProperties(FileStorageProperties.class)
@RequiredArgsConstructor
public class ProfileImageResourceConfig implements WebMvcConfigurer {

    private final FileStorageProperties properties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path directory = Path.of(properties.directory()).toAbsolutePath().normalize();
        registry.addResourceHandler(LocalFileStorageAdapter.URL_PREFIX + "**")
                .addResourceLocations(directory.toUri().toString());
    }
}
