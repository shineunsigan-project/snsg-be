package com.shineunsigan.snsg.user.application.port.in;

/**
 * 타 도메인이 프로필 사진을 올리는 입구. 가입 중(auth)과 가입 후(user) 모두 같은 경로를 쓴다.
 */
public interface UploadProfileImageUseCase {

    /**
     * @return 저장된 사진의 경로
     */
    String upload(byte[] content);
}
