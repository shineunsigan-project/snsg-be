package com.shineunsigan.snsg.auth.presentation;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.shineunsigan.snsg.auth.application.HandleSocialUnlinkUseCase;
import com.shineunsigan.snsg.auth.application.port.out.TokenPort;
import com.shineunsigan.snsg.auth.domain.exception.AuthErrorCode;
import com.shineunsigan.snsg.common.exception.BusinessException;

/**
 * 카카오는 3초 안에 200을 받지 못하면 같은 요청을 다시 보낸다.
 * 처리에 실패해도 200으로 답해 재시도가 쌓이지 않게 하는 것이 여기서 정한 규칙이다.
 */
@WebMvcTest(controllers = KakaoWebhookController.class)
@AutoConfigureMockMvc(addFilters = false)
class KakaoWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HandleSocialUnlinkUseCase handleSocialUnlinkUseCase;

    /** 인증 필터가 요구한다. 이 경로는 필터를 끄고 돌지만 빈은 있어야 컨텍스트가 뜬다. */
    @MockitoBean
    private TokenPort tokenPort;

    @Test
    @DisplayName("연결 끊기 통지를 받으면 처리하고 200을 준다")
    void handlesUnlink() throws Exception {
        mockMvc.perform(post("/webhooks/kakao/unlink")
                        .header(HttpHeaders.AUTHORIZATION, "KakaoAK admin-key")
                        .param("app_id", "123")
                        .param("user_id", "kakao-1")
                        .param("referrer_type", "UNLINK_FROM_APPS"))
                .andExpect(status().isOk());

        verify(handleSocialUnlinkUseCase).handleKakaoUnlink("KakaoAK admin-key", "kakao-1");
    }

    @Test
    @DisplayName("처리하지 못해도 200을 준다")
    void alwaysRespondsOk() throws Exception {
        doThrow(new BusinessException(AuthErrorCode.INVALID_WEBHOOK_SENDER))
                .when(handleSocialUnlinkUseCase).handleKakaoUnlink(anyString(), anyString());

        mockMvc.perform(post("/webhooks/kakao/unlink")
                        .header(HttpHeaders.AUTHORIZATION, "KakaoAK wrong-key")
                        .param("user_id", "kakao-1"))
                .andExpect(status().isOk());
    }
}
