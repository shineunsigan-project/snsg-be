package com.shineunsigan.snsg.admin.application.port.out;

/**
 * 비밀번호 해시와 대조. 구현은 infrastructure의 BCrypt 어댑터다.
 */
public interface PasswordHashPort {

    String hash(String rawPassword);

    /**
     * 원문이 해시와 맞는지 본다. {@code passwordHash}가 {@code null}이면(아이디가 없음) 미리 만들어 둔
     * 더미 해시와 대조해 같은 시간을 쓰고 거짓을 돌려준다. 응답 시간으로 아이디가 있는지 드러나지 않게 한다.
     */
    boolean matches(String rawPassword, String passwordHash);
}
