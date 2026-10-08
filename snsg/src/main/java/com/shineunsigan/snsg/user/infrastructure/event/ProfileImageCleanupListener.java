package com.shineunsigan.snsg.user.infrastructure.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.shineunsigan.snsg.user.application.event.UserWithdrawnEvent;
import com.shineunsigan.snsg.user.application.port.out.FileStoragePort;

import lombok.RequiredArgsConstructor;

/**
 * 탈퇴한 사용자가 올렸던 프로필 사진을 지운다.
 * <p>
 * 같은 도메인의 이벤트를 받는 드문 경우다. 파일 삭제는 되돌릴 수 없어 커밋된 뒤에 해야 하고,
 * 외부 저장소(지금은 디스크, 나중에 S3) 호출이라 트랜잭션 안에 두지 않는다.
 * 실패하면 파일이 남지만 DB에서 참조되지 않는다.
 */
@Component
@RequiredArgsConstructor
public class ProfileImageCleanupListener {

    private final FileStoragePort fileStoragePort;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(UserWithdrawnEvent event) {
        fileStoragePort.delete(event.profileImageUrl());
    }
}
