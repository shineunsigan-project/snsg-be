package com.shineunsigan.snsg.user.application.port.out;

/**
 * 업로드한 파일을 보관한다. dev·prod는 S3, local 프로파일은 로컬 디스크에 둔다({@code file-storage.type}으로 어댑터를 고른다).
 */
public interface FileStoragePort {

    /**
     * @param content   파일 내용
     * @param extension 확장자. 바이트를 보고 판별한 것이어야 한다. 클라이언트가 보낸 값을 그대로 쓰지 않는다
     * @return 클라이언트에게 돌려줄 경로. 도메인은 붙이지 않는다
     */
    String store(byte[] content, String extension);

    /**
     * 없는 파일이면 아무 일도 하지 않는다. 우리가 저장한 경로가 아니면 지우지 않는다.
     */
    void delete(String path);
}
