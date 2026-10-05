package com.shineunsigan.snsg.auth.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.shineunsigan.snsg.auth.application.dto.result.SocialUserResult;
import com.shineunsigan.snsg.auth.domain.exception.AuthErrorCode;
import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.user.domain.constants.SocialProvider;

/**
 * 카카오 응답을 우리 모델로 옮기는 규칙을 검증한다. 실제 카카오를 부르지 않는다.
 */
class KakaoSocialLoginAdapterTest {

    private static final String TOKEN_URL = "https://kauth.kakao.com/oauth/token";
    private static final String USER_URL = "https://kapi.kakao.com/v2/user/me";

    private MockRestServiceServer server;
    private KakaoSocialLoginAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new KakaoSocialLoginAdapter(builder, new OAuthProperties(
                new OAuthProperties.Provider("client-id", "client-secret", "http://localhost:8080/callback"), null));
    }

    @Test
    @DisplayName("인가코드를 토큰으로 바꾸고 사용자 정보를 우리 모델로 옮긴다")
    void fetchUser() {
        server.expect(requestTo(TOKEN_URL))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("code=auth-code")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("client_secret=client-secret")))
                .andRespond(withSuccess("{\"access_token\":\"kakao-access-token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(USER_URL))
                .andExpect(header("Authorization", "Bearer kakao-access-token"))
                .andRespond(withSuccess("""
                        {
                          "id": 1234567890,
                          "kakao_account": {
                            "profile": {
                              "nickname": "민트초코",
                              "profile_image_url": "https://kakao.test/me.png",
                              "is_default_image": false
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        SocialUserResult result = adapter.fetchUser("auth-code");

        assertThat(result.provider()).isEqualTo(SocialProvider.KAKAO);
        assertThat(result.providerUserId()).isEqualTo("1234567890");
        assertThat(result.nickname()).isEqualTo("민트초코");
        assertThat(result.profileImageUrl()).isEqualTo("https://kakao.test/me.png");
        server.verify();
    }

    @Test
    @DisplayName("카카오 기본 이미지면 사진이 없는 것으로 본다")
    void defaultProfileImageTreatedAsEmpty() {
        server.expect(requestTo(TOKEN_URL))
                .andRespond(withSuccess("{\"access_token\":\"kakao-access-token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(USER_URL))
                .andRespond(withSuccess("""
                        {
                          "id": 1,
                          "kakao_account": {
                            "profile": {
                              "nickname": "민트초코",
                              "profile_image_url": "https://kakao.test/default.png",
                              "is_default_image": true
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        SocialUserResult result = adapter.fetchUser("auth-code");

        assertThat(result.profileImageUrl()).isNull();
    }

    @Test
    @DisplayName("동의하지 않아 닉네임·사진이 없어도 식별자만 있으면 된다")
    void withoutProfileAgreement() {
        server.expect(requestTo(TOKEN_URL))
                .andRespond(withSuccess("{\"access_token\":\"kakao-access-token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(USER_URL))
                .andRespond(withSuccess("{\"id\": 1}", MediaType.APPLICATION_JSON));

        SocialUserResult result = adapter.fetchUser("auth-code");

        assertThat(result.providerUserId()).isEqualTo("1");
        assertThat(result.nickname()).isNull();
        assertThat(result.profileImageUrl()).isNull();
    }

    @Test
    @DisplayName("인가코드가 틀리면 INVALID_SOCIAL_CODE")
    void invalidAuthorizationCode() {
        server.expect(requestTo(TOKEN_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> adapter.fetchUser("wrong-code"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(AuthErrorCode.INVALID_SOCIAL_CODE);
    }

    @Test
    @DisplayName("인증 시작 주소에 client_id와 redirect_uri가 들어간다")
    void authorizeUrl() {
        assertThat(adapter.authorizeUrl())
                .startsWith("https://kauth.kakao.com/oauth/authorize?response_type=code")
                .contains("client_id=client-id")
                .contains("redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fcallback");
    }
}
