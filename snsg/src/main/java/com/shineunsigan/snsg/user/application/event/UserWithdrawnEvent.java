package com.shineunsigan.snsg.user.application.event;

import java.util.UUID;

import com.shineunsigan.snsg.user.domain.constants.SocialProvider;
import com.shineunsigan.snsg.user.domain.constants.WithdrawReason;

/**
 * 사용자가 탈퇴했다. 받는 쪽은 자기 도메인에 남은 흔적을 지운다.
 * <p>
 * auth는 refresh token을 지우고, 사용자가 요청한 탈퇴면 소셜 연동도 끊는다.
 * 사진 파일 정리도 이 이벤트를 받아 처리한다. 트랜잭션 밖에서 지워야 하기 때문이다.
 */
public record UserWithdrawnEvent(UUID userId, SocialProvider provider, String providerUserId,
        String profileImageUrl, WithdrawReason reason) {
}
