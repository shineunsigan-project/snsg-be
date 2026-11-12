package com.shineunsigan.snsg.admin.infrastructure.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 더미 해시 대조(아이디 없음)와 README 절차가 만드는 {@code $2y$} 해시를 받는지 본다.
 * 실제 해시 값을 저장소에 남기지 않도록 테스트 안에서 만든다.
 */
class BCryptPasswordHashAdapterTest {

    private static final String PASSWORD = "mint1234!";

    private final BCryptPasswordHashAdapter adapter = new BCryptPasswordHashAdapter();

    @Test
    @DisplayName("해시는 원문과 다르고, 같은 원문만 맞는다")
    void hashAndMatch() {
        String hash = adapter.hash(PASSWORD);

        assertThat(hash).isNotEqualTo(PASSWORD);
        assertThat(adapter.matches(PASSWORD, hash)).isTrue();
        assertThat(adapter.matches("wrong1234", hash)).isFalse();
    }

    @Test
    @DisplayName("해시가 없으면(아이디 없음) 더미 해시와 대조하고 거짓이다")
    void noHash() {
        assertThat(adapter.matches(PASSWORD, null)).isFalse();
    }

    @Test
    @DisplayName("htpasswd가 만드는 $2y$ 접두사 해시도 받는다")
    void acceptsTwoYPrefix() {
        String twoY = "$2y$" + adapter.hash(PASSWORD).substring(4);

        assertThat(adapter.matches(PASSWORD, twoY)).isTrue();
    }
}
