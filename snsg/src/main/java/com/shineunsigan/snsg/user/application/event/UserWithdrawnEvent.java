package com.shineunsigan.snsg.user.application.event;

import java.util.UUID;

import com.shineunsigan.snsg.user.domain.constants.SocialProvider;
import com.shineunsigan.snsg.user.domain.constants.WithdrawReason;

/**
 * 사용자가 탈퇴했다. 받는 쪽은 자기 도메인에 남은 흔적을 지운다.
 * <p>
 * auth는 refresh token을 지우고, 사용자가 요청한 탈퇴면 소셜 연동도 끊는다.
 * 사진 파일은 원본 보관 기간 동안 남기므로 이 이벤트로 지우지 않는다. 비식별할 때 {@link UserAnonymizedEvent}로 지운다.
 */
public record UserWithdrawnEvent(UUID userId, SocialProvider provider, String providerUserId,
        WithdrawReason reason) {
}
