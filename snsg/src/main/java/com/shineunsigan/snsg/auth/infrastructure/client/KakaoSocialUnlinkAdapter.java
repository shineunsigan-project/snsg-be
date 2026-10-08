package com.shineunsigan.snsg.auth.infrastructure.client;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import com.shineunsigan.snsg.auth.application.port.out.SocialUnlinkPort;
import com.shineunsigan.snsg.auth.domain.constants.SocialProvider;

/**
 * 카카오에 걸린 우리 앱 연동을 끊는다.
 * <p>
 * 사용자 액세스 토큰은 로그인 때 쓰고 버리므로 보관하지 않는다. 어드민 키로 회원번호를 지정해 호출한다.
 */
@Component
public class KakaoSocialUnlinkAdapter implements SocialUnlinkPort {

    private static final String UNLINK_URL = "https://kapi.kakao.com/v1/user/unlink";

    private final RestClient restClient;
    private final String adminKey;

    public KakaoSocialUnlinkAdapter(RestClient.Builder restClientBuilder, OAuthProperties oAuthProperties) {
        this.restClient = restClientBuilder.build();
        this.adminKey = oAuthProperties.kakao().adminKey();
    }

    @Override
    public void unlink(SocialProvider provider, String providerUserId) {
        if (provider != SocialProvider.KAKAO) {
            return;
        }
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("target_id_type", "user_id");
        body.add("target_id", providerUserId);

        restClient.post()
                .uri(UNLINK_URL)
                .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + adminKey)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }
}
