package com.shineunsigan.snsg.user.application.dto.result;

import com.shineunsigan.snsg.user.domain.constants.NicknameUnavailableReason;

/**
 * 닉네임 사용 가능 여부. 쓸 수 있으면 {@code reason}이 {@code null}이다.
 */
public record NicknameAvailabilityResult(boolean available, NicknameUnavailableReason reason) {
}
