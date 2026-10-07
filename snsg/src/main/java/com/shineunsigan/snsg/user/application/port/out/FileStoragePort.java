package com.shineunsigan.snsg.user.application.port.out;

/**
 * 업로드한 파일을 보관한다. 지금은 로컬 디스크에 두고, 서버가 여러 대가 되면 어댑터만 S3로 바꾼다.
 */
public interface FileStoragePort {

    /**
     * @param content   파일 내용
     * @param extension 확장자. 바이트를 보고 판별한 것이어야 한다. 클라이언트가 보낸 값을 그대로 쓰지 않는다
     * @return 클라이언트에게 돌려줄 경로. 도메인은 붙이지 않는다
     */
    String store(byte[] content, String extension);
}
