package com.shineunsigan.snsg.user.application.event;

import java.util.UUID;

/**
 * 프로필 사진을 다른 사진으로 바꿨다. 받는 쪽은 이전 사진 파일을 지운다.
 * 파일 삭제는 되돌릴 수 없어 커밋된 뒤에 해야 하므로 이벤트로 넘긴다.
 * <p>
 * 이전 경로가 우리가 저장한 사진이 아니면(기본·소셜 이미지) 받는 쪽이 지우지 않는다.
 */
public record ProfileImageReplacedEvent(UUID userId, String previousProfileImageUrl) {
}
