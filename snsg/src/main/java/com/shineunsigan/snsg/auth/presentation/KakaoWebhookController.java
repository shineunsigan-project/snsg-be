package com.shineunsigan.snsg.auth.presentation;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shineunsigan.snsg.auth.application.HandleSocialUnlinkUseCase;

import io.swagger.v3.oas.annotations.Hidden;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 카카오가 호출하는 주소다. 클라이언트용 API가 아니라 {@code /api} 밖에 둔다.
 * <p>
 * 사용자가 카카오 설정에서 우리 앱 연결을 끊으면 카카오가 이 주소로 알려준다.
 * 카카오는 3초 안에 200을 받지 못하면 같은 요청을 다시 보내므로, 실패해도 200으로 답하고 로그만 남긴다.
 */
@Hidden
@Slf4j
@RestController
@RequiredArgsConstructor
public class KakaoWebhookController {

    private final HandleSocialUnlinkUseCase handleSocialUnlinkUseCase;

    @PostMapping("/webhooks/kakao/unlink")
    public ResponseEntity<Void> unlink(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam("user_id") String userId,
            @RequestParam(value = "referrer_type", required = false) String referrerType) {
        try {
            handleSocialUnlinkUseCase.handleKakaoUnlink(authorization, userId);
            log.info("카카오 연결 끊기를 처리했다. userId={}, referrerType={}", userId, referrerType);
        } catch (RuntimeException exception) {
            // 가입하지 않은 사용자(INCOMPLETE_SIGN_UP)에 대해서도 통지가 온다. 재시도를 부르지 않도록 200으로 답한다.
            log.warn("카카오 연결 끊기를 처리하지 못했다. userId={}, referrerType={}", userId, referrerType, exception);
        }
        return ResponseEntity.ok().build();
    }
}
