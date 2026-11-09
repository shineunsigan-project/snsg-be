package com.shineunsigan.snsg.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shineunsigan.snsg.auth.application.dto.result.TokenResult;
import com.shineunsigan.snsg.auth.application.port.out.RefreshTokenStorePort;
import com.shineunsigan.snsg.auth.application.port.out.TokenPort;

/**
 * 로그인·가입은 매번 새 기기 세션을 만든다. 토큰에 담은 세션과 저장한 세션이 같아야 재발급이 된다.
 */
@ExtendWith(MockitoExtension.class)
class TokenIssuerTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private TokenPort tokenPort;

    @Mock
    private RefreshTokenStorePort refreshTokenStorePort;

    private TokenIssuer tokenIssuer;

    @BeforeEach
    void setUp() {
        tokenIssuer = new TokenIssuer(tokenPort, refreshTokenStorePort);
    }

    @Test
    @DisplayName("같은 사용자가 두 번 로그인하면 서로 다른 세션으로 발급하고 각 세션에 저장한다")
    void issuesNewSessionEveryTime() {
        when(tokenPort.issueRefreshToken(eq(USER_ID), anyString()))
                .thenAnswer(invocation -> "refresh-" + invocation.getArgument(1));

        TokenResult first = tokenIssuer.issue(USER_ID);
        TokenResult second = tokenIssuer.issue(USER_ID);

        ArgumentCaptor<String> issuedSessionIds = ArgumentCaptor.forClass(String.class);
        verify(tokenPort, times(2)).issueRefreshToken(eq(USER_ID), issuedSessionIds.capture());
        List<String> sessionIds = issuedSessionIds.getAllValues();
        assertThat(sessionIds.get(0)).isNotBlank().isNotEqualTo(sessionIds.get(1));

        verify(refreshTokenStorePort).save(USER_ID, sessionIds.get(0), first.refreshToken());
        verify(refreshTokenStorePort).save(USER_ID, sessionIds.get(1), second.refreshToken());
    }

    @Test
    @DisplayName("저장하는 사용자·세션·토큰은 refresh token을 발급한 값과 같다")
    void savesWhatWasIssued() {
        when(tokenPort.issueAccessToken(USER_ID)).thenReturn("access");
        when(tokenPort.issueRefreshToken(eq(USER_ID), anyString())).thenReturn("refresh");

        TokenResult result = tokenIssuer.issue(USER_ID);

        ArgumentCaptor<String> issuedSessionId = ArgumentCaptor.forClass(String.class);
        verify(tokenPort).issueRefreshToken(eq(USER_ID), issuedSessionId.capture());
        ArgumentCaptor<String> savedSessionId = ArgumentCaptor.forClass(String.class);
        verify(refreshTokenStorePort).save(eq(USER_ID), savedSessionId.capture(), eq("refresh"));

        assertThat(savedSessionId.getValue()).isEqualTo(issuedSessionId.getValue());
        assertThat(result.accessToken()).isEqualTo("access");
        assertThat(result.refreshToken()).isEqualTo("refresh");
    }
}
