package com.shineunsigan.snsg.admin.infrastructure.crypto;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import com.shineunsigan.snsg.admin.application.port.out.PasswordHashPort;

/**
 * BCrypt로 해시를 만들고 대조한다. {@code $2a$}·{@code $2b$}·{@code $2y$} 접두사를 모두 받는다.
 * README의 MASTER 계정 절차가 {@code htpasswd}로 만든 {@code $2y$} 해시를 넣는다.
 * <p>
 * 더미 해시는 기동 때 한 번 만든다. 아이디가 없을 때도 같은 비용의 대조를 하기 위해서다.
 */
@Component
public class BCryptPasswordHashAdapter implements PasswordHashPort {

    private static final String DUMMY_PASSWORD = "dummy-password-for-timing-1";

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final String dummyHash = encoder.encode(DUMMY_PASSWORD);

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        if (passwordHash == null) {
            encoder.matches(rawPassword, dummyHash);
            return false;
        }
        return encoder.matches(rawPassword, passwordHash);
    }
}
