package com.shineunsigan.snsg.user.application.event;

import java.util.UUID;

/**
 * 보관 기간이 지난 탈퇴 회원을 비식별했다. 받는 쪽은 비식별 전 사진 파일을 지운다.
 * 파일 삭제는 되돌릴 수 없어 커밋된 뒤에 해야 하므로 이벤트로 넘긴다.
 * <p>
 * 비식별 전 경로가 우리가 저장한 사진일 때만 발행한다.
 */
public record UserAnonymizedEvent(UUID userId, String profileImageUrl) {
}
