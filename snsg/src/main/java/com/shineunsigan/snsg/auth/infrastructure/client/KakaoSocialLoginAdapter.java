package com.shineunsigan.snsg.auth.infrastructure.client;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.shineunsigan.snsg.auth.application.dto.result.SocialUserResult;
import com.shineunsigan.snsg.auth.application.port.out.SocialLoginPort;
import com.shineunsigan.snsg.auth.domain.exception.AuthErrorCode;
import com.shineunsigan.snsg.common.exception.BusinessException;
import com.shineunsigan.snsg.user.domain.constants.SocialProvider;

/**
 * 카카오 인가코드를 토큰으로 바꾸고 사용자 정보를 조회한다.
 * 카카오 응답 형태를 아는 곳은 여기뿐이고, 밖으로는 {@link SocialUserResult}만 나간다.
 */
@Component
public class KakaoSocialLoginAdapter implements SocialLoginPort {

    private static final String AUTHORIZE_URL = "https://kauth.kakao.com/oauth/authorize";
    private static final String TOKEN_URL = "https://kauth.kakao.com/oauth/token";
    private static final String USER_URL = "https://kapi.kakao.com/v2/user/me";

    private final RestClient restClient;
    private final OAuthProperties.Provider kakao;

    public KakaoSocialLoginAdapter(RestClient.Builder restClientBuilder, OAuthProperties oAuthProperties) {
        this.restClient = restClientBuilder.build();
        this.kakao = oAuthProperties.kakao();
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.KAKAO;
    }

    @Override
    public String authorizeUrl() {
        return AUTHORIZE_URL
                + "?response_type=code"
                + "&client_id=" + encode(kakao.clientId())
                + "&redirect_uri=" + encode(kakao.redirectUri());
    }

    @Override
    public SocialUserResult fetchUser(String authorizationCode) {
        String accessToken = exchangeToken(authorizationCode);
        KakaoUserResponse user = fetchUserInfo(accessToken);
        return new SocialUserResult(SocialProvider.KAKAO, String.valueOf(user.id()), user.nickname(),
                user.profileImageUrl());
    }

    private String exchangeToken(String authorizationCode) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", kakao.clientId());
        form.add("client_secret", kakao.clientSecret());
        form.add("redirect_uri", kakao.redirectUri());
        form.add("code", authorizationCode);

        try {
            KakaoTokenResponse response = restClient.post()
                    .uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(KakaoTokenResponse.class);
            if (response == null || response.accessToken() == null) {
                throw new BusinessException(AuthErrorCode.INVALID_SOCIAL_CODE);
            }
            return response.accessToken();
        } catch (RestClientException exception) {
            throw new BusinessException(AuthErrorCode.INVALID_SOCIAL_CODE);
        }
    }

    private KakaoUserResponse fetchUserInfo(String accessToken) {
        try {
            KakaoUserResponse response = restClient.get()
                    .uri(USER_URL)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(KakaoUserResponse.class);
            if (response == null || response.id() == null) {
                throw new BusinessException(AuthErrorCode.SOCIAL_USER_FETCH_FAILED);
            }
            return response;
        } catch (RestClientException exception) {
            throw new BusinessException(AuthErrorCode.SOCIAL_USER_FETCH_FAILED);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
