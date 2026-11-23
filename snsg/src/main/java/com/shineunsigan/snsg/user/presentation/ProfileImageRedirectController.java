package com.shineunsigan.snsg.user.presentation;

import java.net.URI;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.shineunsigan.snsg.user.application.UserQueryService;

import io.swagger.v3.oas.annotations.Hidden;

import lombok.RequiredArgsConstructor;

/**
 * 업로드한 프로필 사진 경로를 CloudFront 주소로 리다이렉트한다(S3 모드, {@code file-storage.type=s3}).
 * <p>
 * DB와 API 응답의 사진 값은 디스크 저장 때와 같은 {@code /images/profiles/<uuid>.<ext>}다. FE가 서버 주소를 붙여 요청하면
 * 여기서 302로 CDN 주소를 준다. JSON API가 아니라 {@code /api} 밖에 두고 문서에 내지 않는다.
 * local 모드는 이 컨트롤러 대신 {@code ProfileImageResourceConfig}가 디스크 파일을 같은 경로로 내려준다.
 * <p>
 * 302로 둔다. 301은 브라우저가 영구 저장해 CDN 주소를 바꿀 때 되돌릴 수 없다. 대신 하루 동안 리다이렉트를 재사용하게 한다.
 */
@Hidden
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "file-storage.type", havingValue = "s3")
public class ProfileImageRedirectController {

    private static final String CACHE_CONTROL = "public, max-age=86400";

    private final UserQueryService userQueryService;

    @GetMapping("/images/profiles/{fileName}")
    public ResponseEntity<Void> redirect(@PathVariable String fileName) {
        return userQueryService.resolveProfileImageCdnUrl(fileName)
                .map(ProfileImageRedirectController::found)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static ResponseEntity<Void> found(String url) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(url))
                .header(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL)
                .build();
    }
}
